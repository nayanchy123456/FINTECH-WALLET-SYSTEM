package com.paymentprocessing.wallet.transaction.service.impl;

import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.common.service.RedisService;
import com.paymentprocessing.wallet.ledger.entity.AccountType;
import com.paymentprocessing.wallet.ledger.service.LedgerService;
import com.paymentprocessing.wallet.notification.kafka.NotificationProducer;
import com.paymentprocessing.wallet.notification.kafka.TransactionEvent;
import com.paymentprocessing.wallet.transaction.dto.TransactionResponse;
import com.paymentprocessing.wallet.transaction.dto.TransferRequest;
import com.paymentprocessing.wallet.transaction.entity.Transaction;
import com.paymentprocessing.wallet.transaction.entity.TransactionStatus;
import com.paymentprocessing.wallet.transaction.entity.TransactionType;
import com.paymentprocessing.wallet.transaction.repository.TransactionRepository;
import com.paymentprocessing.wallet.transaction.service.TransactionService;
import com.paymentprocessing.wallet.wallet.entity.Wallet;
import com.paymentprocessing.wallet.wallet.entity.WalletStatus;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final RedisService redisService;
    // StringRedisTemplate uses plain string serialization — required for INCR
    // to work correctly. GenericJackson2JsonRedisSerializer wraps values in JSON,
    // making them non-integer and causing Redis to reject INCR with an error.
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationProducer notificationProducer;

    @Value("${app.rate-limit.max-per-minute:5}")
    private int maxOperationsPerMinute;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                   WalletRepository walletRepository,
                                   LedgerService ledgerService,
                                   RedisService redisService,
                                   StringRedisTemplate stringRedisTemplate,
                                   NotificationProducer notificationProducer) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.redisService = redisService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationProducer = notificationProducer;
    }

    private static final String IDEMPOTENCY_KEY = "idempotency:";

    // -----------------------------------------------------------------------
    // Rate-limit configuration
    // Each operation gets its own Redis key namespace so limits are tracked
    // independently.  All three share the same sliding-window algorithm.
    // -----------------------------------------------------------------------
    private static final String RATE_LIMIT_KEY_TRANSFER  = "rate:limit:transfer:";
    private static final String RATE_LIMIT_KEY_DEPOSIT   = "rate:limit:deposit:";
    private static final String RATE_LIMIT_KEY_WITHDRAW  = "rate:limit:withdraw:";

    /**
     * Lua script for an atomic sliding-window rate-limit check-and-increment.
     *
     * Keys:  KEYS[1] = current-window key,  KEYS[2] = previous-window key
     * Args:  ARGV[1] = window TTL in seconds (120),
     *        ARGV[2] = elapsed fraction of the current minute * 1000 (integer)
     *        ARGV[3] = max allowed count * 1000 (integer)
     *
     * Returns 1 if the request is allowed (counter already incremented),
     *         0 if the rate limit is exceeded (counter NOT incremented).
     *
     * Uses stringRedisTemplate so values are stored as plain integers, which
     * is a hard requirement for Redis INCR / GET to interoperate correctly.
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;
    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
        RATE_LIMIT_SCRIPT.setScriptText(
            "local cur = tonumber(redis.call('GET', KEYS[1]) or 0) " +
            "local prev = tonumber(redis.call('GET', KEYS[2]) or 0) " +
            "local elapsed = tonumber(ARGV[2]) " +        // * 1000, integer math
            "local maxCount = tonumber(ARGV[3]) " +       // * 1000
            // effective = (cur+1) + prev * (1 - elapsed/1000)
            // multiply everything by 1000 to stay in integer arithmetic
            "local effective = (cur + 1) * 1000 + prev * (1000 - elapsed) " +
            "if effective > maxCount then return 0 end " +
            "local newVal = redis.call('INCR', KEYS[1]) " +
            "if newVal == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return 1"
        );
    }

    // -----------------------------------------------------------------------
    // Shared rate-limit helper
    // -----------------------------------------------------------------------

    /**
     * Applies a sliding-window rate-limit check for the given user and
     * operation.  Throws {@link BadRequestException} when the limit is
     * exceeded; does nothing (returns) when the request is allowed.
     *
     * @param userId       the authenticated user's ID
     * @param keyPrefix    Redis key namespace, e.g. {@code "rate:limit:deposit:"}
     * @param maxPerMinute maximum calls allowed per 60-second window
     * @param opName       human-readable operation name used in the error message
     */
    private void checkRateLimit(Long userId, String keyPrefix, int maxPerMinute, String opName) {
        long currentWindow  = System.currentTimeMillis() / 1000 / 60;
        long previousWindow = currentWindow - 1;

        String currentKey  = keyPrefix + userId + ":" + currentWindow;
        String previousKey = keyPrefix + userId + ":" + previousWindow;

        // elapsed: how far through the current minute we are, scaled ×1000
        long elapsedScaled  = (System.currentTimeMillis() % 60_000) * 1000 / 60_000;
        // maxCount scaled ×1000 to match the Lua integer arithmetic
        long maxCountScaled = (long) maxPerMinute * 1000;

        List<String> keys = Arrays.asList(currentKey, previousKey);
        Long allowed = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT, keys,
                "120",                              // ARGV[1]: TTL seconds
                String.valueOf(elapsedScaled),      // ARGV[2]: elapsed ×1000
                String.valueOf(maxCountScaled));    // ARGV[3]: max ×1000

        if (allowed == null || allowed == 0L) {
            throw new BadRequestException(
                    opName + " limit exceeded. Max " + maxPerMinute +
                    " " + opName.toLowerCase() + "s per minute allowed");
        }
    }

    // -----------------------------------------------------------------------
    // Public operations
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public TransactionResponse transfer(Long senderUserId, TransferRequest request) {

        // Sliding Window Counter Rate Limiting — atomic via Lua script.
        // Executed via stringRedisTemplate so counter values are stored as
        // plain integers (required for INCR).
        checkRateLimit(senderUserId, RATE_LIMIT_KEY_TRANSFER, maxOperationsPerMinute, "Transfer");

        // Idempotency check
        if (request.getIdempotencyKey() != null) {
            String idempotencyKey = IDEMPOTENCY_KEY + request.getIdempotencyKey();
            if (redisService.exists(idempotencyKey)) {
                String existingRefId = redisService.get(idempotencyKey)
                        .map(Object::toString)
                        .orElse(null);
                log.info("Duplicate request detected, returning existing transaction");
                return getTransactionByReferenceId(existingRefId, senderUserId);
            }
        }

        // -----------------------------------------------------------------
        // Resolve the sender's wallet ID from their user ID first, then
        // acquire pessimistic write locks on both wallets in ascending
        // wallet-ID order to prevent deadlocks.
        // -----------------------------------------------------------------

        // Step 1: look up the sender wallet ID (no lock yet, just the ID)
        Wallet senderWalletRef = walletRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found"));
        Long senderWalletId = senderWalletRef.getId();
        Long receiverWalletId = request.getReceiverWalletId();

        // Step 2: acquire locks in a deterministic order (lower wallet ID first)
        Long firstId  = Math.min(senderWalletId, receiverWalletId);
        Long secondId = Math.max(senderWalletId, receiverWalletId);

        Wallet firstWallet = walletRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        firstId.equals(receiverWalletId) ? "Receiver wallet not found" : "Sender wallet not found"));
        Wallet secondWallet = walletRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        secondId.equals(receiverWalletId) ? "Receiver wallet not found" : "Sender wallet not found"));

        // Step 3: map back to semantic roles using wallet IDs (not user IDs)
        Wallet senderWallet   = firstWallet.getId().equals(senderWalletId) ? firstWallet  : secondWallet;
        Wallet receiverWallet = firstWallet.getId().equals(senderWalletId) ? secondWallet : firstWallet;

        // Validations
        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new BadRequestException("Cannot transfer to same wallet");
        }
        if (senderWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Sender wallet is not active");
        }
        if (receiverWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Receiver wallet is not active");
        }
        if (senderWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        String referenceId = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .senderWallet(senderWallet)
                .receiverWallet(receiverWallet)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .referenceId(referenceId)
                .description(request.getDescription())
                .build();

        transactionRepository.save(transaction);

        try {
            // Update wallet balances (version field is bumped automatically by JPA)
            senderWallet.setBalance(
                    senderWallet.getBalance().subtract(request.getAmount()));
            receiverWallet.setBalance(
                    receiverWallet.getBalance().add(request.getAmount()));
            walletRepository.save(senderWallet);
            walletRepository.save(receiverWallet);

            // Get or create ledger accounts
            String senderAccountCode   = "USR-" + senderWallet.getId();
            String receiverAccountCode = "USR-" + receiverWallet.getId();

            ledgerService.getOrCreateAccount(
                    senderAccountCode,
                    "User Wallet Account - " + senderWallet.getId(),
                    AccountType.ASSET);

            ledgerService.getOrCreateAccount(
                    receiverAccountCode,
                    "User Wallet Account - " + receiverWallet.getId(),
                    AccountType.ASSET);

            // Record double entry
            ledgerService.recordDoubleEntry(
                    referenceId,
                    "Transfer from wallet " + senderWallet.getId() +
                    " to wallet " + receiverWallet.getId(),
                    senderAccountCode,
                    receiverAccountCode,
                    request.getAmount());

            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            // Publish Kafka event
            notificationProducer.sendTransactionEvent(
                    TransactionEvent.builder()
                            .referenceId(referenceId)
                            .senderUserId(senderWallet.getUser().getId())
                            .receiverUserId(receiverWallet.getUser().getId())
                            .amount(request.getAmount())
                            .type("TRANSFER")
                            .status("SUCCESS")
                            .build()
            );

            // Store idempotency key
            if (request.getIdempotencyKey() != null) {
                redisService.set(
                        IDEMPOTENCY_KEY + request.getIdempotencyKey(),
                        referenceId,
                        Duration.ofHours(24));
            }

            log.info("Transfer successful: {}", referenceId);

        } catch (BadRequestException | ResourceNotFoundException e) {
            log.error("Transfer failed: {}", e.getMessage());
            throw e;
        }

        return mapToResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long userId, BigDecimal amount) {

        // Sliding Window Counter Rate Limiting — same guard as transfer/withdraw.
        checkRateLimit(userId, RATE_LIMIT_KEY_DEPOSIT, maxOperationsPerMinute, "Deposit");

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Wallet is not active");
        }

        String referenceId = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .receiverWallet(wallet)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .referenceId(referenceId)
                .description("Deposit to wallet")
                .build();

        transactionRepository.save(transaction);

        try {
            wallet.setBalance(wallet.getBalance().add(amount));
            walletRepository.save(wallet);

            String userAccountCode = "USR-" + wallet.getId();
            ledgerService.getOrCreateAccount(
                    userAccountCode,
                    "User Wallet Account - " + wallet.getId(),
                    AccountType.ASSET);

            ledgerService.recordDoubleEntry(
                    referenceId,
                    "Deposit to wallet " + wallet.getId(),
                    "SYS-LIABILITY",
                    userAccountCode,
                    amount);

            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            notificationProducer.sendTransactionEvent(
                    TransactionEvent.builder()
                            .referenceId(referenceId)
                            .receiverUserId(wallet.getUser().getId())
                            .amount(amount)
                            .type("DEPOSIT")
                            .status("SUCCESS")
                            .build()
            );

            log.info("Deposit successful: {}", referenceId);

        } catch (BadRequestException | ResourceNotFoundException e) {
            log.error("Deposit failed: {}", e.getMessage());
            throw e;
        }

        return mapToResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(Long userId, BigDecimal amount) {

        // Sliding Window Counter Rate Limiting — same guard as transfer/deposit.
        checkRateLimit(userId, RATE_LIMIT_KEY_WITHDRAW, maxOperationsPerMinute, "Withdrawal");

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Wallet is not active");
        }

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        String referenceId = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .senderWallet(wallet)
                .amount(amount)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)
                .referenceId(referenceId)
                .description("Withdrawal from wallet")
                .build();

        transactionRepository.save(transaction);

        try {
            wallet.setBalance(wallet.getBalance().subtract(amount));
            walletRepository.save(wallet);

            String userAccountCode = "USR-" + wallet.getId();
            ledgerService.getOrCreateAccount(
                    userAccountCode,
                    "User Wallet Account - " + wallet.getId(),
                    AccountType.ASSET);

            ledgerService.recordDoubleEntry(
                    referenceId,
                    "Withdrawal from wallet " + wallet.getId(),
                    userAccountCode,
                    "SYS-LIABILITY",
                    amount);

            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            notificationProducer.sendTransactionEvent(
                    TransactionEvent.builder()
                            .referenceId(referenceId)
                            .senderUserId(wallet.getUser().getId())
                            .amount(amount)
                            .type("WITHDRAWAL")
                            .status("SUCCESS")
                            .build()
            );

            log.info("Withdrawal successful: {}", referenceId);

        } catch (BadRequestException | ResourceNotFoundException e) {
            log.error("Withdrawal failed: {}", e.getMessage());
            throw e;
        }

        return mapToResponse(transaction);
    }

    @Override
    public TransactionResponse getTransactionByReferenceId(String referenceId, Long userId) {
        Transaction transaction = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        Long senderUserId   = transaction.getSenderWallet()   != null ? transaction.getSenderWallet().getUser().getId()   : null;
        Long receiverUserId = transaction.getReceiverWallet() != null ? transaction.getReceiverWallet().getUser().getId() : null;

        if (!userId.equals(senderUserId) && !userId.equals(receiverUserId)) {
            throw new ResourceNotFoundException("Transaction not found");
        }

        return mapToResponse(transaction);
    }

    @Override
    public Page<TransactionResponse> getTransactionHistory(Long userId, Pageable pageable) {
        // Resolve walletId from userId here in the service layer, keeping
        // the controller free of any repository dependency.
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        return transactionRepository.findByWalletId(wallet.getId(), pageable)
                .map(this::mapToResponse);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .senderWalletId(transaction.getSenderWallet() != null ?
                        transaction.getSenderWallet().getId() : null)
                .receiverWalletId(transaction.getReceiverWallet() != null ?
                        transaction.getReceiverWallet().getId() : null)
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .referenceId(transaction.getReferenceId())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
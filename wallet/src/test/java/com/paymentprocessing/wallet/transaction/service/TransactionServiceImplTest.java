package com.paymentprocessing.wallet.transaction.service;

import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.common.service.RedisService;
import com.paymentprocessing.wallet.ledger.service.LedgerService;
import com.paymentprocessing.wallet.notification.kafka.NotificationProducer;
import com.paymentprocessing.wallet.transaction.dto.TransactionResponse;
import com.paymentprocessing.wallet.transaction.dto.TransferRequest;
import com.paymentprocessing.wallet.transaction.entity.Transaction;
import com.paymentprocessing.wallet.transaction.entity.TransactionStatus;
import com.paymentprocessing.wallet.transaction.entity.TransactionType;
import com.paymentprocessing.wallet.transaction.repository.TransactionRepository;
import com.paymentprocessing.wallet.transaction.service.impl.TransactionServiceImpl;
import com.paymentprocessing.wallet.user.entity.Role;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.wallet.entity.Wallet;
import com.paymentprocessing.wallet.wallet.entity.WalletStatus;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private RedisService redisService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private User senderUser;
    private User receiverUser;
    private Wallet senderWallet;
    private Wallet receiverWallet;
    private TransferRequest transferRequest;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        senderUser = User.builder()
                .email("sender@test.com")
                .password("encoded")
                .fullName("Sender User")
                .role(Role.USER)
                .build();
        senderUser.setId(1L);

        receiverUser = User.builder()
                .email("receiver@test.com")
                .password("encoded")
                .fullName("Receiver User")
                .role(Role.USER)
                .build();
        receiverUser.setId(2L);

        senderWallet = Wallet.builder()
                .user(senderUser)
                .balance(BigDecimal.valueOf(1000))
                .status(WalletStatus.ACTIVE)
                .build();
        senderWallet.setId(1L);

        receiverWallet = Wallet.builder()
                .user(receiverUser)
                .balance(BigDecimal.valueOf(500))
                .status(WalletStatus.ACTIVE)
                .build();
        receiverWallet.setId(2L);

        transferRequest = new TransferRequest();
        transferRequest.setReceiverWalletId(2L);
        transferRequest.setAmount(BigDecimal.valueOf(200));
        transferRequest.setDescription("Test transfer");

        transaction = Transaction.builder()
                .senderWallet(senderWallet)
                .receiverWallet(receiverWallet)
                .amount(BigDecimal.valueOf(200))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .referenceId("test-ref-123")
                .description("Test transfer")
                .build();
        transaction.setId(1L);

        // Mock rate limiter Lua script via stringRedisTemplate (returning 1L = allowed)
        when(stringRedisTemplate.execute(any(), any(java.util.List.class), any(Object[].class))).thenReturn(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
    }

    // =====================
    // TRANSFER TESTS
    // =====================

    @Test
    void transfer_ShouldSucceed_WhenValidRequest() {
        when(redisService.exists(anyString())).thenReturn(false);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        TransactionResponse response = transactionService.transfer(1L, transferRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        verify(walletRepository, atLeast(2)).save(any(Wallet.class));
        verify(notificationProducer, times(1)).sendTransactionEvent(any());
    }

    @Test
    void transfer_ShouldThrowException_WhenInsufficientBalance() {
        transferRequest.setAmount(BigDecimal.valueOf(9999));

        when(redisService.exists(anyString())).thenReturn(false);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        assertThatThrownBy(() -> transactionService.transfer(1L, transferRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient balance");
    }

    @Test
    void transfer_ShouldThrowException_WhenSenderWalletNotFound() {
        when(walletRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(99L, transferRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Sender wallet not found");
    }

    @Test
    void transfer_ShouldThrowException_WhenReceiverWalletNotFound() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(1L, transferRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Receiver wallet not found");
    }

    @Test
    void transfer_ShouldThrowException_WhenSenderWalletInactive() {
        senderWallet.setStatus(WalletStatus.SUSPENDED);

        when(redisService.exists(anyString())).thenReturn(false);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        assertThatThrownBy(() -> transactionService.transfer(1L, transferRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Sender wallet is not active");
    }

    @Test
    void transfer_ShouldThrowException_WhenTransferToSameWallet() {
        transferRequest.setReceiverWalletId(1L);

        when(redisService.exists(anyString())).thenReturn(false);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        assertThatThrownBy(() -> transactionService.transfer(1L, transferRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot transfer to same wallet");
    }

    @Test
    void transfer_ShouldReturnExisting_WhenDuplicateIdempotencyKey() {
        transferRequest.setIdempotencyKey("duplicate-key-123");

        when(redisService.exists("idempotency:duplicate-key-123")).thenReturn(true);
        when(redisService.get("idempotency:duplicate-key-123"))
                .thenReturn(Optional.of("test-ref-123"));
        when(transactionRepository.findByReferenceId("test-ref-123"))
                .thenReturn(Optional.of(transaction));

        TransactionResponse response = transactionService.transfer(1L, transferRequest);

        assertThat(response).isNotNull();
        assertThat(response.getReferenceId()).isEqualTo("test-ref-123");

        verify(walletRepository, never()).findByUserId(anyLong());
    }

    // =====================
    // DEPOSIT TESTS
    // =====================

    @Test
    void deposit_ShouldSucceed_WhenValidRequest() {
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        TransactionResponse response = transactionService.deposit(1L, BigDecimal.valueOf(500));

        assertThat(response).isNotNull();
        verify(walletRepository, atLeast(1)).save(any(Wallet.class));
        verify(notificationProducer, times(1)).sendTransactionEvent(any());
    }

    @Test
    void deposit_ShouldThrowException_WhenWalletNotFound() {
        when(walletRepository.findByUserIdForUpdate(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.deposit(99L, BigDecimal.valueOf(500)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Wallet not found");
    }

    // =====================
    // TRANSACTION HISTORY TESTS
    // =====================

    @Test
    void getTransactionHistory_ShouldReturnPage_WhenExists() {
        Page<Transaction> page = new PageImpl<>(List.of(transaction));
        when(transactionRepository.findByWalletId(anyLong(), any(PageRequest.class)))
                .thenReturn(page);

        Page<TransactionResponse> result = transactionService
                .getTransactionHistory(1L, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReferenceId()).isEqualTo("test-ref-123");
    }
}
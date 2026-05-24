package com.paymentprocessing.wallet.common.config;

import com.paymentprocessing.wallet.ledger.entity.AccountType;
import com.paymentprocessing.wallet.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final LedgerService ledgerService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing system accounts...");

        ledgerService.getOrCreateAccount(
                "SYS-CASH", "System Cash Account", AccountType.ASSET);

        ledgerService.getOrCreateAccount(
                "SYS-LIABILITY", "System Liability Account", AccountType.LIABILITY);

        ledgerService.getOrCreateAccount(
                "SYS-REVENUE", "System Revenue Account", AccountType.REVENUE);

        log.info("System accounts initialized successfully!");
    }
}
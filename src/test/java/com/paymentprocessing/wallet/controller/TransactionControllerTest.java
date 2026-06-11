package com.paymentprocessing.wallet.controller;

import com.paymentprocessing.wallet.integration.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessing.wallet.auth.security.JwtService;
import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.transaction.dto.TransactionResponse;
import com.paymentprocessing.wallet.transaction.dto.TransferRequest;
import com.paymentprocessing.wallet.transaction.entity.TransactionStatus;
import com.paymentprocessing.wallet.transaction.entity.TransactionType;
import com.paymentprocessing.wallet.transaction.service.TransactionService;
import com.paymentprocessing.wallet.user.entity.Role;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.repository.UserRepository;
import com.paymentprocessing.wallet.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionControllerTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    private String validToken;
    private User mockUser;
    private TransactionResponse mockTransactionResponse;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("john@example.com")
                .password("encoded")
                .fullName("John Doe")
                .role(Role.USER)
                .build();
        mockUser.setId(1L);

        mockTransactionResponse = TransactionResponse.builder()
                .id(1L)
                .senderWalletId(1L)
                .receiverWalletId(2L)
                .amount(BigDecimal.valueOf(200))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .referenceId("ref-123")
                .build();

        validToken = "Bearer " + jwtService.generateToken("john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
        when(userService.findByEmail("john@example.com")).thenReturn(mockUser);
    }

    // =====================
    // TRANSFER
    // =====================

    @Test
    void transfer_ShouldReturn200_WhenValidRequest() throws Exception {
        when(transactionService.transfer(anyLong(), any())).thenReturn(mockTransactionResponse);

        TransferRequest request = new TransferRequest();
        request.setReceiverWalletId(2L);
        request.setAmount(BigDecimal.valueOf(200));

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.referenceId").value("ref-123"));
    }

    @Test
    void transfer_ShouldReturn401_WhenNoToken() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setReceiverWalletId(2L);
        request.setAmount(BigDecimal.valueOf(200));

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transfer_ShouldReturn400_WhenAmountIsNull() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setReceiverWalletId(2L);
        request.setAmount(null);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void transfer_ShouldReturn400_WhenAmountIsNegative() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setReceiverWalletId(2L);
        request.setAmount(BigDecimal.valueOf(-100));

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void transfer_ShouldReturn400_WhenAmountIsZero() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setReceiverWalletId(2L);
        request.setAmount(BigDecimal.ZERO);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void transfer_ShouldReturn400_WhenReceiverWalletIdIsNull() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setReceiverWalletId(null);
        request.setAmount(BigDecimal.valueOf(200));

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void transfer_ShouldReturn400_WhenInsufficientBalance() throws Exception {
        when(transactionService.transfer(anyLong(), any()))
                .thenThrow(new BadRequestException("Insufficient balance"));

        TransferRequest request = new TransferRequest();
        request.setReceiverWalletId(2L);
        request.setAmount(BigDecimal.valueOf(99999));

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient balance"));
    }

    // =====================
    // DEPOSIT
    // =====================

    @Test
    void deposit_ShouldReturn200_WhenValidRequest() throws Exception {
        TransactionResponse depositResponse = TransactionResponse.builder()
                .id(2L)
                .receiverWalletId(1L)
                .amount(BigDecimal.valueOf(500))
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .referenceId("ref-deposit-123")
                .build();

        when(transactionService.deposit(anyLong(), any())).thenReturn(depositResponse);

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", validToken)
                        .param("amount", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void deposit_ShouldReturn401_WhenNoToken() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .param("amount", "500"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deposit_ShouldReturn400_WhenAmountIsNegative() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", validToken)
                        .param("amount", "-500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =====================
    // TRANSACTION HISTORY
    // =====================

    @Test
    void getHistory_ShouldReturn200_WhenAuthenticated() throws Exception {
        when(transactionService.getTransactionHistory(anyLong(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/transactions/history")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getHistory_ShouldReturn401_WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/transactions/history"))
                .andExpect(status().isUnauthorized());
    }
}
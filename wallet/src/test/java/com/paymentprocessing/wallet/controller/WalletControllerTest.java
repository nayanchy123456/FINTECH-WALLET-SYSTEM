package com.paymentprocessing.wallet.controller;

import com.paymentprocessing.wallet.integration.IntegrationTestBase;

import com.paymentprocessing.wallet.auth.security.JwtService;
import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.user.entity.Role;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.repository.UserRepository;
import com.paymentprocessing.wallet.user.service.UserService;
import com.paymentprocessing.wallet.wallet.dto.WalletResponse;
import com.paymentprocessing.wallet.wallet.entity.WalletStatus;
import com.paymentprocessing.wallet.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import org.springframework.boot.test.mock.mockito.MockBean;


import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;




class WalletControllerTest extends IntegrationTestBase {

    @Autowired
    private JwtService jwtService;

    @MockBean
    private WalletService walletService;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    private String validToken;
    private User mockUser;
    private WalletResponse mockWalletResponse;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("john@example.com")
                .password("encoded")
                .fullName("John Doe")
                .role(Role.USER)
                .build();
        mockUser.setId(1L);

        mockWalletResponse = WalletResponse.builder()
                .id(1L)
                .userId(1L)
                .balance(BigDecimal.valueOf(1000))
                .status(WalletStatus.ACTIVE)
                .build();

        validToken = "Bearer " + jwtService.generateToken("john@example.com");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
        when(userService.findByEmail("john@example.com")).thenReturn(mockUser);
    }

    // =====================
    // GET MY WALLET
    // =====================

    @Test
    void getMyWallet_ShouldReturn200_WhenAuthenticated() throws Exception {
        when(walletService.getWalletByUserId(1L)).thenReturn(mockWalletResponse);

        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(1000))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getMyWallet_ShouldReturn401_WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/wallets/my-wallet"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyWallet_ShouldReturn401_WhenTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/wallets/my-wallet")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // =====================
    // GET WALLET BY ID
    // =====================

    @Test
    void getWalletById_ShouldReturn200_WhenExists() throws Exception {
        when(walletService.getWalletById(1L)).thenReturn(mockWalletResponse);

        mockMvc.perform(get("/api/wallets/1")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getWalletById_ShouldReturn404_WhenNotFound() throws Exception {
        when(walletService.getWalletById(anyLong()))
                .thenThrow(new com.paymentprocessing.wallet.common.exception
                        .ResourceNotFoundException("Wallet not found"));

        mockMvc.perform(get("/api/wallets/999")
                        .header("Authorization", validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Wallet not found"));
    }

    @Test
    void getWalletById_ShouldReturn401_WhenNoToken() throws Exception {
        mockMvc.perform(get("/api/wallets/1"))
                .andExpect(status().isUnauthorized());
    }

    // =====================
    // CREATE WALLET
    // =====================

    @Test
    void createWallet_ShouldReturn200_WhenAuthenticated() throws Exception {
        when(walletService.createWallet(1L)).thenReturn(mockWalletResponse);

        mockMvc.perform(post("/api/wallets/create")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createWallet_ShouldReturn400_WhenWalletAlreadyExists() throws Exception {
        when(walletService.createWallet(anyLong()))
                .thenThrow(new BadRequestException("Wallet already exists for this user"));

        mockMvc.perform(post("/api/wallets/create")
                        .header("Authorization", validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wallet already exists for this user"));
    }

    @Test
    void createWallet_ShouldReturn401_WhenNoToken() throws Exception {
        mockMvc.perform(post("/api/wallets/create"))
                .andExpect(status().isUnauthorized());
    }
}
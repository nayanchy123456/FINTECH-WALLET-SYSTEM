package com.paymentprocessing.wallet.wallet.service;

import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.user.entity.Role;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;
import com.paymentprocessing.wallet.wallet.dto.WalletResponse;
import com.paymentprocessing.wallet.wallet.entity.Wallet;
import com.paymentprocessing.wallet.wallet.entity.WalletStatus;
import com.paymentprocessing.wallet.wallet.repository.WalletRepository;
import com.paymentprocessing.wallet.wallet.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("john@test.com")
                .password("encodedPassword")
                .fullName("John Doe")
                .role(Role.USER)
                .build();
        user.setId(1L);

        wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.valueOf(1000))
                .status(WalletStatus.ACTIVE)
                .build();
        wallet.setId(1L);
    }

    // =====================
    // CREATE WALLET TESTS
    // =====================

    @Test
    void createWallet_ShouldReturnWallet_WhenUserExists() {
        when(walletRepository.existsByUserId(1L)).thenReturn(false);
        when(userService.findById(1L)).thenReturn(user);
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        WalletResponse response = walletService.createWallet(1L);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUserEmail()).isEqualTo("john@test.com");
        assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void createWallet_ShouldThrowException_WhenWalletAlreadyExists() {
        when(walletRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> walletService.createWallet(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Wallet already exists for this user");

        verify(walletRepository, never()).save(any());
    }

    // =====================
    // GET WALLET TESTS
    // =====================

    @Test
    void getWalletByUserId_ShouldReturnWallet_WhenExists() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWalletByUserId(1L);

        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void getWalletByUserId_ShouldThrowException_WhenNotFound() {
        when(walletRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWalletByUserId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Wallet not found for user");
    }

    @Test
    void getWalletById_ShouldReturnWallet_WhenExists() {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWalletById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void getWalletById_ShouldThrowException_WhenNotFound() {
        when(walletRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWalletById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Wallet not found");
    }
}
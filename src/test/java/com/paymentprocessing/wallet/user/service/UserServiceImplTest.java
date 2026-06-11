package com.paymentprocessing.wallet.user.service;

import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.user.dto.UpdateProfileRequest;
import com.paymentprocessing.wallet.user.dto.UserResponse;
import com.paymentprocessing.wallet.user.entity.Role;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.repository.UserRepository;
import com.paymentprocessing.wallet.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@gmail.com")
                .password("encoded")
                .fullName("Test User")
                .role(Role.USER)
                .build();
        user.setId(1L);
    }

    // =====================
    // GET PROFILE TESTS
    // =====================

    @Test
    void getProfile_ShouldReturnUserResponse_WhenUserExists() {
        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        UserResponse response = userService.getProfile("test@gmail.com");

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@gmail.com");
        assertThat(response.getFullName()).isEqualTo("Test User");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    void getProfile_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile("unknown@gmail.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // =====================
    // GET USER BY ID TESTS
    // =====================

    @Test
    void getUserById_ShouldReturnUserResponse_WhenUserExists() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@gmail.com");
    }

    @Test
    void getUserById_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // =====================
    // UPDATE PROFILE TESTS
    // =====================

    @Test
    void updateProfile_ShouldUpdateFullName_WhenValidRequest() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        UserResponse response = userService.updateProfile(
                "test@gmail.com", request);

        assertThat(response).isNotNull();
        assertThat(user.getFullName()).isEqualTo("Updated Name");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateProfile_ShouldThrowException_WhenUserNotFound() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");

        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(
                "unknown@gmail.com", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // =====================
    // FIND BY EMAIL TESTS
    // =====================

    @Test
    void findByEmail_ShouldReturnUser_WhenExists() {
        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        User result = userService.findByEmail("test@gmail.com");

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@gmail.com");
    }

    @Test
    void findByEmail_ShouldThrowException_WhenNotFound() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("unknown@gmail.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // =====================
    // FIND BY ID TESTS
    // =====================

    @Test
    void findById_ShouldReturnUser_WhenExists() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void findById_ShouldThrowException_WhenNotFound() {
        when(userRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }
}
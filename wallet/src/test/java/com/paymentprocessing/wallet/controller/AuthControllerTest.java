package com.paymentprocessing.wallet.controller;

import com.paymentprocessing.wallet.integration.IntegrationTestBase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessing.wallet.auth.dto.AuthResponse;
import com.paymentprocessing.wallet.auth.dto.LoginRequest;
import com.paymentprocessing.wallet.auth.dto.RegisterRequest;
import com.paymentprocessing.wallet.auth.service.AuthService;
import com.paymentprocessing.wallet.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;



import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;




class AuthControllerTest extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // =====================
    // REGISTER
    // =====================

    @Test
    void register_ShouldReturn200_WhenValidRequest() throws Exception {
        AuthResponse mockResponse = AuthResponse.builder()
                .userId(1L)
                .email("john@example.com")
                .fullName("John Doe")
                .token("mock-jwt-token")
                .role("USER")
                .build();

        when(authService.register(any())).thenReturn(mockResponse);

        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("Password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    void register_ShouldReturn400_WhenEmailBlank() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("");
        request.setPassword("Password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_ShouldReturn400_WhenEmailInvalidFormat() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("not-an-email");
        request.setPassword("Password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_ShouldReturn400_WhenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("Ab1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_ShouldReturn400_WhenPasswordHasNoUppercase() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_ShouldReturn400_WhenEmailAlreadyExists() throws Exception {
        when(authService.register(any()))
                .thenThrow(new BadRequestException("Email already registered"));

        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("existing@example.com");
        request.setPassword("Password1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    // =====================
    // LOGIN
    // =====================

    @Test
    void login_ShouldReturn200_WhenValidCredentials() throws Exception {
        AuthResponse mockResponse = AuthResponse.builder()
                .userId(1L)
                .email("john@example.com")
                .fullName("John Doe")
                .token("mock-jwt-token")
                .role("USER")
                .build();

        when(authService.login(any())).thenReturn(mockResponse);

        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    void login_ShouldReturn400_WhenEmailBlank() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("");
        request.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_ShouldReturn400_WhenPasswordBlank() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_ShouldReturn400_WhenInvalidCredentials() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BadRequestException("Invalid email or password"));

        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("WrongPass1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
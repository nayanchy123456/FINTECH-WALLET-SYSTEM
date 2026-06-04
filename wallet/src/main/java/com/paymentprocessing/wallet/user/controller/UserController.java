package com.paymentprocessing.wallet.user.controller;

import com.paymentprocessing.wallet.common.response.ApiResponse;
import com.paymentprocessing.wallet.common.util.SecurityUtil;
import com.paymentprocessing.wallet.user.dto.UpdateProfileRequest;
import com.paymentprocessing.wallet.user.dto.UserResponse;
import com.paymentprocessing.wallet.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.paymentprocessing.wallet.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile() {
        String email = SecurityUtil.getCurrentUserEmail();
        UserResponse response = userService.getProfile(email);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile fetched successfully"));
    }


    @PatchMapping("/profile")
public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
        @Valid @RequestBody UpdateProfileRequest request) {
    String email = SecurityUtil.getCurrentUserEmail();
    UserResponse response = userService.updateProfile(email, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
}

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "User fetched successfully"));
    }
}
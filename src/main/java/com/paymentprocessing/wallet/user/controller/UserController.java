package com.paymentprocessing.wallet.user.controller;

import com.paymentprocessing.wallet.common.exception.BadRequestException;
import com.paymentprocessing.wallet.common.response.ApiResponse;
import com.paymentprocessing.wallet.common.util.SecurityUtil;
import com.paymentprocessing.wallet.user.dto.UpdateProfileRequest;
import com.paymentprocessing.wallet.user.dto.UserResponse;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // Alias so both /api/users/me and /api/users/profile work
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        return getProfile();
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
        // Fetch first — throws ResourceNotFoundException (404) if not found
        UserResponse response = userService.getUserById(id);

        // Ownership check — only the authenticated user may fetch their own record
        String email = SecurityUtil.getCurrentUserEmail();
        User currentUser = userService.findByEmail(email);
        if (!currentUser.getId().equals(id)) {
            throw new BadRequestException("Access denied: you can only view your own profile");
        }

        return ResponseEntity.ok(ApiResponse.success(response, "User fetched successfully"));
    }
}
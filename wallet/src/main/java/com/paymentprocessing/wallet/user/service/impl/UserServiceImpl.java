package com.paymentprocessing.wallet.user.service.impl;

import com.paymentprocessing.wallet.common.exception.ResourceNotFoundException;
import com.paymentprocessing.wallet.user.dto.UpdateProfileRequest;
import com.paymentprocessing.wallet.user.dto.UserResponse;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.repository.UserRepository;
import com.paymentprocessing.wallet.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import com.paymentprocessing.wallet.user.dto.UpdateProfileRequest;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponse getProfile(String email) {
        User user = findByEmail(email);
        return mapToResponse(user);
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = findById(id);
        return mapToResponse(user);
    }

    @Override
@Transactional
public UserResponse updateProfile(String email, UpdateProfileRequest request) {
    User user = findByEmail(email);
    user.setFullName(request.getFullName());
    User updated = userRepository.save(user);
    log.info("Profile updated for user: {}", email);
    return mapToResponse(updated);
}

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
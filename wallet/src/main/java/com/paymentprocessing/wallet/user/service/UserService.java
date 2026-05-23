package com.paymentprocessing.wallet.user.service;

import com.paymentprocessing.wallet.user.dto.UserResponse;
import com.paymentprocessing.wallet.user.entity.User;

public interface UserService {
    UserResponse getProfile(String email);
    UserResponse getUserById(Long id);
    User findByEmail(String email);
    User findById(Long id);
}
package com.paymentprocessing.wallet.notification.controller;

import com.paymentprocessing.wallet.common.response.ApiResponse;
import com.paymentprocessing.wallet.common.util.SecurityUtil;
import com.paymentprocessing.wallet.notification.dto.NotificationResponse;
import com.paymentprocessing.wallet.notification.service.NotificationService;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Notification APIs")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications() {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        List<NotificationResponse> response =
                notificationService.getUserNotifications(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response,
                "Notifications fetched successfully"));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications() {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        List<NotificationResponse> response =
                notificationService.getUnreadNotifications(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response,
                "Unread notifications fetched successfully"));
    }
}
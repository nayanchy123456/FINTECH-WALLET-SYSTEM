package com.paymentprocessing.wallet.notification.controller;

import com.paymentprocessing.wallet.common.response.ApiResponse;
import com.paymentprocessing.wallet.common.util.SecurityUtil;
import com.paymentprocessing.wallet.notification.dto.NotificationResponse;
import com.paymentprocessing.wallet.notification.service.NotificationService;
import com.paymentprocessing.wallet.user.entity.User;
import com.paymentprocessing.wallet.user.service.UserService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationResponse> response =
                notificationService.getUserNotifications(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response,
                "Notifications fetched successfully"));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getUnreadNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String email = SecurityUtil.getCurrentUserEmail();
        User user = userService.findByEmail(email);
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationResponse> response =
                notificationService.getUnreadNotifications(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response,
                "Unread notifications fetched successfully"));
    }

    @PatchMapping("/{notificationId}/read")
public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
        @PathVariable Long notificationId) {
    String email = SecurityUtil.getCurrentUserEmail();
    User user = userService.findByEmail(email);
    NotificationResponse response =
            notificationService.markAsRead(notificationId, user.getId());
    return ResponseEntity.ok(ApiResponse.success(response,
            "Notification marked as read"));
}

@PatchMapping("/read-all")
public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
    String email = SecurityUtil.getCurrentUserEmail();
    User user = userService.findByEmail(email);
    notificationService.markAllAsRead(user.getId());
    return ResponseEntity.ok(ApiResponse.success(null,
            "All notifications marked as read"));
}
}
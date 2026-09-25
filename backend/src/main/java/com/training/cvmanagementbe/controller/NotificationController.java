package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.notifications.NotificationResponse;
import com.training.cvmanagementbe.dto.response.notifications.UnreadCountResponse;
import com.training.cvmanagementbe.enums.notifications.NotificationSortField;
import com.training.cvmanagementbe.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.NOTIFICATIONS)
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification feed of the signed-in user")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List the caller's notifications, newest first")
    public ResponseEntity<PagedResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        // Fixed order: a feed is always read newest first.
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page),
                PageDefaults.clampSize(size),
                Sort.by(Sort.Direction.DESC,
                        NotificationSortField.CREATED_AT.getProperty(),
                        NotificationSortField.ID.getProperty())
        );
        return ResponseEntity.ok(notificationService.list(unreadOnly, pageable));
    }

    @GetMapping(ApiPath.NOTIFICATIONS_UNREAD_COUNT)
    @Operation(summary = "Count the caller's unread notifications")
    public ResponseEntity<UnreadCountResponse> countUnread() {
        return ResponseEntity.ok(notificationService.countUnread());
    }

    @PatchMapping(ApiPath.NOTIFICATION_READ)
    @Operation(summary = "Mark one notification read; returns the new unread count")
    public ResponseEntity<UnreadCountResponse> markAsRead(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @PatchMapping(ApiPath.NOTIFICATIONS_READ_ALL)
    @Operation(summary = "Mark every notification of the caller read; returns the new unread count")
    public ResponseEntity<UnreadCountResponse> markAllAsRead() {
        return ResponseEntity.ok(notificationService.markAllAsRead());
    }
}

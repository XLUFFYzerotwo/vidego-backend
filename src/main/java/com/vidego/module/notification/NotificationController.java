package com.vidego.module.notification;

import com.vidego.auth.UserContext;
import com.vidego.common.result.PageResult;
import com.vidego.common.result.Result;
import com.vidego.module.notification.dto.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "Notification center")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Get paginated notification list")
    @GetMapping
    public Result<PageResult<NotificationVO>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        return Result.success(notificationService.getNotifications(userId, page, size));
    }

    @Operation(summary = "Get unread notification count")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @Operation(summary = "Mark a notification as read")
    @PutMapping("/{notificationId}/read")
    public Result<Void> markAsRead(@PathVariable Long notificationId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        notificationService.markAsRead(notificationId, userId);
        return Result.success();
    }

    @Operation(summary = "Mark all notifications as read")
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error(401, "unauthorized");
        }
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}

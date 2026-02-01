package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.Notification;
import com.caat.service.NotificationManagementService;
import com.caat.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 通知管理控制器
 */
@Tag(name = "通知管理", description = "通知查询和管理接口")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationManagementService notificationManagementService;
    private final NotificationService notificationService;
    
    @Operation(summary = "获取通知列表", description = "分页获取通知列表，支持按已读状态过滤")
    @GetMapping
    public ApiResponse<Page<Notification>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean isRead
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ApiResponse.success(notificationManagementService.getNotifications(pageable, isRead));
    }
    
    @Operation(summary = "获取未读通知列表", description = "获取未读通知列表")
    @GetMapping("/unread")
    public ApiResponse<Page<Notification>> getUnreadNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ApiResponse.success(notificationManagementService.getUnreadNotifications(pageable));
    }
    
    @Operation(summary = "获取未读通知数量", description = "获取未读通知的总数")
    @GetMapping("/unread/count")
    public ApiResponse<Map<String, Long>> getUnreadCount() {
        long count = notificationManagementService.getUnreadCount();
        return ApiResponse.success(Map.of("count", count));
    }
    
    @Operation(summary = "标记通知为已读", description = "将指定通知标记为已读")
    @PutMapping("/{id}/read")
    public ApiResponse<Notification> markAsRead(@PathVariable UUID id) {
        return ApiResponse.success(notificationManagementService.markAsRead(id));
    }
    
    @Operation(summary = "标记所有通知为已读", description = "将所有未读通知标记为已读")
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        notificationManagementService.markAllAsRead();
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "删除通知", description = "删除指定通知")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(@PathVariable UUID id) {
        notificationManagementService.deleteNotification(id);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "获取通知详情", description = "根据 ID 获取通知详情")
    @GetMapping("/{id}")
    public ApiResponse<Notification> getNotification(@PathVariable UUID id) {
        return ApiResponse.success(notificationManagementService.getNotificationById(id));
    }

    @Operation(summary = "按配置测试下发", description = "不依赖已保存规则，按传入的规则类型与配置发送一条测试消息到 QQ 群/飞书")
    @PostMapping("/test-with-config")
    public ApiResponse<Map<String, Object>> testWithConfig(@RequestBody Map<String, Object> body) {
        String ruleType = body != null && body.get("ruleType") != null ? body.get("ruleType").toString().trim() : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> config = body != null && body.get("config") instanceof Map ? (Map<String, Object>) body.get("config") : null;
        String error = notificationService.sendTestMessageWithConfig(ruleType, config);
        if (error == null) {
            return ApiResponse.success(Map.of("success", true, "message", "测试消息已发送"));
        }
        return ApiResponse.success(Map.of("success", false, "message", error));
    }
}

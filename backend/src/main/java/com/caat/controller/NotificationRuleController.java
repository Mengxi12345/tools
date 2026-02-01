package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.NotificationRule;
import com.caat.service.NotificationRuleService;
import com.caat.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "通知规则", description = "通知规则管理接口")
@RestController
@RequestMapping("/api/v1/notification-rules")
@RequiredArgsConstructor
public class NotificationRuleController {

    private final NotificationRuleService notificationRuleService;
    private final NotificationService notificationService;

    @Operation(summary = "分页列表")
    @GetMapping
    public ApiResponse<Page<NotificationRule>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(notificationRuleService.findAll(pageable));
    }

    @Operation(summary = "根据 ID 查询")
    @GetMapping("/{id}")
    public ApiResponse<NotificationRule> getById(@PathVariable UUID id) {
        return ApiResponse.success(notificationRuleService.getById(id));
    }

    @Operation(summary = "创建规则")
    @PostMapping
    public ApiResponse<NotificationRule> create(@RequestBody NotificationRule rule) {
        return ApiResponse.success(notificationRuleService.create(rule));
    }

    @Operation(summary = "更新规则")
    @PutMapping("/{id}")
    public ApiResponse<NotificationRule> update(
        @PathVariable UUID id,
        @RequestBody NotificationRule rule
    ) {
        return ApiResponse.success(notificationRuleService.update(id, rule));
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        notificationRuleService.delete(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "测试下发消息", description = "向规则配置的机器人（QQ 群 / 飞书）发送一条测试消息，仅支持 QQ_GROUP、FEISHU 类型")
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable UUID id) {
        NotificationRule rule;
        try {
            rule = notificationRuleService.getById(id);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, "规则不存在");
        }
        String error = notificationService.sendTestMessage(rule);
        if (error == null) {
            return ApiResponse.success(Map.of("success", true, "message", "测试消息已发送"));
        }
        return ApiResponse.success(Map.of("success", false, "message", error));
    }
}

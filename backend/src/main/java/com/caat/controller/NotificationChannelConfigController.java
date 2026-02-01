package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.dto.NotificationChannelConfigCreateRequest;
import com.caat.entity.NotificationChannelConfig;
import com.caat.service.NotificationChannelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "通知通道配置", description = "通知通道配置存储与共享")
@RestController
@RequestMapping("/api/v1/notification-channel-configs")
@RequiredArgsConstructor
public class NotificationChannelConfigController {

    private final NotificationChannelConfigService service;

    @Operation(summary = "列表", description = "按通道类型筛选；不传 channelType 时返回全部")
    @GetMapping
    public ApiResponse<List<NotificationChannelConfig>> list(
        @RequestParam(required = false) String channelType
    ) {
        List<NotificationChannelConfig> list = channelType != null && !channelType.isBlank()
            ? service.findByChannelType(channelType.trim())
            : service.findAll();
        return ApiResponse.success(list);
    }

    @Operation(summary = "根据 ID 查询")
    @GetMapping("/{id}")
    public ApiResponse<NotificationChannelConfig> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @Operation(summary = "创建通道配置")
    @PostMapping
    public ApiResponse<NotificationChannelConfig> create(@RequestBody NotificationChannelConfigCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "更新通道配置")
    @PutMapping("/{id}")
    public ApiResponse<NotificationChannelConfig> update(
        @PathVariable UUID id,
        @RequestBody NotificationChannelConfigCreateRequest request
    ) {
        return ApiResponse.success(service.update(id, request));
    }

    @Operation(summary = "删除通道配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}

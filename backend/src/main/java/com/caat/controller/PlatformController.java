package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.dto.PlatformCreateRequest;
import com.caat.dto.PlatformUpdateRequest;
import com.caat.entity.Platform;
import com.caat.service.PlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 平台管理控制器
 */
@Tag(name = "平台管理", description = "平台配置和管理接口")
@RestController
@RequestMapping("/api/v1/platforms")
@RequiredArgsConstructor
public class PlatformController {
    
    private final PlatformService platformService;
    
    @Operation(summary = "获取平台列表", description = "获取所有已配置的平台")
    @GetMapping
    public ApiResponse<List<Platform>> getAllPlatforms() {
        return ApiResponse.success(platformService.getAllPlatforms());
    }
    
    @Operation(summary = "获取平台详情", description = "根据 ID 获取平台详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Platform> getPlatform(@PathVariable UUID id) {
        return ApiResponse.success(platformService.getPlatformById(id));
    }
    
    @Operation(summary = "创建平台", description = "创建新的平台配置")
    @PostMapping
    public ApiResponse<Platform> createPlatform(@Valid @RequestBody PlatformCreateRequest request) {
        Platform platform = new Platform();
        platform.setName(request.getName());
        platform.setType(request.getType());
        platform.setApiBaseUrl(request.getApiBaseUrl());
        platform.setAuthType(request.getAuthType());
        platform.setConfig(request.getConfig());
        platform.setStatus(Platform.PlatformStatus.ACTIVE);
        
        return ApiResponse.success(platformService.createPlatform(platform));
    }
    
    @Operation(summary = "更新平台", description = "更新平台配置信息")
    @PutMapping("/{id}")
    public ApiResponse<Platform> updatePlatform(
        @PathVariable UUID id,
        @Valid @RequestBody PlatformUpdateRequest request
    ) {
        Platform platform = new Platform();
        platform.setType(request.getType());
        platform.setApiBaseUrl(request.getApiBaseUrl());
        platform.setAuthType(request.getAuthType());
        platform.setConfig(request.getConfig());
        if (request.getStatus() != null) {
            platform.setStatus(Platform.PlatformStatus.valueOf(request.getStatus()));
        }
        
        return ApiResponse.success(platformService.updatePlatform(id, platform));
    }
    
    @Operation(summary = "删除平台", description = "删除平台配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlatform(@PathVariable UUID id) {
        platformService.deletePlatform(id);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "测试平台连接", description = "测试平台 API 连接是否正常")
    @PostMapping("/{id}/test")
    public ApiResponse<Boolean> testConnection(@PathVariable UUID id) {
        boolean success = platformService.testConnection(id);
        return ApiResponse.success(success);
    }
}

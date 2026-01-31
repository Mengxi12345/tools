package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.dto.UserCreateRequest;
import com.caat.dto.UserStatsResponse;
import com.caat.dto.UserUpdateRequest;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.service.TrackedUserService;
import com.caat.service.UserAvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理", description = "追踪用户管理接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final TrackedUserService trackedUserService;
    private final UserAvatarService userAvatarService;

    /** 使用固定路径 /upload-avatar，避免与 /{id} 误匹配 */
    @Operation(summary = "上传用户头像", description = "上传图片文件，保存到本地并返回访问路径")
    @PostMapping("/upload-avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = userAvatarService.saveUploadedFile(file);
        return ApiResponse.success(Map.of("url", url));
    }

    @Operation(summary = "获取用户列表", description = "分页获取所有追踪用户，支持标签和优先级过滤")
    @GetMapping
    public ApiResponse<Page<TrackedUser>> getAllUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDir,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) Integer minPriority,
        @RequestParam(required = false) Integer maxPriority
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        // 按标签过滤
        if (tag != null && !tag.isEmpty()) {
            return ApiResponse.success(trackedUserService.getUsersByTag(tag, pageable));
        }
        
        // 按优先级范围过滤
        if (minPriority != null || maxPriority != null) {
            int min = minPriority != null ? minPriority : Integer.MIN_VALUE;
            int max = maxPriority != null ? maxPriority : Integer.MAX_VALUE;
            return ApiResponse.success(trackedUserService.getUsersByPriorityRange(min, max, pageable));
        }
        
        return ApiResponse.success(trackedUserService.getAllUsers(pageable));
    }
    
    @Operation(summary = "获取用户文章总数", description = "批量获取指定用户的文章总数，用于列表展示；ids 为空时返回空 Map")
    @GetMapping("/content-counts")
    public ApiResponse<Map<UUID, Long>> getContentCounts(@RequestParam(required = false) List<UUID> ids) {
        return ApiResponse.success(trackedUserService.getContentCountsByUserIds(ids != null ? ids : List.of()));
    }

    @Operation(summary = "获取用户详情", description = "根据 ID 获取用户详细信息")
    @GetMapping("/{id}")
    public ApiResponse<TrackedUser> getUser(@PathVariable UUID id) {
        return ApiResponse.success(trackedUserService.getUserById(id));
    }
    
    @Operation(summary = "创建用户", description = "添加新的追踪用户")
    @PostMapping
    public ApiResponse<TrackedUser> createUser(@Valid @RequestBody UserCreateRequest request) {
        TrackedUser user = new TrackedUser();
        Platform platform = new Platform();
        platform.setId(request.getPlatformId());
        user.setPlatform(platform);
        user.setUsername(request.getUsername());
        user.setUserId(request.getUserId());
        user.setDisplayName(request.getDisplayName());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setGroupId(request.getGroupId());
        user.setTags(request.getTags());
        user.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        user.setIsActive(true);
        
        return ApiResponse.success(trackedUserService.createUser(user));
    }
    
    @Operation(summary = "更新用户", description = "更新用户信息")
    @PutMapping("/{id}")
    public ApiResponse<TrackedUser> updateUser(
        @PathVariable UUID id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        TrackedUser user = new TrackedUser();
        user.setDisplayName(request.getDisplayName());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setSelfIntroduction(request.getSelfIntroduction());
        user.setGroupId(request.getGroupId());
        user.setTags(request.getTags());
        user.setPriority(request.getPriority());
        user.setIsActive(request.getIsActive());
        
        return ApiResponse.success(trackedUserService.updateUser(id, user));
    }
    
    @Operation(summary = "删除用户", description = "删除追踪用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable UUID id) {
        trackedUserService.deleteUser(id);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "启用/禁用用户", description = "切换用户的启用状态")
    @PutMapping("/{id}/toggle")
    public ApiResponse<TrackedUser> toggleUserStatus(
        @PathVariable UUID id,
        @RequestParam boolean isActive
    ) {
        return ApiResponse.success(trackedUserService.toggleUserStatus(id, isActive));
    }
    
    @Operation(summary = "拉取用户资料", description = "从平台拉取用户头像与简介并更新到数据库（仅 TimeStore 等支持 profile 的平台有效）")
    @PostMapping("/{id}/refresh-profile")
    public ApiResponse<TrackedUser> refreshProfile(@PathVariable UUID id) {
        return ApiResponse.success(trackedUserService.refreshProfile(id));
    }

    @Operation(summary = "获取用户统计", description = "获取用户的统计信息")
    @GetMapping("/{id}/stats")
    public ApiResponse<UserStatsResponse> getUserStats(@PathVariable UUID id) {
        // TODO: 实现统计逻辑
        UserStatsResponse stats = UserStatsResponse.builder()
            .totalContents(0L)
            .unreadContents(0L)
            .favoriteContents(0L)
            .build();
        return ApiResponse.success(stats);
    }
}

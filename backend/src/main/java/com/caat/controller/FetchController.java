package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.dto.BatchFetchRequest;
import com.caat.dto.FetchRequest;
import com.caat.entity.FetchTask;
import com.caat.entity.TrackedUser;
import com.caat.repository.FetchTaskRepository;
import com.caat.repository.TrackedUserRepository;
import com.caat.service.ContentFetchService;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 内容拉取控制器
 */
@Slf4j
@Tag(name = "内容拉取", description = "手动刷新和任务管理接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FetchController {
    
    private final ContentFetchService contentFetchService;
    private final FetchTaskRepository fetchTaskRepository;
    private final TrackedUserRepository trackedUserRepository;
    
    @Operation(summary = "刷新用户内容", description = "手动触发拉取指定用户的内容")
    @PostMapping("/users/{id}/fetch")
    public ApiResponse<FetchTask> fetchUserContent(
        @PathVariable UUID id,
        @RequestBody(required = false) FetchRequest request
    ) {
        String fetchMode = request != null && "fast".equalsIgnoreCase(request.getFetchMode()) ? "fast" : "normal";
        // 完整拉取时不传日期参数，逐页 50 条直到无数据；快速拉取或指定了时间范围时才传 start/end
        LocalDateTime startTime;
        LocalDateTime endTime;
        if ("normal".equalsIgnoreCase(fetchMode)
            && (request == null || (request.getStartTime() == null && request.getEndTime() == null))) {
            startTime = null;
            endTime = null;
        } else {
            startTime = request != null && request.getStartTime() != null ? request.getStartTime() : null;
            endTime = request != null && request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now();
        }
        log.info("刷新内容请求: userId={}, startTime={}, endTime={}, fetchMode={}", id, startTime, endTime, fetchMode);
        
        // 验证用户存在
        TrackedUser user = trackedUserRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 创建任务记录
        FetchTask task = new FetchTask();
        task.setUser(user);
        task.setTaskType(FetchTask.TaskType.MANUAL);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setStatus(FetchTask.TaskStatus.PENDING);
        task = fetchTaskRepository.save(task);
        
        // 异步执行（传入已创建的任务ID 与拉取模式）
        contentFetchService.fetchUserContentAsync(id, startTime, endTime, fetchMode, task.getId());
        
        // 重新加载带 user 的任务，避免序列化时懒加载导致前端拿不到用户信息
        task = fetchTaskRepository.findByIdWithUser(task.getId()).orElse(task);
        return ApiResponse.success(task);
    }
    
    @Operation(summary = "批量刷新用户内容", description = "批量刷新多个用户的内容")
    @PostMapping("/users/batch-fetch")
    public ApiResponse<List<FetchTask>> batchFetchUsers(@Valid @RequestBody BatchFetchRequest request) {
        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = LocalDateTime.now();
        
        // TODO: 为每个用户创建任务并异步执行
        return ApiResponse.success(List.of());
    }
    
    @Operation(summary = "获取用户刷新历史", description = "获取指定用户的刷新历史记录")
    @GetMapping("/users/{id}/fetch-history")
    public ApiResponse<Page<FetchTask>> getFetchHistory(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(fetchTaskRepository.findByUserIdOrderByCreatedAtDesc(id, pageable));
    }
}

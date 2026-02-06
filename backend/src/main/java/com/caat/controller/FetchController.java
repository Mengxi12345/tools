package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.dto.BatchFetchRequest;
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
    
    @Operation(summary = "刷新用户内容", description = "手动触发拉取指定用户的内容，完整拉取逐页直至无数据")
    @PostMapping("/users/{id}/fetch")
    public ApiResponse<FetchTask> fetchUserContent(@PathVariable UUID id) {
        TrackedUser user = trackedUserRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        FetchTask task = new FetchTask();
        task.setUser(user);
        task.setTaskType(FetchTask.TaskType.MANUAL);
        task = fetchTaskRepository.save(task);
        contentFetchService.fetchUserContentAsync(id, null, null, task.getId());
        return ApiResponse.success(fetchTaskRepository.findByIdWithUser(task.getId()).orElse(task));
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

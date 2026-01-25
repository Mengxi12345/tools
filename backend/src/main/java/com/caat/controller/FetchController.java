package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.dto.BatchFetchRequest;
import com.caat.dto.FetchRequest;
import com.caat.entity.FetchTask;
import com.caat.repository.FetchTaskRepository;
import com.caat.service.ContentFetchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "内容拉取", description = "手动刷新和任务管理接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FetchController {
    
    private final ContentFetchService contentFetchService;
    private final FetchTaskRepository fetchTaskRepository;
    
    @Operation(summary = "刷新用户内容", description = "手动触发拉取指定用户的内容")
    @PostMapping("/users/{id}/fetch")
    public ApiResponse<FetchTask> fetchUserContent(
        @PathVariable UUID id,
        @RequestBody(required = false) FetchRequest request
    ) {
        LocalDateTime startTime = request != null && request.getStartTime() != null 
            ? request.getStartTime() 
            : null; // 如果为 null，Service 层会使用最后拉取时间
        
        LocalDateTime endTime = LocalDateTime.now();
        
        // 创建任务记录
        FetchTask task = new FetchTask();
        task.setTaskType(FetchTask.TaskType.MANUAL);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setStatus(FetchTask.TaskStatus.PENDING);
        task = fetchTaskRepository.save(task);
        
        // 异步执行
        contentFetchService.fetchUserContentAsync(id, startTime, endTime);
        
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

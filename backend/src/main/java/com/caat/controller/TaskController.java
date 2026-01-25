package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.FetchTask;
import com.caat.repository.FetchTaskRepository;
import com.caat.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 任务管理控制器
 */
@Tag(name = "任务管理", description = "定时任务和刷新任务管理接口")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {
    
    private final ScheduleService scheduleService;
    private final FetchTaskRepository fetchTaskRepository;
    
    @Operation(summary = "获取定时任务状态", description = "获取全局定时任务启用状态")
    @GetMapping("/schedule/status")
    public ApiResponse<Boolean> getScheduleStatus() {
        return ApiResponse.success(scheduleService.getGlobalScheduleStatus());
    }
    
    @Operation(summary = "启用全局定时任务", description = "启用所有定时拉取任务")
    @PutMapping("/schedule/enable")
    public ApiResponse<Void> enableGlobalSchedule() {
        scheduleService.setGlobalScheduleStatus(true);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "禁用全局定时任务", description = "禁用所有定时拉取任务")
    @PutMapping("/schedule/disable")
    public ApiResponse<Void> disableGlobalSchedule() {
        scheduleService.setGlobalScheduleStatus(false);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "启用用户定时任务", description = "启用指定用户的定时拉取任务")
    @PutMapping("/schedule/users/{id}/enable")
    public ApiResponse<Void> enableUserSchedule(@PathVariable UUID id) {
        scheduleService.setUserScheduleStatus(id, true);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "禁用用户定时任务", description = "禁用指定用户的定时拉取任务")
    @PutMapping("/schedule/users/{id}/disable")
    public ApiResponse<Void> disableUserSchedule(@PathVariable UUID id) {
        scheduleService.setUserScheduleStatus(id, false);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "获取刷新任务队列", description = "获取所有进行中的刷新任务")
    @GetMapping("/fetch/queue")
    public ApiResponse<List<FetchTask>> getFetchQueue() {
        return ApiResponse.success(fetchTaskRepository.findByStatus(FetchTask.TaskStatus.RUNNING));
    }
    
    @Operation(summary = "获取刷新任务详情", description = "根据任务 ID 获取刷新任务详情")
    @GetMapping("/fetch/{taskId}")
    public ApiResponse<FetchTask> getFetchTask(@PathVariable UUID taskId) {
        return ApiResponse.success(fetchTaskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("任务不存在")));
    }
    
    @Operation(summary = "取消刷新任务", description = "取消进行中的刷新任务")
    @DeleteMapping("/fetch/{taskId}")
    public ApiResponse<Void> cancelFetchTask(@PathVariable UUID taskId) {
        FetchTask task = fetchTaskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("任务不存在"));
        
        if (task.getStatus() == FetchTask.TaskStatus.RUNNING) {
            task.setStatus(FetchTask.TaskStatus.CANCELLED);
            fetchTaskRepository.save(task);
        }
        
        return ApiResponse.success(null);
    }
}

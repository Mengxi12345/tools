package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.FetchTask;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.FetchTaskRepository;
import com.caat.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public ApiResponse<Map<String, Boolean>> getScheduleStatus() {
        return ApiResponse.success(Map.of("isEnabled", scheduleService.getGlobalScheduleStatus()));
    }

    @Operation(summary = "获取定时任务执行状态详情", description = "包含是否启用、执行间隔、最近一次定时执行时间与状态、当前进行中任务数")
    @GetMapping("/schedule/status/detail")
    public ApiResponse<Map<String, Object>> getScheduleStatusDetail() {
        return ApiResponse.success(scheduleService.getScheduleStatusDetail());
    }

    @Operation(summary = "获取全局附件下载开关状态", description = "返回是否启用内容附件（图片、文件）下载到本地")
    @GetMapping("/schedule/content-asset-download")
    public ApiResponse<Map<String, Boolean>> getContentAssetDownloadStatus() {
        return ApiResponse.success(Map.of("enabled", scheduleService.isContentAssetDownloadEnabled()));
    }

    @Operation(summary = "启用全局附件下载", description = "启用内容附件（图片、文件）下载到本地并使用本地地址")
    @PutMapping("/schedule/content-asset-download/enable")
    public ApiResponse<Void> enableContentAssetDownload() {
        scheduleService.setContentAssetDownloadEnabled(true);
        return ApiResponse.success(null);
    }

    @Operation(summary = "禁用全局附件下载", description = "关闭内容附件下载，直接使用平台原始地址")
    @PutMapping("/schedule/content-asset-download/disable")
    public ApiResponse<Void> disableContentAssetDownload() {
        scheduleService.setContentAssetDownloadEnabled(false);
        return ApiResponse.success(null);
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

    @Operation(summary = "获取各用户定时任务当前状态", description = "返回 userId -> isEnabled，无配置的用户视为已启用")
    @GetMapping("/schedule/users/status")
    public ApiResponse<Map<String, Boolean>> getUsersScheduleStatus() {
        Map<UUID, Boolean> status = scheduleService.getUsersScheduleStatus();
        Map<String, Boolean> result = status.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
        return ApiResponse.success(result);
    }
    
    @Operation(summary = "获取刷新任务队列", description = "获取所有进行中的刷新任务")
    @GetMapping("/fetch/queue")
    public ApiResponse<List<FetchTask>> getFetchQueue() {
        return ApiResponse.success(fetchTaskRepository.findByStatus(FetchTask.TaskStatus.RUNNING));
    }

    /** 刷新任务记录（须在 /fetch/{taskId} 之前声明，避免 history 被当作 taskId）。taskType=MANUAL 仅手动，SCHEDULED 仅定时，不传则全部 */
    @Operation(summary = "刷新任务记录", description = "分页获取刷新任务，按创建时间倒序。taskType=MANUAL 仅手动，SCHEDULED 仅定时")
    @GetMapping("/fetch/history")
    public ApiResponse<Page<FetchTask>> getFetchHistory(
        @RequestParam(required = false) String page,
        @RequestParam(required = false) String size,
        @RequestParam(required = false) String taskType
    ) {
        int p = parseNonNegativeInt(page, 0);
        int s = parsePositiveInt(size, 20);
        Pageable pageable = PageRequest.of(p, s, Sort.by("createdAt").descending());
        if ("MANUAL".equalsIgnoreCase(taskType)) {
            return ApiResponse.success(fetchTaskRepository.findByTaskTypeOrderByCreatedAtDesc(FetchTask.TaskType.MANUAL, pageable));
        }
        if ("SCHEDULED".equalsIgnoreCase(taskType)) {
            return ApiResponse.success(fetchTaskRepository.findByTaskTypeOrderByCreatedAtDesc(FetchTask.TaskType.SCHEDULED, pageable));
        }
        return ApiResponse.success(fetchTaskRepository.findAllByOrderByCreatedAtDesc(pageable));
    }
    
    @Operation(summary = "获取刷新任务详情", description = "根据任务 ID 获取刷新任务详情")
    @GetMapping("/fetch/{taskId}")
    public ApiResponse<FetchTask> getFetchTask(@PathVariable UUID taskId) {
        return ApiResponse.success(fetchTaskRepository.findByIdWithUser(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FETCH_TASK_NOT_FOUND)));
    }
    
    @Operation(summary = "取消刷新任务", description = "取消进行中的刷新任务")
    @PutMapping("/fetch/{taskId}/cancel")
    public ApiResponse<Void> cancelFetchTask(@PathVariable UUID taskId) {
        FetchTask task = fetchTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FETCH_TASK_NOT_FOUND));
        if (task.getStatus() == FetchTask.TaskStatus.RUNNING) {
            task.setStatus(FetchTask.TaskStatus.CANCELLED);
            fetchTaskRepository.save(task);
        }
        return ApiResponse.success(null);
    }

    /** POST 固定路径，避免 Spring Boot 3.2 下 DELETE 未匹配到控制器导致 NoResourceFoundException */
    @Operation(summary = "清除单条任务记录", description = "从数据库中删除指定任务记录（POST 固定路径，避免路由未匹配）")
    @PostMapping("/fetch/delete-record")
    public ApiResponse<Void> deleteFetchTaskRecordPost(@RequestBody Map<String, String> body) {
        String taskIdStr = body != null ? body.get("taskId") : null;
        if (taskIdStr == null || taskIdStr.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskId 不能为空");
        }
        UUID taskId;
        try {
            taskId = UUID.fromString(taskIdStr.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskId 格式无效");
        }
        if (!fetchTaskRepository.existsById(taskId)) {
            throw new BusinessException(ErrorCode.FETCH_TASK_NOT_FOUND);
        }
        fetchTaskRepository.deleteById(taskId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "批量清除任务记录", description = "从数据库中删除多条任务记录（POST 固定路径）")
    @PostMapping("/fetch/delete-records")
    public ApiResponse<Integer> deleteFetchTaskRecordsPost(@RequestBody List<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return ApiResponse.success(0);
        }
        List<UUID> ids = taskIds.stream().distinct().collect(Collectors.toList());
        ids.forEach(fetchTaskRepository::deleteById);
        return ApiResponse.success(ids.size());
    }

    @Operation(summary = "按类型一键全清任务记录", description = "删除指定类型的所有任务记录。body.taskType 为 MANUAL 或 SCHEDULED")
    @PostMapping("/fetch/delete-all-by-type")
    @Transactional
    public ApiResponse<Integer> deleteAllFetchTaskRecordsByType(@RequestBody Map<String, String> body) {
        String typeStr = body != null ? body.get("taskType") : null;
        if (typeStr == null || typeStr.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskType 不能为空，应为 MANUAL 或 SCHEDULED");
        }
        FetchTask.TaskType type;
        try {
            type = FetchTask.TaskType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskType 应为 MANUAL 或 SCHEDULED");
        }
        int deleted = fetchTaskRepository.deleteByTaskType(type);
        return ApiResponse.success(deleted);
    }

    @Operation(summary = "取消刷新任务（兼容旧接口）", description = "取消进行中的刷新任务")
    @DeleteMapping("/fetch/{taskId}")
    public ApiResponse<Void> cancelFetchTaskLegacy(@PathVariable UUID taskId) {
        return cancelFetchTask(taskId);
    }
    
    @Operation(summary = "获取定时任务执行历史", description = "分页获取定时任务执行记录")
    @GetMapping("/schedule/history")
    public ApiResponse<Page<FetchTask>> getScheduleHistory(
        @RequestParam(required = false) String page,
        @RequestParam(required = false) String size
    ) {
        int p = parseNonNegativeInt(page, 0);
        int s = parsePositiveInt(size, 20);
        Pageable pageable = PageRequest.of(p, s, Sort.by("createdAt").descending());
        Page<FetchTask> tasks = fetchTaskRepository.findByTaskTypeOrderByCreatedAtDesc(
            FetchTask.TaskType.SCHEDULED, pageable);
        return ApiResponse.success(tasks);
    }

    private static int parseNonNegativeInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        String v = value.trim();
        if ("NaN".equalsIgnoreCase(v) || "Infinity".equalsIgnoreCase(v)) return defaultValue;
        try {
            int n = Integer.parseInt(v);
            return n >= 0 ? n : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        String v = value.trim();
        if ("NaN".equalsIgnoreCase(v) || "Infinity".equalsIgnoreCase(v)) return defaultValue;
        try {
            int n = Integer.parseInt(v);
            return n > 0 ? n : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

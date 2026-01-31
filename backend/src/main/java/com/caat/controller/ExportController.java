package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.ExportTask;
import com.caat.repository.ExportTaskRepository;
import com.caat.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 数据导出控制器
 */
@Tag(name = "数据导出", description = "数据导出接口")
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {
    
    private final ExportService exportService;
    private final ExportTaskRepository exportTaskRepository;
    
    @Operation(summary = "导出JSON格式", description = "导出内容为JSON格式")
    @GetMapping("/json")
    public ResponseEntity<byte[]> exportJson(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime
    ) throws Exception {
        byte[] data = exportService.exportToJson(userId, startTime, endTime);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }
    
    @Operation(summary = "导出Markdown格式", description = "导出内容为Markdown格式")
    @GetMapping("/markdown")
    public ResponseEntity<byte[]> exportMarkdown(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime
    ) throws Exception {
        byte[] data = exportService.exportToMarkdown(userId, startTime, endTime);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.md")
                .contentType(MediaType.TEXT_PLAIN)
                .body(data);
    }
    
    @Operation(summary = "导出CSV格式", description = "导出内容为CSV格式")
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime
    ) throws Exception {
        byte[] data = exportService.exportToCsv(userId, startTime, endTime);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }
    
    @Operation(summary = "导出HTML格式", description = "导出内容为HTML格式")
    @GetMapping("/html")
    public ResponseEntity<byte[]> exportHtml(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime
    ) throws Exception {
        byte[] data = exportService.exportToHtml(userId, startTime, endTime);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=export.html")
                .contentType(MediaType.TEXT_HTML)
                .body(data);
    }
    
    @Operation(summary = "创建异步导出任务", description = "创建异步导出任务，支持JSON、Markdown、CSV、HTML格式")
    @PostMapping("/async")
    public ApiResponse<ExportTask> createAsyncExport(
            @RequestParam(required = false) UUID userId,
            @RequestParam ExportTask.ExportFormat format,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime
    ) {
        ExportTask task = exportService.createExportTask(userId, format, startTime, endTime);
        return ApiResponse.success(task);
    }
    
    @Operation(summary = "获取导出任务列表", description = "分页获取导出任务列表")
    @GetMapping("/tasks")
    public ApiResponse<Page<ExportTask>> getExportTasks(
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ExportTask> tasks;
        if (userId != null) {
            tasks = exportService.getExportTasksByUser(userId, pageable);
        } else {
            tasks = exportService.getAllExportTasks(pageable);
        }
        return ApiResponse.success(tasks);
    }
    
    @Operation(summary = "获取导出任务详情", description = "根据任务ID获取导出任务详情")
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<ExportTask> getExportTask(@PathVariable UUID taskId) {
        return ApiResponse.success(exportService.getExportTask(taskId));
    }
    
    @Operation(summary = "下载导出文件", description = "下载已完成的导出文件")
    @GetMapping("/tasks/{taskId}/download")
    public ResponseEntity<byte[]> downloadExportFile(@PathVariable UUID taskId) throws Exception {
        ExportTask task = exportService.getExportTask(taskId);
        if (task.getStatus() != ExportTask.TaskStatus.COMPLETED) {
            throw new RuntimeException("导出任务未完成");
        }
        
        byte[] data = exportService.getExportFile(taskId);
        String fileName = "export." + getFileExtension(task.getExportFormat());
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(getMediaType(task.getExportFormat()))
                .body(data);
    }
    
    private String getFileExtension(ExportTask.ExportFormat format) {
        return switch (format) {
            case JSON -> "json";
            case MARKDOWN -> "md";
            case CSV -> "csv";
            case HTML -> "html";
        };
    }
    
    private MediaType getMediaType(ExportTask.ExportFormat format) {
        return switch (format) {
            case JSON -> MediaType.APPLICATION_JSON;
            case MARKDOWN -> MediaType.TEXT_PLAIN;
            case CSV -> MediaType.parseMediaType("text/csv");
            case HTML -> MediaType.TEXT_HTML;
        };
    }
}

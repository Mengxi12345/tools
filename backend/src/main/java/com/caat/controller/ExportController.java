package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.ExportTask;
import com.caat.repository.ExportTaskRepository;
import com.caat.service.ExportService;
import com.caat.service.PdfWordExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 数据导出控制器
 */
@Tag(name = "数据导出", description = "数据导出接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {
    
    private final ExportService exportService;
    private final ExportTaskRepository exportTaskRepository;
    private final PdfWordExportService pdfWordExportService;
    
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

    @Operation(summary = "导出PDF格式", description = "导出选中用户全部文章为PDF，按年/月/日组织，支持日期排序")
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "DESC") String sortOrder
    ) throws Exception {
        PdfWordExportService.SortOrder order = "ASC".equalsIgnoreCase(sortOrder)
                ? PdfWordExportService.SortOrder.ASC
                : PdfWordExportService.SortOrder.DESC;
        PdfWordExportService.ExportResult result = pdfWordExportService.exportToPdf(userId, order, null);
        if (result.data().length == 0) {
            return ResponseEntity.noContent().build();
        }
        String filename = result.suggestedFileName() != null ? result.suggestedFileName() : "export.pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionWithFilename(filename))
                .contentType(MediaType.APPLICATION_PDF)
                .body(result.data());
    }

    @Operation(summary = "导出Word格式", description = "导出选中用户全部文章为Word，按年/月/日组织，支持日期排序")
    @GetMapping("/word")
    public ResponseEntity<byte[]> exportWord(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "DESC") String sortOrder
    ) throws Exception {
        PdfWordExportService.SortOrder order = "ASC".equalsIgnoreCase(sortOrder)
                ? PdfWordExportService.SortOrder.ASC
                : PdfWordExportService.SortOrder.DESC;
        PdfWordExportService.ExportResult result = pdfWordExportService.exportToWord(userId, order, null);
        if (result.data().length == 0) {
            return ResponseEntity.noContent().build();
        }
        String filename = result.suggestedFileName() != null ? result.suggestedFileName() : "export.docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionWithFilename(filename))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(result.data());
    }
    
    @Operation(summary = "创建异步导出任务", description = "创建异步导出任务，支持JSON、Markdown、CSV、HTML、PDF、Word格式；PDF/Word需传userId和sortOrder")
    @PostMapping("/async")
    public ApiResponse<ExportTask> createAsyncExport(
            @RequestParam(required = false) UUID userId,
            @RequestParam ExportTask.ExportFormat format,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "DESC") String sortOrder
    ) {
        log.info("收到创建异步导出任务请求: userId={}, format={}, sortOrder={}", userId, format, sortOrder);
        try {
            ExportTask task = exportService.createExportTask(userId, format, startTime, endTime, sortOrder);
            log.info("创建异步导出任务成功: taskId={}, status={}", task.getId(), task.getStatus());
            return ApiResponse.success(task);
        } catch (Exception e) {
            log.error("创建异步导出任务失败: userId={}, format={}", userId, format, e);
            throw e;
        }
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
        String fileName = task.getFilePath() != null ? Paths.get(task.getFilePath()).getFileName().toString()
                : ("export." + getFileExtension(task.getExportFormat()));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionWithFilename(fileName))
                .contentType(getMediaType(task.getExportFormat()))
                .body(data);
    }
    
    /**
     * 构建支持 Unicode 文件名的 Content-Disposition 头。
     * 使用 RFC 5987 filename* 参数，避免 Tomcat 对非 ASCII 字符编码时报错。
     */
    private String contentDispositionWithFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            filename = "export";
        }
        boolean hasNonAscii = filename.chars().anyMatch(c -> c > 127);
        if (!hasNonAscii) {
            return "attachment; filename=\"" + filename.replace("\"", "\\\"") + "\"";
        }
        String asciiFallback = filename.replaceAll("[^\\x00-\\x7F]", "_");
        if (asciiFallback.isBlank()) asciiFallback = "export";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + asciiFallback.replace("\"", "\\\"") + "\"; filename*=UTF-8''" + encoded;
    }

    private String getFileExtension(ExportTask.ExportFormat format) {
        return switch (format) {
            case JSON -> "json";
            case MARKDOWN -> "md";
            case CSV -> "csv";
            case HTML -> "html";
            case PDF -> "pdf";
            case WORD -> "docx";
        };
    }
    
    private MediaType getMediaType(ExportTask.ExportFormat format) {
        return switch (format) {
            case JSON -> MediaType.APPLICATION_JSON;
            case MARKDOWN -> MediaType.TEXT_PLAIN;
            case CSV -> MediaType.parseMediaType("text/csv");
            case HTML -> MediaType.TEXT_HTML;
            case PDF -> MediaType.APPLICATION_PDF;
            case WORD -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        };
    }
}

package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.ExportTask;
import com.caat.repository.ContentRepository;
import com.caat.repository.ExportTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 数据导出服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {
    
    private final ContentRepository contentRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final ObjectMapper objectMapper;
    
    private static final String EXPORT_DIR = "exports";
    
    /**
     * 创建异步导出任务
     */
    @Transactional
    public ExportTask createExportTask(UUID userId, ExportTask.ExportFormat format, 
                                      LocalDateTime startTime, LocalDateTime endTime) {
        ExportTask task = new ExportTask();
        task.setUserId(userId);
        task.setExportFormat(format);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setStatus(ExportTask.TaskStatus.PENDING);
        task = exportTaskRepository.save(task);
        
        // 异步执行导出任务
        executeExportTaskAsync(task.getId());
        
        return task;
    }
    
    /**
     * 异步执行导出任务
     */
    @Async
    public void executeExportTaskAsync(UUID taskId) {
        ExportTask task = exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("导出任务不存在"));
        
        try {
            task.setStatus(ExportTask.TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            exportTaskRepository.save(task);
            
            // 执行导出
            byte[] data = exportToFormat(task.getUserId(), task.getExportFormat(), 
                    task.getStartTime(), task.getEndTime());
            
            // 保存文件
            String fileName = generateFileName(task.getExportFormat());
            Path exportPath = Paths.get(EXPORT_DIR);
            if (!Files.exists(exportPath)) {
                Files.createDirectories(exportPath);
            }
            
            Path filePath = exportPath.resolve(fileName);
            Files.write(filePath, data);
            
            // 更新任务状态
            task.setStatus(ExportTask.TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setFilePath(filePath.toString());
            task.setFileSize((long) data.length);
            task.setProgress(100);
            exportTaskRepository.save(task);
            
            log.info("导出任务完成: taskId={}, format={}, fileSize={}", 
                    taskId, task.getExportFormat(), data.length);
        } catch (Exception e) {
            log.error("导出任务失败: taskId={}", taskId, e);
            task.setStatus(ExportTask.TaskStatus.FAILED);
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorMessage(e.getMessage());
            exportTaskRepository.save(task);
        }
    }
    
    /**
     * 获取导出任务
     */
    public ExportTask getExportTask(UUID taskId) {
        return exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("导出任务不存在"));
    }
    
    /**
     * 获取用户的导出任务列表（分页）
     */
    public Page<ExportTask> getExportTasksByUser(UUID userId, Pageable pageable) {
        return exportTaskRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    
    /**
     * 获取所有导出任务列表（分页）
     */
    public Page<ExportTask> getAllExportTasks(Pageable pageable) {
        return exportTaskRepository.findAll(pageable);
    }
    
    /**
     * 获取导出文件内容
     */
    public byte[] getExportFile(UUID taskId) throws IOException {
        ExportTask task = getExportTask(taskId);
        if (task.getStatus() != ExportTask.TaskStatus.COMPLETED || task.getFilePath() == null) {
            throw new RuntimeException("导出任务未完成或文件不存在");
        }
        return Files.readAllBytes(Paths.get(task.getFilePath()));
    }
    
    /**
     * 生成文件名
     */
    private String generateFileName(ExportTask.ExportFormat format) {
        String extension = switch (format) {
            case JSON -> "json";
            case MARKDOWN -> "md";
            case CSV -> "csv";
            case HTML -> "html";
        };
        return "export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) 
                + "." + extension;
    }
    
    /**
     * 导出为JSON格式（同步）
     */
    public byte[] exportToJson(UUID userId, LocalDateTime startTime, LocalDateTime endTime) throws IOException {
        return exportToFormat(userId, ExportTask.ExportFormat.JSON, startTime, endTime);
    }
    
    /**
     * 根据格式导出
     */
    private byte[] exportToFormat(UUID userId, ExportTask.ExportFormat format, 
                                   LocalDateTime startTime, LocalDateTime endTime) throws IOException {
        return switch (format) {
            case JSON -> exportToJson(userId, startTime, endTime);
            case MARKDOWN -> exportToMarkdown(userId, startTime, endTime);
            case CSV -> exportToCsv(userId, startTime, endTime);
            case HTML -> exportToHtml(userId, startTime, endTime);
        };
    }
    
    /**
     * 导出为JSON格式（内部方法）
     */
    private byte[] exportToJsonInternal(UUID userId, LocalDateTime startTime, LocalDateTime endTime) throws IOException {
        List<Content> contents = getContents(userId, startTime, endTime);
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(contents);
    }
    
    /**
     * 导出为Markdown格式
     */
    public byte[] exportToMarkdown(UUID userId, LocalDateTime startTime, LocalDateTime endTime) throws IOException {
        List<Content> contents = getContents(userId, startTime, endTime);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);
        
        writer.println("# 内容导出");
        writer.println();
        writer.println("导出时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        writer.println("内容数量: " + contents.size());
        writer.println();
        
        for (Content content : contents) {
            writer.println("## " + (content.getTitle() != null ? content.getTitle() : "无标题"));
            writer.println();
            writer.println("- **平台**: " + (content.getPlatform() != null ? content.getPlatform().getName() : "未知"));
            writer.println("- **作者**: " + (content.getUser() != null ? content.getUser().getUsername() : "未知"));
            writer.println("- **发布时间**: " + content.getPublishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.println("- **链接**: " + content.getUrl());
            if (content.getBody() != null && !content.getBody().isEmpty()) {
                writer.println();
                writer.println(content.getBody());
            }
            writer.println();
            writer.println("---");
            writer.println();
        }
        
        writer.flush();
        return baos.toByteArray();
    }
    
    /**
     * 导出为CSV格式
     */
    public byte[] exportToCsv(UUID userId, LocalDateTime startTime, LocalDateTime endTime) throws IOException {
        List<Content> contents = getContents(userId, startTime, endTime);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);
        
        // CSV头部
        writer.println("标题,平台,作者,发布时间,链接,已读,收藏");
        
        // CSV数据
        for (Content content : contents) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,%s%n",
                    escapeCsv(content.getTitle()),
                    escapeCsv(content.getPlatform() != null ? content.getPlatform().getName() : ""),
                    escapeCsv(content.getUser() != null ? content.getUser().getUsername() : ""),
                    content.getPublishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    escapeCsv(content.getUrl()),
                    content.getIsRead(),
                    content.getIsFavorite()
            );
        }
        
        writer.flush();
        return baos.toByteArray();
    }
    
    /**
     * 导出为HTML格式
     */
    public byte[] exportToHtml(UUID userId, LocalDateTime startTime, LocalDateTime endTime) throws IOException {
        List<Content> contents = getContents(userId, startTime, endTime);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);
        
        writer.println("<!DOCTYPE html>");
        writer.println("<html><head><meta charset='UTF-8'><title>内容导出</title>");
        writer.println("<style>body{font-family:Arial,sans-serif;margin:20px;}h1{color:#333;}h2{border-bottom:2px solid #eee;padding-bottom:10px;margin-top:30px;}.meta{color:#666;font-size:14px;}.content{margin:20px 0;}</style>");
        writer.println("</head><body>");
        writer.println("<h1>内容导出</h1>");
        writer.println("<p>导出时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "</p>");
        writer.println("<p>内容数量: " + contents.size() + "</p>");
        
        for (Content content : contents) {
            writer.println("<div>");
            writer.println("<h2>" + escapeHtml(content.getTitle() != null ? content.getTitle() : "无标题") + "</h2>");
            writer.println("<div class='meta'>");
            writer.println("平台: " + escapeHtml(content.getPlatform() != null ? content.getPlatform().getName() : "未知") + " | ");
            writer.println("作者: " + escapeHtml(content.getUser() != null ? content.getUser().getUsername() : "未知") + " | ");
            writer.println("发布时间: " + content.getPublishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " | ");
            writer.println("<a href='" + escapeHtml(content.getUrl()) + "'>查看原文</a>");
            writer.println("</div>");
            if (content.getBody() != null && !content.getBody().isEmpty()) {
                writer.println("<div class='content'>" + escapeHtml(content.getBody()) + "</div>");
            }
            writer.println("</div>");
            writer.println("<hr>");
        }
        
        writer.println("</body></html>");
        writer.flush();
        return baos.toByteArray();
    }
    
    /**
     * 获取内容列表
     */
    private List<Content> getContents(UUID userId, LocalDateTime startTime, LocalDateTime endTime) {
        Pageable pageable = PageRequest.of(0, 10000); // 最大10000条
        Page<Content> page;
        
        if (userId != null) {
            page = contentRepository.findByUserId(userId, pageable);
        } else {
            page = contentRepository.findAll(pageable);
        }
        
        List<Content> contents = page.getContent();
        
        // 时间过滤
        if (startTime != null || endTime != null) {
            contents = contents.stream()
                    .filter(content -> {
                        if (startTime != null && content.getPublishedAt().isBefore(startTime)) {
                            return false;
                        }
                        if (endTime != null && content.getPublishedAt().isAfter(endTime)) {
                            return false;
                        }
                        return true;
                    })
                    .toList();
        }
        
        return contents;
    }
    
    /**
     * CSV转义
     */
    private String escapeCsv(String str) {
        if (str == null) return "";
        return str.replace("\"", "\"\"");
    }
    
    /**
     * HTML转义
     */
    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

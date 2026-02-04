package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.ExportTask;
import com.caat.repository.ContentRepository;
import com.caat.repository.ExportTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final PdfWordExportService pdfWordExportService;
    private final ExportTaskProgressUpdater progressUpdater;
    private final ObjectMapper objectMapper;
    
    /** 用于异步方法调用，避免同一类内调用时 @Async 不生效（self-injection） */
    private ExportService self;
    
    @Lazy
    @Autowired
    public void setSelf(ExportService self) {
        this.self = self;
    }
    
    private static final String EXPORT_DIR = "exports";
    
    /**
     * 创建异步导出任务
     * @param sortOrder PDF/Word 日期排序：ASC | DESC，默认 DESC
     */
    @Transactional
    public ExportTask createExportTask(UUID userId, ExportTask.ExportFormat format, 
                                      LocalDateTime startTime, LocalDateTime endTime, String sortOrder) {
        log.info("开始创建导出任务: userId={}, format={}, sortOrder={}", userId, format, sortOrder);
        ExportTask task = new ExportTask();
        task.setUserId(userId);
        task.setExportFormat(format);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setSortOrder(sortOrder != null ? sortOrder : "DESC");
        task.setStatus(ExportTask.TaskStatus.PENDING);
        task.setProgress(1); // 设置初始进度，表示任务已创建
        try {
            task.setLogMessages(objectMapper.writeValueAsString(List.of("任务已创建，准备开始...")));
        } catch (Exception e) {
            log.warn("设置初始日志失败", e);
        }
        // 立即保存任务，确保任务ID生成
        task = exportTaskRepository.saveAndFlush(task);
        log.info("导出任务已创建: taskId={}, status={}, progress={}", task.getId(), task.getStatus(), task.getProgress());
        
        // 在事务提交后异步执行导出任务，确保任务已保存到数据库
        final UUID taskId = task.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("事务已提交，启动异步导出任务: taskId={}", taskId);
                        try {
                            // 使用 self 调用，确保 @Async 生效
                            self.executeExportTaskAsync(taskId);
                            log.info("已启动异步导出任务: taskId={}", taskId);
                        } catch (Exception e) {
                            log.error("启动异步导出任务失败: taskId={}", taskId, e);
                        }
                    }
                }
            );
        } else {
            // 如果没有活动事务，直接启动异步任务
            log.info("无活动事务，直接启动异步导出任务: taskId={}", taskId);
            try {
                // 使用 self 调用，确保 @Async 生效
                self.executeExportTaskAsync(taskId);
                log.info("已启动异步导出任务: taskId={}", taskId);
            } catch (Exception e) {
                log.error("启动异步导出任务失败: taskId={}", taskId, e);
            }
        }
        
        // 立即返回任务，不等待异步任务执行
        log.info("返回任务给前端: taskId={}", taskId);
        return task;
    }
    
    /**
     * 异步执行导出任务
     */
    @Async
    @Transactional
    public void executeExportTaskAsync(UUID taskId) {
        log.info("异步导出任务开始执行: taskId={}", taskId);
        ExportTask task = exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("导出任务不存在: " + taskId));
        
        try {
            log.info("更新任务状态为RUNNING: taskId={}", taskId);
            task.setStatus(ExportTask.TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(2); // 设置进度，表示任务已开始执行
            try {
                List<String> initialLogs = new ArrayList<>();
                if (task.getLogMessages() != null) {
                    try {
                        initialLogs = objectMapper.readValue(task.getLogMessages(), new TypeReference<List<String>>() {});
                    } catch (Exception ignored) {}
                }
                initialLogs.add("任务已开始执行...");
                task.setLogMessages(objectMapper.writeValueAsString(initialLogs));
            } catch (Exception e) {
                log.warn("设置任务开始日志失败: taskId={}", taskId, e);
            }
            // 使用 saveAndFlush 确保立即提交到数据库，让前端能立即看到状态更新
            exportTaskRepository.saveAndFlush(task);
            log.info("任务状态已更新为RUNNING: taskId={}, progress={}", taskId, task.getProgress());
            
            byte[] data;
            String suggestedFileName = null;
            if (task.getExportFormat() == ExportTask.ExportFormat.PDF || task.getExportFormat() == ExportTask.ExportFormat.WORD) {
                // PDF/Word 使用 PdfWordExportService，带进度回调
                if (task.getUserId() == null) {
                    throw new IllegalArgumentException("PDF/Word 导出必须指定用户");
                }
                PdfWordExportService.SortOrder order = "ASC".equalsIgnoreCase(task.getSortOrder())
                        ? PdfWordExportService.SortOrder.ASC
                        : PdfWordExportService.SortOrder.DESC;
                final List<String> logList = new ArrayList<>();
                try {
                    if (task.getLogMessages() != null) {
                        logList.addAll(objectMapper.readValue(task.getLogMessages(), new TypeReference<List<String>>() {}));
                    }
                } catch (Exception ignored) {}
                final UUID finalTaskId = taskId;
                PdfWordExportService.ProgressCallback callback = (progress, message) -> {
                    logList.add(message);
                    log.debug("导出进度更新: taskId={}, progress={}%, message={}", finalTaskId, progress, message);
                    try {
                        String logJson = objectMapper.writeValueAsString(logList);
                        progressUpdater.updateProgress(finalTaskId, progress, logJson);
                    } catch (IllegalStateException e) {
                        log.debug("无法更新任务进度：应用上下文已关闭，taskId={}", finalTaskId);
                    } catch (org.springframework.beans.factory.BeanCreationException e) {
                        log.debug("无法更新任务进度：应用上下文正在重新加载，taskId={}", finalTaskId);
                    } catch (org.springframework.context.ApplicationContextException e) {
                        log.debug("无法更新任务进度：应用上下文异常，taskId={}", finalTaskId);
                    } catch (Exception ex) {
                        String exMsg = ex.getMessage();
                        String exClassName = ex.getClass().getName();
                        if (exMsg != null && (
                            exMsg.contains("has been closed") ||
                            exMsg.contains("ApplicationContext") ||
                            exMsg.contains("BeanFactory") ||
                            exMsg.contains("FlywayProperties") ||
                            exMsg.contains("Could not bind properties") ||
                            exClassName.contains("ConfigurationProperties") ||
                            exClassName.contains("BeanCreation") ||
                            exClassName.contains("ApplicationContext")
                        )) {
                            log.debug("无法更新任务进度：应用上下文问题，taskId={}, error={}", finalTaskId, exMsg);
                        } else {
                            log.warn("更新导出任务日志失败，taskId={}, error={}", finalTaskId, ex.getMessage());
                        }
                    }
                };
                PdfWordExportService.ExportResult result;
                if (task.getExportFormat() == ExportTask.ExportFormat.PDF) {
                    result = pdfWordExportService.exportToPdf(task.getUserId(), order, callback);
                } else {
                    result = pdfWordExportService.exportToWord(task.getUserId(), order, callback);
                }
                data = result.data();
                suggestedFileName = result.suggestedFileName();
            } else {
                // JSON/Markdown/CSV/HTML 使用原有逻辑
                task.setProgress(10);
                task.setLogMessages(objectMapper.writeValueAsString(List.of("开始加载数据...")));
                exportTaskRepository.save(task);
                data = exportToFormat(task.getUserId(), task.getExportFormat(), 
                        task.getStartTime(), task.getEndTime());
                task.setProgress(80);
                task.setLogMessages(objectMapper.writeValueAsString(List.of("开始加载数据...", "数据加载完成，正在生成文件...")));
                exportTaskRepository.save(task);
            }
            
            if (data.length == 0 && (task.getExportFormat() == ExportTask.ExportFormat.PDF || task.getExportFormat() == ExportTask.ExportFormat.WORD)) {
                task.setStatus(ExportTask.TaskStatus.COMPLETED);
                task.setCompletedAt(LocalDateTime.now());
                task.setProgress(100);
                exportTaskRepository.save(task);
                log.info("导出任务完成(无内容): taskId={}, format={}", taskId, task.getExportFormat());
                return;
            }
            
            // 保存文件（PDF/Word 使用 username-平台-时间 命名）
            String fileName = (suggestedFileName != null && !suggestedFileName.isEmpty())
                    ? suggestedFileName : generateFileName(task.getExportFormat());
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
            List<String> finalLogs = new ArrayList<>();
            try {
                if (task.getLogMessages() != null) {
                    finalLogs = objectMapper.readValue(task.getLogMessages(), new TypeReference<List<String>>() {});
                }
            } catch (Exception ignored) {}
            finalLogs.add("导出完成");
            task.setLogMessages(objectMapper.writeValueAsString(finalLogs));
            exportTaskRepository.save(task);
            
            log.info("导出任务完成: taskId={}, format={}, fileSize={}", 
                    taskId, task.getExportFormat(), data.length);
        } catch (Exception e) {
            log.error("导出任务失败: taskId={}", taskId, e);
            task.setStatus(ExportTask.TaskStatus.FAILED);
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorMessage(e.getMessage());
            try {
                List<String> errLogs = new ArrayList<>();
                if (task.getLogMessages() != null) {
                    errLogs = objectMapper.readValue(task.getLogMessages(), new TypeReference<List<String>>() {});
                }
                errLogs.add("导出失败: " + e.getMessage());
                task.setLogMessages(objectMapper.writeValueAsString(errLogs));
            } catch (Exception ignored) {}
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
            case PDF -> "pdf";
            case WORD -> "docx";
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
            case JSON -> exportToJsonInternal(userId, startTime, endTime);
            case MARKDOWN -> exportToMarkdown(userId, startTime, endTime);
            case CSV -> exportToCsv(userId, startTime, endTime);
            case HTML -> exportToHtml(userId, startTime, endTime);
            case PDF, WORD -> throw new UnsupportedOperationException("PDF/Word 请使用异步导出");
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

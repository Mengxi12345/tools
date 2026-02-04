package com.caat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 导出任务表
 */
@Entity
@Table(name = "export_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId; // 可选，如果为 null 则导出所有用户的内容

    @Enumerated(EnumType.STRING)
    @Column(name = "export_format", nullable = false)
    private ExportFormat exportFormat;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(nullable = false)
    private Integer progress = 0; // 进度百分比 0-100

    @Column(name = "file_path")
    private String filePath; // 导出文件路径

    @Column(name = "file_size")
    private Long fileSize; // 文件大小（字节）

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "sort_order", length = 10)
    private String sortOrder; // PDF/Word 日期排序：ASC | DESC

    @Column(name = "log_messages", columnDefinition = "text")
    private String logMessages; // 进度日志，JSON 数组字符串，如 ["开始加载...","已加载 100 篇"]

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ExportFormat {
        JSON, MARKDOWN, CSV, HTML, PDF, WORD
    }

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }
}

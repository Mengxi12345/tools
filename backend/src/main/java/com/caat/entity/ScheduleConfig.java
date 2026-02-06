package com.caat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 定时任务配置表
 */
@Entity
@Table(name = "schedule_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ConfigType type;

    @Column(name = "user_id")
    private UUID userId; // 如果 type 为 USER，则此字段有值

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "cron_expression")
    private String cronExpression; // Cron 表达式，可选

    /**
     * 全局附件下载开关：
     * - 仅当 type = GLOBAL 时有效；
     * - null 表示使用默认值 true（保持兼容老数据）。
     */
    @Column(name = "enable_attachment_download")
    private Boolean enableAttachmentDownload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ConfigType {
        GLOBAL, USER
    }
}

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

    @Column(name = "is_global_enabled", nullable = false)
    private Boolean isGlobalEnabled = true;

    @Column(name = "default_cron", nullable = false)
    private String defaultCron = "0 0 */6 * * ?"; // 默认每6小时执行一次

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

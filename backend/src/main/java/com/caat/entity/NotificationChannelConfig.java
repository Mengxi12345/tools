package com.caat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 通知通道配置（可存储并可选共享）
 * 用于保存 QQ 群/飞书等通道的配置，在创建通知规则时可选择已保存的配置复用。
 */
@Entity
@Table(name = "notification_channel_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType; // QQ_GROUP, FEISHU

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config;

    @Column(name = "created_by")
    private UUID createdBy; // 创建人，可选；暂无登录用户时可空

    @Column(name = "is_shared", nullable = false)
    private Boolean isShared = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

package com.caat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通知记录表
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "rule_id")
    private UUID ruleId; // 关联的通知规则 ID
    
    @Column(name = "content_id")
    private UUID contentId; // 关联的内容 ID
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "text")
    private String message;
    
    @Column(name = "notification_type", nullable = false)
    private String notificationType; // EMAIL, WEBHOOK, DESKTOP
    
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

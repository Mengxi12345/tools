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
 * 归档规则表
 */
@Entity
@Table(name = "archive_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "rule_type", length = 64)
    private String ruleType; // TIME_BASED, KEYWORD, AUTHOR, PLATFORM, TAG
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> conditions; // 归档条件
    
    @Column(name = "target_category")
    private String targetCategory; // 归档目标分类
    
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;
    
    @Column(name = "auto_execute", nullable = false)
    private Boolean autoExecute = false; // 是否自动执行
    
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

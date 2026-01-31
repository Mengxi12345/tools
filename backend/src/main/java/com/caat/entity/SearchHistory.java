package com.caat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 搜索历史表
 */
@Entity
@Table(name = "search_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String query; // 搜索关键词
    
    @Column(name = "search_type")
    private String searchType; // ELASTICSEARCH, DATABASE
    
    @Column(name = "result_count")
    private Integer resultCount; // 搜索结果数量
    
    @Column(name = "user_id")
    private UUID userId; // 用户ID（可选，如果支持多用户）
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

package com.caat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 内容表
 */
@Entity
@Table(name = "contents", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_published_at", columnList = "published_at"),
    @Index(name = "idx_hash", columnList = "hash")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Platform platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private TrackedUser user;

    @Column(name = "content_id", nullable = false)
    private String contentId; // 平台内容 ID

    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType = ContentType.TEXT;

    @ElementCollection
    @CollectionTable(name = "content_media_urls", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "media_url")
    private List<String> mediaUrls = new ArrayList<>();

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata; // JSON 格式的元数据（点赞数、转发数等）

    @Column(nullable = false, unique = true)
    private String hash; // 内容哈希，用于去重

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "is_favorite", nullable = false)
    private Boolean isFavorite = false;

    @Column(columnDefinition = "text")
    private String notes; // 个人备注

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ContentType {
        TEXT, IMAGE, VIDEO, LINK
    }
}

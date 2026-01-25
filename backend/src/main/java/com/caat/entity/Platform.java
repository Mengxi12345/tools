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
 * 平台表
 */
@Entity
@Table(name = "platforms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Platform {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(name = "api_base_url")
    private String apiBaseUrl;

    @Column(name = "auth_type")
    private String authType; // api_key, oauth

    @Column(columnDefinition = "CLOB")  // H2 不支持 jsonb，使用 CLOB
    private String config; // JSON 格式的平台配置

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformStatus status = PlatformStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum PlatformStatus {
        ACTIVE, INACTIVE
    }
}

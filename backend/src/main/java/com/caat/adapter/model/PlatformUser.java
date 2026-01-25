package com.caat.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台用户信息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUser {
    private String userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String profileUrl;
    private Integer followersCount;
    private Integer followingCount;
    private LocalDateTime createdAt;
    private List<String> tags;
}

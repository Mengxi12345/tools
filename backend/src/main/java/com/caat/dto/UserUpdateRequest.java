package com.caat.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 更新用户请求
 */
@Data
public class UserUpdateRequest {
    private String displayName;
    private String avatarUrl;
    private String selfIntroduction;
    private UUID groupId;
    private List<String> tags;
    private Integer priority;
    private Boolean isActive;
}

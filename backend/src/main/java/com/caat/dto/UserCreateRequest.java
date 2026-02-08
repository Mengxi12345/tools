package com.caat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 创建用户请求
 */
@Data
public class UserCreateRequest {
    @NotNull(message = "平台 ID 不能为空")
    private UUID platformId;
    
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "用户 ID 不能为空")
    private String userId;
    
    private String displayName;
    private String avatarUrl;
}

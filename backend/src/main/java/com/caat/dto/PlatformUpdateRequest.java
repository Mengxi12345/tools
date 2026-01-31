package com.caat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新平台请求
 */
@Data
public class PlatformUpdateRequest {
    /** 平台名称（自定义显示名） */
    private String name;

    @NotBlank(message = "平台类型不能为空")
    private String type;
    
    private String apiBaseUrl;
    
    private String authType;
    
    /** 平台头像/Logo 图片 URL */
    private String avatarUrl;
    
    private String config; // JSON 字符串
    
    private String status; // ACTIVE, INACTIVE
}

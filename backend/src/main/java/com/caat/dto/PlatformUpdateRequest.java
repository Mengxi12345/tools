package com.caat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新平台请求
 */
@Data
public class PlatformUpdateRequest {
    @NotBlank(message = "平台类型不能为空")
    private String type;
    
    private String apiBaseUrl;
    
    private String authType;
    
    private String config; // JSON 字符串
    
    private String status; // ACTIVE, INACTIVE
}

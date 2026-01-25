package com.caat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建平台请求
 */
@Data
public class PlatformCreateRequest {
    @NotBlank(message = "平台名称不能为空")
    private String name;
    
    @NotBlank(message = "平台类型不能为空")
    private String type;
    
    private String apiBaseUrl;
    
    private String authType;
    
    private String config; // JSON 字符串
}

package com.caat.dto;

import lombok.Data;


/**
 * 更新用户请求
 */
@Data
public class UserUpdateRequest {
    private String displayName;
    private String avatarUrl;
    private String selfIntroduction;
    private Boolean isActive;
}

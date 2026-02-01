package com.caat.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 创建/更新通知通道配置请求
 */
@Data
public class NotificationChannelConfigCreateRequest {

    private String name;
    private String channelType; // QQ_GROUP, FEISHU
    private Map<String, Object> config;
    private Boolean isShared = false;
    private UUID createdBy; // 可选，暂无登录时传 null
}

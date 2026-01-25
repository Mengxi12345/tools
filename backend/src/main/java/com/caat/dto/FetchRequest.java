package com.caat.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 刷新请求
 */
@Data
public class FetchRequest {
    private LocalDateTime startTime; // 可选，不传则使用最后拉取时间
}

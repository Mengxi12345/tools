package com.caat.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 批量刷新请求
 */
@Data
public class BatchFetchRequest {
    private List<UUID> userIds;
    private LocalDateTime startTime; // 可选
}

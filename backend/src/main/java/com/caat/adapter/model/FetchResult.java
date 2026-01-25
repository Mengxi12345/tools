package com.caat.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 拉取结果模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchResult {
    private List<PlatformContent> contents;
    private boolean hasMore;
    private String nextCursor; // 用于分页的游标
    private Integer totalCount;
    private Integer fetchedCount;
}

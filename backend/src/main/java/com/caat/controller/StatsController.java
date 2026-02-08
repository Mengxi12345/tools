package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.service.ContentService;
import com.caat.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "数据统计", description = "统计与分析接口")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ContentService contentService;
    private final StatsService statsService;

    @Operation(summary = "内容统计", description = "全局或按用户的内容统计")
    @GetMapping("/content")
    public ApiResponse<ContentService.ContentStats> contentStats(
            @RequestParam(required = false) java.util.UUID userId
    ) {
        return ApiResponse.success(contentService.getContentStats(userId));
    }

    @Operation(summary = "平台分布", description = "各平台内容数量分布")
    @GetMapping("/platform-distribution")
    public ApiResponse<Map<String, Long>> platformDistribution() {
        return ApiResponse.success(statsService.getPlatformDistribution());
    }

    @Operation(summary = "用户统计", description = "追踪用户总数与启用数")
    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> userStats() {
        return ApiResponse.success(statsService.getUserStats());
    }

    @Operation(summary = "内容时间分布", description = "统计内容发布时间分布")
    @GetMapping("/content-time-distribution")
    public ApiResponse<Map<String, Object>> getContentTimeDistribution(
            @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.success(statsService.getContentTimeDistribution(days));
    }
    
    @Operation(summary = "内容类型分布", description = "统计各类型内容的分布")
    @GetMapping("/content-type-distribution")
    public ApiResponse<Map<String, Long>> getContentTypeDistribution() {
        return ApiResponse.success(statsService.getContentTypeDistribution());
    }
    
    @Operation(summary = "活跃用户排行", description = "按内容数量统计活跃用户排行")
    @GetMapping("/active-users-ranking")
    public ApiResponse<List<Map<String, Object>>> getActiveUsersRanking(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.success(statsService.getActiveUsersRanking(limit));
    }
    
    @Operation(summary = "内容增长趋势", description = "统计内容增长趋势")
    @GetMapping("/content-growth-trend")
    public ApiResponse<Map<String, Object>> getContentGrowthTrend(
            @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.success(statsService.getContentGrowthTrend(days));
    }
    
    @Operation(summary = "统计概览", description = "获取所有统计信息的概览")
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new java.util.HashMap<>();
        try {
            overview.put("platformDistribution", statsService.getPlatformDistribution());
            overview.put("userStats", statsService.getUserStats());
            overview.put("contentTypeDistribution", statsService.getContentTypeDistribution());
            overview.put("contentStats", contentService.getContentStats(null));
            return ApiResponse.success(overview);
        } catch (Exception e) {
            return ApiResponse.success(overview);
        }
    }
}

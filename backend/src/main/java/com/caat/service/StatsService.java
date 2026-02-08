package com.caat.service;

import com.caat.repository.ContentRepository;
import com.caat.repository.TrackedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据统计服务（发布频率、平台分布、类型分布等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final ContentRepository contentRepository;
    private final TrackedUserRepository trackedUserRepository;

    /**
     * 平台分布：各平台内容数量
     */
    @Cacheable(value = "stats", key = "'platform-distribution'")
    public Map<String, Long> getPlatformDistribution() {
        try {
            List<Object[]> rows = contentRepository.countByPlatform();
            Map<String, Long> map = new HashMap<>();
            for (Object[] r : rows) {
                if (r.length >= 2 && r[0] != null && r[1] != null) {
                    map.put(r[0].toString(), ((Number) r[1]).longValue());
                }
            }
            return map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 用户统计：总用户数、启用用户数
     */
    @Cacheable(value = "stats", key = "'user-stats'")
    public Map<String, Object> getUserStats() {
        try {
            long total = trackedUserRepository.count();
            long active = trackedUserRepository.findByIsActiveTrue().size();
            Map<String, Object> result = new HashMap<>();
            result.put("totalUsers", total);
            result.put("activeUsers", active);
            return result;
        } catch (Exception e) {
            log.error("获取用户统计失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("totalUsers", 0L);
            result.put("activeUsers", 0L);
            return result;
        }
    }
    
    /**
     * 高级统计：内容发布时间分布（按天）
     */
    public Map<String, Object> getContentTimeDistribution(int days) {
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(days);
            List<Object[]> rows = contentRepository.findTimeDistribution(startDate, endDate);
            Map<String, Long> distribution = new LinkedHashMap<>();
            for (Object[] row : rows) {
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    distribution.put(row[0].toString(), ((Number) row[1]).longValue());
                }
            }
            return Map.of("days", days, "distribution", distribution);
        } catch (Exception e) {
            log.error("获取内容时间分布失败", e);
            return Map.of("days", days, "distribution", new HashMap<>());
        }
    }
    
    /**
     * 高级统计：内容类型分布
     */
    public Map<String, Long> getContentTypeDistribution() {
        try {
            List<Object[]> rows = contentRepository.findContentTypeDistribution();
            Map<String, Long> distribution = new HashMap<>();
            for (Object[] row : rows) {
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    distribution.put(row[0].toString(), ((Number) row[1]).longValue());
                }
            }
            return distribution;
        } catch (Exception e) {
            log.error("获取内容类型分布失败", e);
            return new HashMap<>();
        }
    }
    
    /**
     * 高级统计：活跃用户排行（按内容数量）
     */
    public List<Map<String, Object>> getActiveUsersRanking(int limit) {
        try {
            List<Object[]> rows = contentRepository.findActiveUsersRanking(limit);
            return rows.stream()
                    .map(row -> {
                        Map<String, Object> userStats = new HashMap<>();
                        if (row.length >= 3) {
                            userStats.put("userId", row[0] != null ? row[0].toString() : "未知");
                            userStats.put("username", row[1] != null ? row[1].toString() : "未知");
                            userStats.put("contentCount", row[2] != null ? ((Number) row[2]).longValue() : 0L);
                        }
                        return userStats;
                    })
                    .filter(map -> !map.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取活跃用户排行失败", e);
            return List.of();
        }
    }
    
    /**
     * 高级统计：内容增长趋势（按天）
     */
    public Map<String, Object> getContentGrowthTrend(int days) {
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(days);
            
            List<Object[]> rows = contentRepository.findContentGrowthTrend(startDate, endDate);
            Map<String, Long> trend = new LinkedHashMap<>();
            for (Object[] row : rows) {
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    String dateKey = row[0].toString();
                    trend.put(dateKey, ((Number) row[1]).longValue());
                }
            }
            return Map.of("days", days, "trend", trend);
        } catch (Exception e) {
            log.error("获取内容增长趋势失败", e);
            return Map.of("days", days, "trend", new HashMap<>());
        }
    }
}

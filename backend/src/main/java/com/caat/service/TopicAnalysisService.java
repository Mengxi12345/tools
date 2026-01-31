package com.caat.service;

import com.caat.entity.Content;
import com.caat.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 话题分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicAnalysisService {
    
    private final ContentRepository contentRepository;
    private final KeywordExtractionService keywordExtractionService;
    
    /**
     * 识别热门话题
     */
    public List<Map<String, Object>> identifyHotTopics(int limit, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        // 获取指定时间范围内的内容
        List<Content> contents = contentRepository.findAll().stream()
                .filter(c -> c.getPublishedAt() != null && c.getPublishedAt().isAfter(startDate))
                .collect(Collectors.toList());
        
        // 提取所有关键词
        Map<String, Integer> keywordCounts = new HashMap<>();
        for (Content content : contents) {
            String title = content.getTitle() != null ? content.getTitle() : "";
            String body = content.getBody() != null ? content.getBody() : "";
            List<String> keywords = keywordExtractionService.extractKeywordsFromContent(title, body, 10);
            for (String keyword : keywords) {
                keywordCounts.put(keyword, keywordCounts.getOrDefault(keyword, 0) + 1);
            }
        }
        
        // 按频率排序
        List<Map<String, Object>> hotTopics = keywordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> topic = new HashMap<>();
                    topic.put("keyword", entry.getKey());
                    topic.put("count", entry.getValue());
                    topic.put("trend", calculateTrend(entry.getKey(), contents));
                    return topic;
                })
                .collect(Collectors.toList());
        
        return hotTopics;
    }
    
    /**
     * 追踪话题演化
     */
    public Map<String, Object> trackTopicEvolution(String keyword, int days) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);
        
        // 按天分组统计
        Map<String, Integer> dailyCounts = new HashMap<>();
        
        List<Content> contents = contentRepository.findAll().stream()
                .filter(c -> {
                    if (c.getPublishedAt() == null) return false;
                    if (c.getPublishedAt().isBefore(startDate) || c.getPublishedAt().isAfter(endDate)) {
                        return false;
                    }
                    String text = (c.getTitle() + " " + (c.getBody() != null ? c.getBody() : "")).toLowerCase();
                    return text.contains(keyword.toLowerCase());
                })
                .collect(Collectors.toList());
        
        for (Content content : contents) {
            String dateKey = content.getPublishedAt().toLocalDate().toString();
            dailyCounts.put(dateKey, dailyCounts.getOrDefault(dateKey, 0) + 1);
        }
        
        Map<String, Object> evolution = new HashMap<>();
        evolution.put("keyword", keyword);
        evolution.put("period", days + " days");
        evolution.put("dailyCounts", dailyCounts);
        evolution.put("totalCount", contents.size());
        evolution.put("trend", calculateTrend(keyword, contents));
        
        return evolution;
    }
    
    /**
     * 计算趋势（上升/下降/稳定）
     */
    private String calculateTrend(String keyword, List<Content> contents) {
        if (contents.size() < 2) {
            return "stable";
        }
        
        // 按时间排序
        List<Content> sortedContents = contents.stream()
                .sorted(Comparator.comparing(Content::getPublishedAt))
                .collect(Collectors.toList());
        
        // 计算前半部分和后半部分的频率
        int mid = sortedContents.size() / 2;
        long firstHalf = sortedContents.subList(0, mid).stream()
                .filter(c -> {
                    String text = (c.getTitle() + " " + (c.getBody() != null ? c.getBody() : "")).toLowerCase();
                    return text.contains(keyword.toLowerCase());
                })
                .count();
        
        long secondHalf = sortedContents.subList(mid, sortedContents.size()).stream()
                .filter(c -> {
                    String text = (c.getTitle() + " " + (c.getBody() != null ? c.getBody() : "")).toLowerCase();
                    return text.contains(keyword.toLowerCase());
                })
                .count();
        
        if (secondHalf > firstHalf * 1.2) {
            return "rising";
        } else if (secondHalf < firstHalf * 0.8) {
            return "declining";
        } else {
            return "stable";
        }
    }
}

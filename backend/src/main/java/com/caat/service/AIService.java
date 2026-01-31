package com.caat.service;

import com.caat.entity.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 服务（内容摘要、情感分析等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {
    
    private final RestTemplate restTemplate;
    
    @Value("${ai.service.enabled:false}")
    private boolean aiServiceEnabled;
    
    @Value("${ai.service.url:}")
    private String aiServiceUrl;
    
    @Value("${ai.service.api-key:}")
    private String apiKey;
    
    /**
     * 生成内容摘要
     */
    public String generateSummary(Content content) {
        if (!aiServiceEnabled) {
            return generateSimpleSummary(content);
        }
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("text", content.getTitle() + "\n\n" + (content.getBody() != null ? content.getBody() : ""));
            request.put("maxLength", 200);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("Content-Type", "application/json");
            
            // 调用 AI 服务 API（示例，需要根据实际 API 调整）
            // Map response = restTemplate.postForObject(aiServiceUrl + "/summarize", request, Map.class, headers);
            // return (String) response.get("summary");
            
            // 暂时返回简单摘要
            return generateSimpleSummary(content);
        } catch (Exception e) {
            log.error("AI 摘要生成失败，使用简单摘要", e);
            return generateSimpleSummary(content);
        }
    }
    
    /**
     * 简单摘要生成（基于文本截取）
     */
    private String generateSimpleSummary(Content content) {
        String text = content.getBody() != null ? content.getBody() : content.getTitle();
        if (text == null || text.isEmpty()) {
            return "无摘要";
        }
        
        // 取前 200 个字符
        if (text.length() > 200) {
            return text.substring(0, 200) + "...";
        }
        return text;
    }
    
    /**
     * 情感分析
     */
    public Map<String, Object> analyzeSentiment(Content content) {
        if (!aiServiceEnabled) {
            return getSimpleSentiment(content);
        }
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("text", content.getTitle() + "\n\n" + (content.getBody() != null ? content.getBody() : ""));
            
            // 调用 AI 服务 API（示例）
            // Map response = restTemplate.postForObject(aiServiceUrl + "/sentiment", request, Map.class);
            // return response;
            
            return getSimpleSentiment(content);
        } catch (Exception e) {
            log.error("情感分析失败，使用简单分析", e);
            return getSimpleSentiment(content);
        }
    }
    
    /**
     * 简单情感分析（基于关键词）
     */
    private Map<String, Object> getSimpleSentiment(Content content) {
        Map<String, Object> result = new HashMap<>();
        String text = (content.getTitle() + " " + (content.getBody() != null ? content.getBody() : "")).toLowerCase();
        
        // 简单的关键词匹配
        int positiveCount = countKeywords(text, new String[]{"好", "棒", "优秀", "喜欢", "推荐", "great", "good", "excellent"});
        int negativeCount = countKeywords(text, new String[]{"差", "糟糕", "问题", "错误", "bad", "poor", "wrong", "error"});
        
        if (positiveCount > negativeCount) {
            result.put("sentiment", "positive");
            result.put("score", 0.6 + (positiveCount - negativeCount) * 0.1);
        } else if (negativeCount > positiveCount) {
            result.put("sentiment", "negative");
            result.put("score", 0.4 - (negativeCount - positiveCount) * 0.1);
        } else {
            result.put("sentiment", "neutral");
            result.put("score", 0.5);
        }
        
        return result;
    }
    
    private int countKeywords(String text, String[] keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 提取关键信息
     */
    public Map<String, Object> extractKeyInfo(Content content) {
        Map<String, Object> keyInfo = new HashMap<>();
        
        // 提取标题中的关键词
        if (content.getTitle() != null) {
            String[] words = content.getTitle().split("[\\s\\p{Punct}]+");
            keyInfo.put("keywords", java.util.Arrays.asList(words).subList(0, Math.min(5, words.length)));
        }
        
        // 提取发布时间
        if (content.getPublishedAt() != null) {
            keyInfo.put("publishedAt", content.getPublishedAt());
        }
        
        // 提取平台信息
        if (content.getPlatform() != null) {
            keyInfo.put("platform", content.getPlatform().getName());
        }
        
        return keyInfo;
    }
}

package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.Content;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.service.AIService;
import com.caat.service.ContentService;
import com.caat.service.RecommendationService;
import com.caat.service.TopicAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 功能控制器
 */
@Tag(name = "AI 功能", description = "AI 内容摘要、情感分析、话题分析、推荐系统")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AIController {
    
    private final AIService aiService;
    private final ContentService contentService;
    private final TopicAnalysisService topicAnalysisService;
    private final RecommendationService recommendationService;
    
    @Operation(summary = "生成内容摘要", description = "为指定内容生成 AI 摘要")
    @GetMapping("/content/{id}/summary")
    public ApiResponse<String> generateSummary(@PathVariable UUID id) {
        Content content = contentService.getContentById(id);
        String summary = aiService.generateSummary(content);
        return ApiResponse.success(summary);
    }
    
    @Operation(summary = "情感分析", description = "分析内容的情感倾向")
    @GetMapping("/content/{id}/sentiment")
    public ApiResponse<Map<String, Object>> analyzeSentiment(@PathVariable UUID id) {
        Content content = contentService.getContentById(id);
        Map<String, Object> sentiment = aiService.analyzeSentiment(content);
        return ApiResponse.success(sentiment);
    }
    
    @Operation(summary = "提取关键信息", description = "从内容中提取关键信息")
    @GetMapping("/content/{id}/key-info")
    public ApiResponse<Map<String, Object>> extractKeyInfo(@PathVariable UUID id) {
        Content content = contentService.getContentById(id);
        Map<String, Object> keyInfo = aiService.extractKeyInfo(content);
        return ApiResponse.success(keyInfo);
    }
    
    @Operation(summary = "识别热门话题", description = "识别指定时间范围内的热门话题")
    @GetMapping("/topics/hot")
    public ApiResponse<List<Map<String, Object>>> identifyHotTopics(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days
    ) {
        List<Map<String, Object>> hotTopics = topicAnalysisService.identifyHotTopics(limit, days);
        return ApiResponse.success(hotTopics);
    }
    
    @Operation(summary = "追踪话题演化", description = "追踪指定话题的时间演化趋势")
    @GetMapping("/topics/evolution")
    public ApiResponse<Map<String, Object>> trackTopicEvolution(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") int days
    ) {
        Map<String, Object> evolution = topicAnalysisService.trackTopicEvolution(keyword, days);
        return ApiResponse.success(evolution);
    }
    
    @Operation(summary = "推荐相似内容", description = "基于内容相似度推荐相关内容")
    @GetMapping("/recommendations/similar/{contentId}")
    public ApiResponse<List<Content>> recommendSimilarContent(
            @PathVariable UUID contentId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<Content> recommendations = recommendationService.recommendSimilarContent(contentId, limit);
        return ApiResponse.success(recommendations);
    }
    
    @Operation(summary = "推荐相关作者", description = "基于内容相似度推荐相关作者")
    @GetMapping("/recommendations/authors/{userId}")
    public ApiResponse<List<Map<String, Object>>> recommendRelatedAuthors(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<Map<String, Object>> authors = recommendationService.recommendRelatedAuthors(userId, limit);
        return ApiResponse.success(authors);
    }
    
    @Operation(summary = "个性化推荐", description = "基于用户历史行为进行个性化内容推荐")
    @GetMapping("/recommendations/personalized/{userId}")
    public ApiResponse<List<Content>> personalizedRecommendation(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<Content> recommendations = recommendationService.personalizedRecommendation(userId, limit);
        return ApiResponse.success(recommendations);
    }
}

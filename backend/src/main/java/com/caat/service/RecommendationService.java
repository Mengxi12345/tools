package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.Tag;
import com.caat.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    
    private final ContentRepository contentRepository;
    private final KeywordExtractionService keywordExtractionService;
    
    /**
     * 推荐相似内容
     */
    public List<Content> recommendSimilarContent(UUID contentId, int limit) {
        Content targetContent = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("内容不存在"));
        
        // 提取目标内容的关键词
        String targetTitle = targetContent.getTitle() != null ? targetContent.getTitle() : "";
        String targetBody = targetContent.getBody() != null ? targetContent.getBody() : "";
        List<String> targetKeywords = keywordExtractionService.extractKeywordsFromContent(targetTitle, targetBody, 10);
        
        // 获取所有其他内容
        List<Content> allContents = contentRepository.findAll().stream()
                .filter(c -> !c.getId().equals(contentId))
                .collect(Collectors.toList());
        
        // 计算相似度
        Map<Content, Double> similarityScores = new HashMap<>();
        for (Content content : allContents) {
            String contentTitle = content.getTitle() != null ? content.getTitle() : "";
            String contentBody = content.getBody() != null ? content.getBody() : "";
            List<String> contentKeywords = keywordExtractionService.extractKeywordsFromContent(contentTitle, contentBody, 10);
            double similarity = calculateSimilarity(targetKeywords, contentKeywords);
            similarityScores.put(content, similarity);
        }
        
        // 按相似度排序并返回前 limit 个
        return similarityScores.entrySet().stream()
                .sorted(Map.Entry.<Content, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * 推荐相关作者
     */
    public List<Map<String, Object>> recommendRelatedAuthors(UUID userId, int limit) {
        // 获取用户的内容
        List<Content> userContents = contentRepository.findByUserId(userId, PageRequest.of(0, 100))
                .getContent();
        
        // 提取用户内容的关键词
        Set<String> userKeywords = new HashSet<>();
        for (Content content : userContents) {
            String title = content.getTitle() != null ? content.getTitle() : "";
            String body = content.getBody() != null ? content.getBody() : "";
            userKeywords.addAll(keywordExtractionService.extractKeywordsFromContent(title, body, 10));
        }
        
        // 获取所有其他用户的内容
        Map<UUID, List<Content>> authorContents = new HashMap<>();
        List<Content> allContents = contentRepository.findAll();
        
        for (Content content : allContents) {
            if (!content.getUser().getId().equals(userId)) {
                authorContents.computeIfAbsent(content.getUser().getId(), k -> new ArrayList<>()).add(content);
            }
        }
        
        // 计算每个作者的相关度
        Map<UUID, Double> authorScores = new HashMap<>();
        for (Map.Entry<UUID, List<Content>> entry : authorContents.entrySet()) {
            Set<String> authorKeywords = new HashSet<>();
            for (Content content : entry.getValue()) {
                String title = content.getTitle() != null ? content.getTitle() : "";
                String body = content.getBody() != null ? content.getBody() : "";
                authorKeywords.addAll(keywordExtractionService.extractKeywordsFromContent(title, body, 10));
            }
            
            double similarity = calculateSetSimilarity(userKeywords, authorKeywords);
            authorScores.put(entry.getKey(), similarity);
        }
        
        // 返回相关度最高的作者
        return authorScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> author = new HashMap<>();
                    author.put("userId", entry.getKey());
                    author.put("similarity", entry.getValue());
                    return author;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 个性化推荐
     */
    public List<Content> personalizedRecommendation(UUID userId, int limit) {
        // 获取用户的历史内容（收藏、已读）
        List<Content> userContents = contentRepository.findByUserId(userId, PageRequest.of(0, 100))
                .getContent();
        
        // 提取用户偏好关键词
        Map<String, Integer> preferenceKeywords = new HashMap<>();
        for (Content content : userContents) {
            String title = content.getTitle() != null ? content.getTitle() : "";
            String body = content.getBody() != null ? content.getBody() : "";
            if (content.getIsFavorite() != null && content.getIsFavorite()) {
                List<String> keywords = keywordExtractionService.extractKeywordsFromContent(title, body, 10);
                for (String keyword : keywords) {
                    preferenceKeywords.put(keyword, preferenceKeywords.getOrDefault(keyword, 0) + 2);
                }
            } else if (content.getIsRead() != null && content.getIsRead()) {
                List<String> keywords = keywordExtractionService.extractKeywordsFromContent(title, body, 10);
                for (String keyword : keywords) {
                    preferenceKeywords.put(keyword, preferenceKeywords.getOrDefault(keyword, 0) + 1);
                }
            }
        }
        
        // 获取未读内容
        List<Content> unreadContents = contentRepository.findAll().stream()
                .filter(c -> !c.getUser().getId().equals(userId))
                .filter(c -> c.getIsRead() == null || !c.getIsRead())
                .collect(Collectors.toList());
        
        // 计算推荐分数
        Map<Content, Double> recommendationScores = new HashMap<>();
        for (Content content : unreadContents) {
            String title = content.getTitle() != null ? content.getTitle() : "";
            String body = content.getBody() != null ? content.getBody() : "";
            List<String> contentKeywords = keywordExtractionService.extractKeywordsFromContent(title, body, 10);
            double score = 0.0;
            for (String keyword : contentKeywords) {
                score += preferenceKeywords.getOrDefault(keyword, 0);
            }
            recommendationScores.put(content, score);
        }
        
        // 按分数排序并返回
        return recommendationScores.entrySet().stream()
                .sorted(Map.Entry.<Content, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * 计算两个关键词列表的相似度（Jaccard 相似度）
     */
    private double calculateSimilarity(List<String> list1, List<String> list2) {
        Set<String> set1 = new HashSet<>(list1);
        Set<String> set2 = new HashSet<>(list2);
        return calculateSetSimilarity(set1, set2);
    }
    
    /**
     * 计算两个集合的 Jaccard 相似度
     */
    private double calculateSetSimilarity(Set<String> set1, Set<String> set2) {
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        if (union.isEmpty()) {
            return 0.0;
        }
        
        return (double) intersection.size() / union.size();
    }
}

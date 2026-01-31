package com.caat.service;

import com.caat.entity.ArchiveRule;
import com.caat.entity.Content;
import com.caat.repository.ArchiveRuleRepository;
import com.caat.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 归档服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {
    
    private final ArchiveRuleRepository archiveRuleRepository;
    private final ContentRepository contentRepository;
    private final TagService tagService;
    
    /**
     * 执行归档规则
     */
    @Transactional
    public int executeArchiveRule(UUID ruleId) {
        ArchiveRule rule = archiveRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("归档规则不存在: " + ruleId));
        
        if (!rule.getIsEnabled()) {
            throw new IllegalStateException("归档规则未启用: " + ruleId);
        }
        
        List<Content> contents = findContentsMatchingRule(rule);
        int archivedCount = 0;
        
        for (Content content : contents) {
            try {
                archiveContent(content, rule);
                archivedCount++;
            } catch (Exception e) {
                log.error("归档内容失败: contentId={}, ruleId={}", content.getId(), ruleId, e);
            }
        }
        
        rule.setLastExecutedAt(LocalDateTime.now());
        archiveRuleRepository.save(rule);
        
        log.info("归档规则执行完成: ruleId={}, archivedCount={}", ruleId, archivedCount);
        return archivedCount;
    }
    
    /**
     * 批量归档操作
     */
    @Transactional
    public int batchArchive(List<UUID> contentIds, String category) {
        int archivedCount = 0;
        for (UUID contentId : contentIds) {
            try {
                Content content = contentRepository.findById(contentId)
                        .orElseThrow(() -> new IllegalArgumentException("内容不存在: " + contentId));
                archiveContentByCategory(content, category);
                archivedCount++;
            } catch (Exception e) {
                log.error("批量归档失败: contentId={}", contentId, e);
            }
        }
        return archivedCount;
    }
    
    /**
     * 查找匹配规则的内容
     */
    private List<Content> findContentsMatchingRule(ArchiveRule rule) {
        Map<String, Object> conditions = rule.getConditions();
        if (conditions == null) {
            return List.of();
        }
        
        String ruleType = rule.getRuleType();
        if (ruleType == null) {
            return List.of();
        }
        
        return switch (ruleType.toUpperCase()) {
            case "TIME_BASED" -> findContentsByTime(conditions);
            case "KEYWORD" -> findContentsByKeyword(conditions);
            case "AUTHOR" -> findContentsByAuthor(conditions);
            case "PLATFORM" -> findContentsByPlatform(conditions);
            case "TAG" -> findContentsByTag(conditions);
            default -> List.of();
        };
    }
    
    /**
     * 按时间查找内容
     */
    private List<Content> findContentsByTime(Map<String, Object> conditions) {
        // 例如：归档30天前的内容
        Integer days = (Integer) conditions.get("days");
        if (days == null) {
            return List.of();
        }
        LocalDateTime beforeDate = LocalDateTime.now().minusDays(days);
        return contentRepository.findAll().stream()
                .filter(c -> c.getPublishedAt().isBefore(beforeDate))
                .toList();
    }
    
    /**
     * 按关键词查找内容
     */
    private List<Content> findContentsByKeyword(Map<String, Object> conditions) {
        String keyword = (String) conditions.get("keyword");
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }
        Page<Content> page = contentRepository.searchByKeyword(keyword, Pageable.unpaged());
        return page.getContent();
    }
    
    /**
     * 按作者查找内容
     */
    private List<Content> findContentsByAuthor(Map<String, Object> conditions) {
        String authorId = (String) conditions.get("authorId");
        if (authorId == null) {
            return List.of();
        }
        UUID userId = UUID.fromString(authorId);
        Page<Content> page = contentRepository.findByUserId(userId, Pageable.unpaged());
        return page.getContent();
    }
    
    /**
     * 按平台查找内容
     */
    private List<Content> findContentsByPlatform(Map<String, Object> conditions) {
        String platformId = (String) conditions.get("platformId");
        if (platformId == null) {
            return List.of();
        }
        UUID pid = UUID.fromString(platformId);
        Page<Content> page = contentRepository.findByPlatformId(pid, Pageable.unpaged());
        return page.getContent();
    }
    
    /**
     * 按标签查找内容
     */
    private List<Content> findContentsByTag(Map<String, Object> conditions) {
        String tagId = (String) conditions.get("tagId");
        if (tagId == null) {
            return List.of();
        }
        UUID tid = UUID.fromString(tagId);
        Page<Content> page = contentRepository.findByTagId(tid, Pageable.unpaged());
        return page.getContent();
    }
    
    /**
     * 归档内容（根据规则）
     */
    private void archiveContent(Content content, ArchiveRule rule) {
        String category = rule.getTargetCategory();
        if (category != null && !category.isEmpty()) {
            archiveContentByCategory(content, category);
        }
        // 可以添加其他归档操作，如移动到归档表、添加归档标签等
    }
    
    /**
     * 按分类归档内容
     */
    private void archiveContentByCategory(Content content, String category) {
        // 为内容添加归档标签
        try {
            var tag = tagService.getTagByName("归档:" + category);
            if (!content.getTags().contains(tag)) {
                content.getTags().add(tag);
                contentRepository.save(content);
            }
        } catch (Exception e) {
            // 标签不存在，创建新标签
            try {
                var tag = tagService.createTag("归档:" + category, "#999999", "archive");
                content.getTags().add(tag);
                contentRepository.save(content);
            } catch (Exception ex) {
                log.warn("创建归档标签失败: category={}", category, ex);
            }
        }
    }
}

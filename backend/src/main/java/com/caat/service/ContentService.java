package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.PlatformContent;
import com.caat.entity.Content;
import com.caat.entity.ContentDocument;
import com.caat.entity.Platform;
import com.caat.entity.SearchHistory;
import com.caat.entity.Tag;
import com.caat.repository.ContentRepository;
import com.caat.repository.SearchHistoryRepository;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {
    
    private final ContentRepository contentRepository;
    private final KeywordExtractionService keywordExtractionService;
    private final TagService tagService;
    private final ElasticsearchService elasticsearchService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ContentAssetService contentAssetService;
    private final AdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    @Qualifier("timestoreFixExecutor")
    private final ThreadPoolTaskExecutor timestoreFixExecutor;
    private final TimeStoreFixService timeStoreFixService;
    
    /**
     * 获取内容列表（分页、过滤），一次性加载 platform、user，避免 N+1
     */
    public Page<Content> getContents(Pageable pageable) {
        try {
            return contentRepository.findAllWithPlatformAndUser(pageable);
        } catch (Exception e) {
            log.error("获取内容列表失败", e);
            return Page.empty(pageable);
        }
    }
    
    /**
     * 根据用户 ID 获取内容列表，一次性加载 platform、user
     */
    public Page<Content> getContentsByUserId(UUID userId, Pageable pageable) {
        return contentRepository.findByUserIdWithPlatformAndUser(userId, pageable);
    }
    
    /**
     * 根据平台 ID 获取内容列表，一次性加载 platform、user
     */
    public Page<Content> getContentsByPlatformId(UUID platformId, Pageable pageable) {
        return contentRepository.findByPlatformIdWithPlatformAndUser(platformId, pageable);
    }
    
    /**
     * 根据标签 ID 获取内容列表
     */
    public Page<Content> getContentsByTagId(UUID tagId, Pageable pageable) {
        try {
            return contentRepository.findByTagIdFixed(tagId, pageable);
        } catch (Exception e) {
            log.warn("使用固定查询方法失败，回退到原方法", e);
            return contentRepository.findByTagId(tagId, pageable);
        }
    }
    
    /**
     * 根据多个标签 ID 获取内容列表（包含任一标签）
     */
    public Page<Content> getContentsByTagIds(List<UUID> tagIds, Pageable pageable) {
        try {
            return contentRepository.findByTagIdsFixed(tagIds, pageable);
        } catch (Exception e) {
            log.warn("使用固定查询方法失败，回退到原方法", e);
            return contentRepository.findByTagIds(tagIds, pageable);
        }
    }
    
    /**
     * 根据内容类型获取内容列表，一次性加载 platform、user
     */
    public Page<Content> getContentsByContentType(Content.ContentType contentType, Pageable pageable) {
        return contentRepository.findByContentTypeWithPlatformAndUser(contentType, pageable);
    }

    /**
     * 按用户（及可选平台）、发布时间范围分页，用于内容管理「某月/某年文章」点击加载。
     * platformId 为 null 时使用单独查询，避免 PostgreSQL 无法推断 (:platformId IS NULL) 的参数类型。
     */
    public Page<Content> getContentsByUserAndPublishedAtBetween(UUID userId, UUID platformId,
                                                                LocalDateTime startTime, LocalDateTime endTime,
                                                                Pageable pageable) {
        if (platformId == null) {
            return contentRepository.findByUserIdAndPublishedAtBetweenWithPlatformAndUser(
                userId, startTime, endTime, pageable);
        }
        return contentRepository.findByPlatformIdAndUserIdAndPublishedAtBetween(
            platformId, userId, startTime, endTime, pageable);
    }

    /**
     * 按平台→用户→年-月聚合数量，用于内容管理树形展示（仅数量，点击某月再拉该月文章）
     * 返回：{ total, platforms: [ { platformId, platformName, total, users: [ { userId, username, total, months: [ { year, month, count } ] } ] } ] }
     */
    public Map<String, Object> getGroupedCountsByPlatformUserMonth() {
        List<Object[]> rows = contentRepository.findGroupedCountsByPlatformUserYearMonth();
        long globalTotal = 0;
        // platformId -> { platformName, total, users: Map<userId, { username, total, months }> }
        Map<String, Map<String, Object>> platformMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 7) continue;
            String platformId = row[0] != null ? row[0].toString() : "";
            String platformName = row[1] != null ? row[1].toString() : "未分类";
            String userId = row[2] != null ? row[2].toString() : "";
            String username = row[3] != null ? row[3].toString() : "-";
            Number yNum = row[4] instanceof Number ? (Number) row[4] : null;
            Number mNum = row[5] instanceof Number ? (Number) row[5] : null;
            long cnt = row[6] instanceof Number ? ((Number) row[6]).longValue() : 0;
            int year = yNum != null ? yNum.intValue() : 0;
            int month = mNum != null ? mNum.intValue() : 0;
            globalTotal += cnt;
            platformMap.putIfAbsent(platformId, new LinkedHashMap<>());
            Map<String, Object> platformNode = platformMap.get(platformId);
            platformNode.put("platformId", platformId);
            platformNode.put("platformName", platformName);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> users = (Map<String, Map<String, Object>>) platformNode.computeIfAbsent("users", k -> new LinkedHashMap<String, Map<String, Object>>());
            users.putIfAbsent(userId, new LinkedHashMap<>());
            Map<String, Object> userNode = users.get(userId);
            userNode.put("userId", userId);
            userNode.put("username", username);
            long userTotal = userNode.containsKey("total") ? ((Number) userNode.get("total")).longValue() : 0;
            userNode.put("total", userTotal + cnt);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> months = (List<Map<String, Object>>) userNode.computeIfAbsent("months", k -> new ArrayList<Map<String, Object>>());
            months.add(Map.<String, Object>of("year", year, "month", month, "count", (int) cnt));
        }
        List<Map<String, Object>> platformsList = new ArrayList<>();
        for (Map<String, Object> platformNode : platformMap.values()) {
            long platformTotal = 0;
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> usersMap = (Map<String, Map<String, Object>>) platformNode.get("users");
            List<Map<String, Object>> usersList = new ArrayList<>();
            if (usersMap != null) {
                for (Map<String, Object> userNode : usersMap.values()) {
                    platformTotal += ((Number) userNode.get("total")).longValue();
                    usersList.add(userNode);
                }
            }
            platformNode.put("total", (int) platformTotal);
            platformNode.put("users", usersList);
            platformsList.add(platformNode);
        }
        return Map.<String, Object>of("total", globalTotal, "platforms", platformsList);
    }

    /**
     * 获取分类统计（平台、作者、标签）
     */
    public Map<String, Object> getCategoryStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        
        // 平台分类统计
        List<Object[]> platformStats = contentRepository.countByPlatformGrouped();
        Map<String, Long> platformMap = new java.util.HashMap<>();
        for (Object[] row : platformStats) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                platformMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        stats.put("byPlatform", platformMap);
        
        // 作者分类统计
        List<Object[]> authorStats = contentRepository.countByAuthorGrouped();
        Map<String, Long> authorMap = new java.util.HashMap<>();
        for (Object[] row : authorStats) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                authorMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        stats.put("byAuthor", authorMap);
        
        // 标签分类统计
        List<Object[]> tagStats = contentRepository.countByTagGrouped();
        Map<String, Long> tagMap = new java.util.HashMap<>();
        for (Object[] row : tagStats) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                tagMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        stats.put("byTag", tagMap);
        
        return stats;
    }

    /**
     * 按关键词搜索内容（标题、正文）
     * 优先使用 Elasticsearch，如果失败则回退到数据库搜索
     */
    @Transactional
    public Page<Content> searchByKeyword(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            try {
                return contentRepository.findAllWithPlatformAndUser(pageable);
            } catch (Exception e) {
                log.error("获取内容列表失败", e);
                return Page.empty(pageable);
            }
        }
        
        String trimmedKeyword = keyword.trim();
        Page<Content> results;
        String searchType = "DATABASE";
        
        try {
            // 尝试使用 Elasticsearch 搜索
            Page<ContentDocument> documents = elasticsearchService.search(trimmedKeyword, pageable);
            // 将 ContentDocument 转换为 Content（需要根据 ID 查询）
            List<UUID> contentIds = documents.getContent().stream()
                    .map(doc -> UUID.fromString(doc.getId()))
                    .toList();
            // 这里简化处理，直接返回数据库搜索结果
            // 实际应该根据 Elasticsearch 返回的 ID 列表查询 Content 并保持排序
            if (contentIds.isEmpty()) {
                results = Page.empty(pageable);
            } else {
                // 回退到数据库搜索（Elasticsearch 主要用于全文搜索，这里简化实现）
                results = contentRepository.searchByKeywordWithPlatformAndUser(trimmedKeyword, pageable);
            }
            searchType = "ELASTICSEARCH";
        } catch (Exception e) {
            log.warn("Elasticsearch 搜索失败，回退到数据库搜索: {}", e.getMessage());
            // 回退到数据库搜索
            try {
                results = contentRepository.searchByKeywordWithPlatformAndUser(trimmedKeyword, pageable);
            } catch (Exception dbException) {
                log.error("数据库搜索也失败", dbException);
                return Page.empty(pageable);
            }
        }
        
        // 保存搜索历史
        try {
            SearchHistory history = new SearchHistory();
            history.setQuery(trimmedKeyword);
            history.setSearchType(searchType);
            history.setResultCount((int) results.getTotalElements());
            searchHistoryRepository.save(history);
            if (log.isDebugEnabled()) {
                log.debug("存储搜索历史: query={}, searchType={}, resultCount={}", trimmedKeyword, searchType, results.getTotalElements());
            }
        } catch (Exception e) {
            log.warn("保存搜索历史失败: query={}", trimmedKeyword, e);
        }
        return results;
    }
    
    /**
     * Elasticsearch 全文搜索（带搜索历史记录）；ES 不可用时返回空页
     */
    @Transactional
    public Page<ContentDocument> searchInElasticsearch(String query, Pageable pageable) {
        Page<ContentDocument> results;
        try {
            results = elasticsearchService.search(query, pageable);
        } catch (Exception e) {
            log.warn("Elasticsearch 不可用，返回空结果: query={}, error={}", query, e.getMessage());
            return Page.empty(pageable);
        }
        // 保存搜索历史
        try {
            SearchHistory history = new SearchHistory();
            history.setQuery(query);
            history.setSearchType("ELASTICSEARCH");
            history.setResultCount((int) results.getTotalElements());
            searchHistoryRepository.save(history);
            if (log.isDebugEnabled()) {
                log.debug("存储搜索历史: query={}, searchType=ELASTICSEARCH, resultCount={}", query, results.getTotalElements());
            }
        } catch (Exception e) {
            log.warn("保存搜索历史失败: query={}", query, e);
        }
        return results;
    }
    
    /**
     * Elasticsearch 正则表达式搜索；ES 不可用时返回空页
     */
    @Transactional
    public Page<ContentDocument> searchByRegex(String regexPattern, Pageable pageable) {
        Page<ContentDocument> results;
        try {
            results = elasticsearchService.searchByRegex(regexPattern, pageable);
        } catch (Exception e) {
            log.warn("Elasticsearch 不可用，返回空结果: pattern={}, error={}", regexPattern, e.getMessage());
            return Page.empty(pageable);
        }
        try {
            SearchHistory history = new SearchHistory();
            history.setQuery("regex:" + regexPattern);
            history.setSearchType("ELASTICSEARCH_REGEX");
            history.setResultCount((int) results.getTotalElements());
            searchHistoryRepository.save(history);
            if (log.isDebugEnabled()) {
                log.debug("存储搜索历史: query=regex:{}, searchType=ELASTICSEARCH_REGEX, resultCount={}", regexPattern, results.getTotalElements());
            }
        } catch (Exception e) {
            log.warn("保存搜索历史失败: pattern={}", regexPattern, e);
        }
        return results;
    }
    
    /**
     * Elasticsearch 高级搜索；ES 不可用时返回空页
     */
    @Transactional
    public Page<ContentDocument> advancedSearch(String query, String contentType, Pageable pageable) {
        Page<ContentDocument> results;
        try {
            results = elasticsearchService.advancedSearch(query, contentType, pageable);
        } catch (Exception e) {
            log.warn("Elasticsearch 不可用，返回空结果: query={}, error={}", query, e.getMessage());
            return Page.empty(pageable);
        }
        try {
            SearchHistory history = new SearchHistory();
            history.setQuery("advanced:" + query + (contentType != null ? " type:" + contentType : ""));
            history.setSearchType("ELASTICSEARCH_ADVANCED");
            history.setResultCount((int) results.getTotalElements());
            searchHistoryRepository.save(history);
            if (log.isDebugEnabled()) {
                log.debug("存储搜索历史: query=advanced:{}, searchType=ELASTICSEARCH_ADVANCED, resultCount={}", query, results.getTotalElements());
            }
        } catch (Exception e) {
            log.warn("保存搜索历史失败: query={}", query, e);
        }
        return results;
    }
    
    /**
     * 获取搜索历史
     */
    public Page<SearchHistory> getSearchHistory(Pageable pageable, UUID userId) {
        if (userId != null) {
            return searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return searchHistoryRepository.findByOrderByCreatedAtDesc(pageable);
    }
    
    /**
     * 获取热门搜索关键词
     */
    public List<String> getPopularSearchQueries(int limit) {
        Pageable pageable = Pageable.ofSize(limit);
        List<Object[]> results = searchHistoryRepository.findPopularQueries(pageable);
        return results.stream()
                .map(row -> row[0].toString())
                .toList();
    }
    
    /**
     * 获取最近的搜索关键词
     */
    public List<String> getRecentSearchQueries(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return searchHistoryRepository.findRecentQueries(pageable);
        } catch (Exception e) {
            log.error("获取最近搜索关键词失败", e);
            return List.of();
        }
    }
    
    /**
     * 根据 ID 获取内容详情
     */
    public Content getContentById(UUID id) {
        return contentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
    }
    
    /**
     * 获取上一篇和下一篇内容
     * @param id 当前内容ID
     * @param sameUserOnly 是否只查找同一用户的内容（true：仅同用户，false：全局查找）
     * @return Map包含 "previous" 和 "next" 两个键，值为Content或null
     */
    public Map<String, Content> getAdjacentContents(UUID id, boolean sameUserOnly) {
        Content current = getContentById(id);
        Map<String, Content> result = new HashMap<>();
        result.put("previous", null);
        result.put("next", null);
        
        Pageable pageable = PageRequest.of(0, 1);
        List<Content> previousList;
        List<Content> nextList;
        
        if (sameUserOnly) {
            // 只查找同一用户的内容
            previousList = contentRepository.findPreviousContentByUser(
                current.getUser().getId(), 
                current.getPublishedAt(), 
                pageable
            );
            nextList = contentRepository.findNextContentByUser(
                current.getUser().getId(), 
                current.getPublishedAt(), 
                pageable
            );
        } else {
            // 全局查找
            previousList = contentRepository.findPreviousContent(
                current.getPublishedAt(), 
                pageable
            );
            nextList = contentRepository.findNextContent(
                current.getPublishedAt(), 
                pageable
            );
        }
        
        if (!previousList.isEmpty()) {
            result.put("previous", previousList.get(0));
        }
        if (!nextList.isEmpty()) {
            result.put("next", nextList.get(0));
        }
        
        return result;
    }
    
    /**
     * 更新内容（标记已读、收藏等）
     */
    @Transactional
    public Content updateContent(UUID id, Content content) {
        Content existing = getContentById(id);
        if (content.getIsRead() != null) {
            existing.setIsRead(content.getIsRead());
        }
        if (content.getIsFavorite() != null) {
            existing.setIsFavorite(content.getIsFavorite());
        }
        if (content.getNotes() != null) {
            existing.setNotes(content.getNotes());
        }
        if (content.getTags() != null) {
            existing.setTags(content.getTags());
        }
        Content saved = contentRepository.save(existing);
        if (log.isDebugEnabled()) {
            String title = saved.getTitle();
            log.debug("存储内容更新: id={}, title={}, isRead={}, isFavorite={}",
                    saved.getId(), title != null && title.length() > 60 ? title.substring(0, 60) + "..." : title,
                    saved.getIsRead(), saved.getIsFavorite());
        }
        // 同步更新 Elasticsearch
        try {
            elasticsearchService.updateContent(saved);
        } catch (Exception e) {
            log.warn("更新 Elasticsearch 索引失败: {}", e.getMessage());
        }
        return saved;
    }
    
    /**
     * 为内容自动生成标签（基于关键词提取）
     */
    @Transactional
    public Content generateTagsForContent(UUID contentId, int maxTags) {
        Content content = getContentById(contentId);
        
        // 提取关键词
        List<String> keywords = keywordExtractionService.extractKeywordsFromContent(
                content.getTitle(), content.getBody(), maxTags);
        
        // 为每个关键词创建或获取标签
        List<Tag> tags = new ArrayList<>();
        for (String keyword : keywords) {
            try {
                Tag tag = tagService.getTagByName(keyword);
                tags.add(tag);
            } catch (BusinessException e) {
                // 标签不存在，创建新标签
                try {
                    Tag newTag = tagService.createTag(keyword, "#1890ff", "auto");
                    tags.add(newTag);
                    log.info("自动创建标签: {}", keyword);
                } catch (Exception ex) {
                    log.warn("创建标签失败: {}", keyword, ex);
                }
            }
        }
        
        // 合并现有标签和新标签
        if (content.getTags() == null) {
            content.setTags(new ArrayList<>());
        }
        for (Tag tag : tags) {
            if (!content.getTags().contains(tag)) {
                content.getTags().add(tag);
            }
        }
        
        return contentRepository.save(content);
    }
    
    /**
     * 为内容添加标签
     */
    @Transactional
    public Content addTagToContent(UUID contentId, UUID tagId) {
        Content content = getContentById(contentId);
        Tag tag = tagService.getTagById(tagId);
        if (content.getTags() == null) {
            content.setTags(new ArrayList<>());
        }
        if (!content.getTags().contains(tag)) {
            content.getTags().add(tag);
        }
        return contentRepository.save(content);
    }
    
    /**
     * 从内容移除标签
     */
    @Transactional
    public Content removeTagFromContent(UUID contentId, UUID tagId) {
        Content content = getContentById(contentId);
        if (content.getTags() != null) {
            content.getTags().removeIf(tag -> tag.getId().equals(tagId));
        }
        return contentRepository.save(content);
    }
    
    /**
     * 删除内容
     */
    @Transactional
    public void deleteContent(UUID id) {
        Content content = getContentById(id);
        contentRepository.delete(content);
        
        // 同步删除 Elasticsearch 索引
        try {
            elasticsearchService.deleteContent(id);
        } catch (Exception e) {
            log.warn("删除 Elasticsearch 索引失败: {}", e.getMessage());
        }
    }

    /**
     * 按作者（追踪用户）删除该用户下的全部内容
     * @param userId 追踪用户 ID（content.user.id）
     * @return 删除条数
     */
    @Transactional
    public int deleteContentsByUserId(UUID userId) {
        int deleted = contentRepository.deleteByUserId(userId);
        if (deleted > 0) {
            log.info("已按作者删除 {} 条内容: userId={}", deleted, userId);
        }
        return deleted;
    }

    /**
     * 获取内容统计（按用户或全局）
     */
    public ContentStats getContentStats(UUID userId) {
        if (userId != null) {
            Long total = contentRepository.countByUserId(userId);
            Long unread = contentRepository.countUnreadByUserId(userId);
            Long fav = contentRepository.countFavoriteByUserId(userId);
            return new ContentStats(total, unread, fav);
        }
        return getGlobalStats();
    }

    /**
     * 全局内容统计
     */
    public ContentStats getGlobalStats() {
        long total = contentRepository.count();
        long unread = contentRepository.countUnread();
        long fav = contentRepository.countFavorite();
        return new ContentStats(total, unread, fav);
    }

    public record ContentStats(Long total, Long unread, Long favorite) {}

    /**
     * 修复 TimeStore 已保存文章中的外部图片：下载到本地并更新数据库中的地址。
     *
     * @return 修复的文章数量
     */
    @Transactional
    public int fixTimestoreImages() {
        return timeStoreFixService.fixTimestoreImages();
    }

    /**
     * 修复 TimeStore 加密文章。
     *
     * @return 成功修复的文章数量
     */
    public int fixTimestoreEncryptedArticles() {
        return timeStoreFixService.fixTimestoreEncryptedArticles();
    }
}

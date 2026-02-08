package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.PlatformContent;
import com.caat.entity.Content;
import com.caat.entity.ContentDocument;
import com.caat.entity.Platform;
import com.caat.entity.SearchHistory;
import com.caat.repository.ContentRepository;
import com.caat.repository.SearchHistoryRepository;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
     * 根据内容类型获取内容列表，一次性加载 platform、user
     */
    public Page<Content> getContentsByContentType(Content.ContentType contentType, Pageable pageable) {
        return contentRepository.findByContentTypeWithPlatformAndUser(contentType, pageable);
    }

    /**
     * 获取收藏内容列表（分页），支持按平台、用户过滤
     * 分派到不同查询方法，避免 PostgreSQL 对 (:param IS NULL) 参数类型推断失败
     */
    public Page<Content> getFavoriteContents(UUID platformId, UUID userId, Pageable pageable) {
        if (platformId == null && userId == null) {
            return contentRepository.findByIsFavoriteTrueWithPlatformAndUser(pageable);
        }
        if (platformId == null) {
            return contentRepository.findByIsFavoriteTrueAndUserIdWithPlatformAndUser(userId, pageable);
        }
        if (userId == null) {
            return contentRepository.findByIsFavoriteTrueAndPlatformIdWithPlatformAndUser(platformId, pageable);
        }
        return contentRepository.findByIsFavoriteTrueAndPlatformIdAndUserIdWithPlatformAndUser(platformId, userId, pageable);
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
        
        return stats;
    }

    /**
     * 刷新单篇内容的图片：
     * - 仅处理 mediaUrls 中的图片 URL；
     * - 如果 URL 不是本地上传目录（/api/v1/uploads/...），则下载到本地并更新为本地地址；
     * - 已是本地地址的保持不变；
     * - 其他字段与附件元数据不做修改。
     */
    @Transactional
    public Content refreshContentAssets(UUID contentId) {
        Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));

        boolean changed = false;

        // 1. 刷新 mediaUrls 中的图片：远程 URL -> 本地 URL
        List<String> mediaUrls = content.getMediaUrls();
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            List<String> newMediaUrls = new ArrayList<>();
            for (String url : mediaUrls) {
                String u = url != null ? url.trim() : null;
                if (u == null || u.isEmpty()) {
                    continue;
                }
                if (isLocalUploadUrl(u)) {
                    newMediaUrls.add(u);
                    continue;
                }
                try {
                    String localUrl = contentAssetService.downloadImageAndSave(u);
                    if (localUrl != null && !localUrl.isEmpty()) {
                        newMediaUrls.add(localUrl);
                        changed = true;
                    } else {
                        newMediaUrls.add(u);
                    }
                } catch (BusinessException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("刷新内容图片到本地失败，保留原 URL: contentId={}, url={}, error={}",
                        content.getId(), u, e.getMessage());
                    newMediaUrls.add(u);
                }
            }
            content.setMediaUrls(newMediaUrls);
        }

        if (changed) {
            Content saved = contentRepository.save(content);
            return saved;
        }

        return content;
    }

    private static boolean isLocalUploadUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        return u.startsWith("/api/v1/uploads/");
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
            log.warn("Elasticsearch 搜索失败（可能是日期格式转换问题），回退到数据库搜索: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Elasticsearch 搜索异常详情", e);
            }
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
     * 按关键词搜索内容（标题、正文），支持平台和用户过滤
     * 支持组合搜索：关键字 + 平台ID + 用户ID
     */
    @Transactional
    public Page<Content> searchByKeywordWithFilters(String keyword, UUID platformId, UUID userId, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // 如果没有关键字，使用普通查询
            if (platformId != null && userId != null) {
                return contentRepository.findByPlatformIdAndUserIdAndPublishedAtBetween(
                    platformId, userId, LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.now(), pageable);
            } else if (userId != null) {
                return contentRepository.findByUserIdWithPlatformAndUser(userId, pageable);
            } else if (platformId != null) {
                return contentRepository.findByPlatformIdWithPlatformAndUser(platformId, pageable);
            } else {
                return contentRepository.findAllWithPlatformAndUser(pageable);
            }
        }
        
        String trimmedKeyword = keyword.trim();
        Page<Content> results;
        
        try {
            log.info("执行关键字搜索: keyword={}, platformId={}, userId={}, page={}, size={}", 
                trimmedKeyword, platformId, userId, pageable.getPageNumber(), pageable.getPageSize());
            
            // 根据过滤条件选择不同的查询方法
            if (platformId != null && userId != null) {
                results = contentRepository.searchByKeywordAndPlatformIdAndUserIdWithPlatformAndUser(
                    trimmedKeyword, platformId, userId, pageable);
            } else if (platformId != null) {
                results = contentRepository.searchByKeywordAndPlatformIdWithPlatformAndUser(
                    trimmedKeyword, platformId, pageable);
            } else if (userId != null) {
                results = contentRepository.searchByKeywordAndUserIdWithPlatformAndUser(
                    trimmedKeyword, userId, pageable);
            } else {
                // 只有关键字，使用原有的搜索方法
                return searchByKeyword(trimmedKeyword, pageable);
            }
            
            log.info("关键字搜索完成: keyword={}, 找到 {} 条结果", trimmedKeyword, results.getTotalElements());
            
            // 验证搜索结果：确保每条结果在标题或正文中确实包含关键字
            List<Content> filteredResults = results.getContent().stream()
                .filter(content -> {
                    String title = content.getTitle() != null ? content.getTitle().toLowerCase() : "";
                    String body = content.getBody() != null ? content.getBody().toLowerCase() : "";
                    // 去除 HTML 标签后搜索
                    String bodyText = body.replaceAll("<[^>]+>", " ");
                    String keywordLower = trimmedKeyword.toLowerCase();
                    boolean matches = title.contains(keywordLower) || bodyText.contains(keywordLower);
                    if (!matches) {
                        log.warn("搜索结果验证失败: contentId={}, title={}, keyword={}", 
                            content.getId(), content.getTitle(), trimmedKeyword);
                    }
                    return matches;
                })
                .collect(java.util.stream.Collectors.toList());
            
            // 如果过滤后有差异，记录警告
            if (filteredResults.size() != results.getContent().size()) {
                log.warn("搜索验证发现 {} 条不匹配的结果，已过滤", 
                    results.getContent().size() - filteredResults.size());
            }
            
            // 重新构建分页结果
            if (filteredResults.size() != results.getContent().size()) {
                results = new PageImpl<>(
                    filteredResults, 
                    pageable, 
                    results.getTotalElements() - (results.getContent().size() - filteredResults.size())
                );
            }
            
            // 保存搜索历史
            try {
                SearchHistory history = new SearchHistory();
                history.setQuery(trimmedKeyword);
                history.setSearchType("DATABASE");
                history.setResultCount((int) results.getTotalElements());
                searchHistoryRepository.save(history);
                if (log.isDebugEnabled()) {
                    log.debug("存储搜索历史: query={}, platformId={}, userId={}, resultCount={}", 
                        trimmedKeyword, platformId, userId, results.getTotalElements());
                }
            } catch (Exception e) {
                log.warn("保存搜索历史失败: query={}", trimmedKeyword, e);
            }
            
            return results;
        } catch (Exception e) {
            log.error("组合搜索失败: keyword={}, platformId={}, userId={}", trimmedKeyword, platformId, userId, e);
            return Page.empty(pageable);
        }
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
     * 删除内容，包括文字、图片、附件等该文章下的所有关联资源。
     * 会删除 mediaUrls、metadata.downloaded_file_urls、body 中 img 标签引用的本地文件。
     */
    @Transactional
    public void deleteContent(UUID id) {
        Content content = getContentById(id);
        deleteLocalFilesForContent(content);
        contentRepository.delete(content);
        try {
            elasticsearchService.deleteContent(id);
        } catch (Exception e) {
            log.warn("删除 Elasticsearch 索引失败: {}", e.getMessage());
        }
    }

    /**
     * 删除内容关联的本地文件（图片、附件）。
     * 从 mediaUrls、metadata.downloaded_file_urls、body 中 img 标签提取本地路径并删除。
     */
    private void deleteLocalFilesForContent(Content content) {
        Set<String> localUrlsToDelete = new HashSet<>();

        if (content.getMediaUrls() != null) {
            for (String url : content.getMediaUrls()) {
                if (url != null && url.contains("/api/v1/uploads/")) {
                    localUrlsToDelete.add(url.trim());
                }
            }
        }

        if (content.getMetadata() != null && !content.getMetadata().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = objectMapper.readValue(content.getMetadata(), Map.class);
                Object list = meta.get("downloaded_file_urls");
                if (list instanceof List<?> urls) {
                    for (Object item : urls) {
                        if (item instanceof Map<?, ?> m) {
                            Object localUrl = m.get("local_url");
                            if (localUrl instanceof String s && s.contains("/api/v1/uploads/")) {
                                localUrlsToDelete.add(s.trim());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 metadata 获取 downloaded_file_urls 失败: id={}", content.getId(), e);
            }
        }

        if (content.getBody() != null && content.getBody().contains("/api/v1/uploads/")) {
            Matcher m = Pattern.compile("src\\s*=\\s*[\"']([^\"']*?/api/v1/uploads/[^\"']+)[\"']")
                .matcher(content.getBody());
            while (m.find()) {
                localUrlsToDelete.add(m.group(1).trim());
            }
        }

        for (String url : localUrlsToDelete) {
            try {
                contentAssetService.deleteLocalFileByUrl(url);
            } catch (Exception e) {
                log.warn("删除内容关联文件失败（继续）: url={}, error={}", url, e.getMessage());
            }
        }
    }

    /**
     * 按作者（追踪用户）删除该用户下的全部内容，包括文字、图片、附件等。
     * @param userId 追踪用户 ID（content.user.id）
     * @return 删除条数
     */
    @Transactional
    public int deleteContentsByUserId(UUID userId) {
        List<Content> contents = contentRepository.findAllByUserId(userId);
        for (Content c : contents) {
            deleteLocalFilesForContent(c);
            try {
                elasticsearchService.deleteContent(c.getId());
            } catch (Exception e) {
                log.warn("删除 Elasticsearch 索引失败: id={}", c.getId(), e);
            }
        }
        int deleted = contentRepository.deleteByUserId(userId);
        if (deleted > 0) {
            log.info("已按作者删除 {} 条内容（含图片、附件）: userId={}", deleted, userId);
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

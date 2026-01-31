package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.Content;
import com.caat.entity.ContentDocument;
import com.caat.entity.SearchHistory;
import com.caat.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 内容管理控制器
 */
@Tag(name = "内容管理", description = "内容查询和管理接口")
@RestController
@RequestMapping("/api/v1/contents")
@RequiredArgsConstructor
public class ContentController {
    
    private final ContentService contentService;
    
    @Operation(summary = "获取内容列表", description = "分页获取内容列表，支持过滤与关键词搜索，支持按平台、作者、时间范围、标签、类型分类")
    @GetMapping
    public ApiResponse<Page<Content>> getContents(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "publishedAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDir,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) UUID platformId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
        @RequestParam(required = false) UUID tagId,
        @RequestParam(required = false) List<UUID> tagIds,
        @RequestParam(required = false) Content.ContentType contentType,
        @RequestParam(required = false) String keyword
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Content> contents;
        if (keyword != null && !keyword.trim().isEmpty()) {
            contents = contentService.searchByKeyword(keyword, pageable);
        } else if (tagIds != null && !tagIds.isEmpty()) {
            contents = contentService.getContentsByTagIds(tagIds, pageable);
        } else if (tagId != null) {
            contents = contentService.getContentsByTagId(tagId, pageable);
        } else if (contentType != null) {
            contents = contentService.getContentsByContentType(contentType, pageable);
        } else if (userId != null && startTime != null && endTime != null) {
            contents = contentService.getContentsByUserAndPublishedAtBetween(userId, platformId, startTime, endTime, pageable);
        } else if (userId != null) {
            contents = contentService.getContentsByUserId(userId, pageable);
        } else if (platformId != null) {
            contents = contentService.getContentsByPlatformId(platformId, pageable);
        } else {
            contents = contentService.getContents(pageable);
        }

        return ApiResponse.success(contents);
    }

    @Operation(summary = "按平台→用户→月聚合数量", description = "用于内容管理树形展示，仅返回数量；点击某月再调列表接口拉取该月文章")
    @GetMapping("/grouped-counts")
    public ApiResponse<Map<String, Object>> getGroupedCounts() {
        return ApiResponse.success(contentService.getGroupedCountsByPlatformUserMonth());
    }
    
    @Operation(summary = "获取内容详情", description = "根据 ID 获取内容详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Content> getContent(@PathVariable UUID id) {
        return ApiResponse.success(contentService.getContentById(id));
    }
    
    @Operation(summary = "更新内容", description = "更新内容状态（已读、收藏、备注等）")
    @PutMapping("/{id}")
    public ApiResponse<Content> updateContent(
        @PathVariable UUID id,
        @RequestBody Content content
    ) {
        return ApiResponse.success(contentService.updateContent(id, content));
    }
    
    @Operation(summary = "按作者删除内容", description = "删除指定作者（追踪用户）下的全部内容")
    @DeleteMapping("/by-author/{userId}")
    public ApiResponse<Integer> deleteContentsByAuthor(@PathVariable UUID userId) {
        int deleted = contentService.deleteContentsByUserId(userId);
        return ApiResponse.success(deleted);
    }

    @Operation(summary = "删除内容", description = "删除指定内容")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteContent(@PathVariable UUID id) {
        contentService.deleteContent(id);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "获取内容统计", description = "获取内容统计信息（支持按用户或全局）")
    @GetMapping("/stats")
    public ApiResponse<ContentService.ContentStats> getContentStats(
        @RequestParam(required = false) UUID userId
    ) {
        return ApiResponse.success(contentService.getContentStats(userId));
    }
    
    @Operation(summary = "为内容自动生成标签", description = "基于关键词提取为内容自动生成标签")
    @PostMapping("/{id}/generate-tags")
    public ApiResponse<Content> generateTags(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "5") int maxTags
    ) {
        return ApiResponse.success(contentService.generateTagsForContent(id, maxTags));
    }
    
    @Operation(summary = "Elasticsearch 全文搜索", description = "使用 Elasticsearch 进行全文搜索")
    @GetMapping("/search")
    public ApiResponse<Page<ContentDocument>> searchInElasticsearch(
        @RequestParam String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(contentService.searchInElasticsearch(query, pageable));
    }
    
    @Operation(summary = "正则表达式搜索", description = "使用正则表达式进行高级搜索")
    @GetMapping("/search/regex")
    public ApiResponse<Page<ContentDocument>> searchByRegex(
        @RequestParam String pattern,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(contentService.searchByRegex(pattern, pageable));
    }
    
    @Operation(summary = "高级搜索", description = "支持多个条件组合的高级搜索")
    @GetMapping("/search/advanced")
    public ApiResponse<Page<ContentDocument>> advancedSearch(
        @RequestParam String query,
        @RequestParam(required = false) String contentType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(contentService.advancedSearch(query, contentType, pageable));
    }
    
    @Operation(summary = "获取分类统计", description = "获取按平台、作者、标签分类的统计信息")
    @GetMapping("/categories/stats")
    public ApiResponse<Map<String, Object>> getCategoryStatistics() {
        return ApiResponse.success(contentService.getCategoryStatistics());
    }
    
    @Operation(summary = "获取搜索历史", description = "获取搜索历史记录")
    @GetMapping("/search/history")
    public ApiResponse<Page<SearchHistory>> getSearchHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(contentService.getSearchHistory(pageable, userId));
    }
    
    @Operation(summary = "获取热门搜索", description = "获取热门搜索关键词")
    @GetMapping("/search/popular")
    public ApiResponse<List<String>> getPopularSearchQueries(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.success(contentService.getPopularSearchQueries(limit));
    }
    
    @Operation(summary = "获取最近搜索", description = "获取最近的搜索关键词")
    @GetMapping("/search/recent")
    public ApiResponse<List<String>> getRecentSearchQueries(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.success(contentService.getRecentSearchQueries(limit));
    }
}

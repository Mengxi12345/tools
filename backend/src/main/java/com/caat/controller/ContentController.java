package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.Content;
import com.caat.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

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
    
    @Operation(summary = "获取内容列表", description = "分页获取内容列表，支持过滤")
    @GetMapping
    public ApiResponse<Page<Content>> getContents(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "publishedAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDir,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) UUID platformId
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Content> contents;
        if (userId != null) {
            contents = contentService.getContentsByUserId(userId, pageable);
        } else if (platformId != null) {
            contents = contentService.getContentsByPlatformId(platformId, pageable);
        } else {
            contents = contentService.getContents(pageable);
        }
        
        return ApiResponse.success(contents);
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
    
    @Operation(summary = "删除内容", description = "删除指定内容")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteContent(@PathVariable UUID id) {
        contentService.deleteContent(id);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "获取内容统计", description = "获取内容统计信息")
    @GetMapping("/stats")
    public ApiResponse<ContentService.ContentStats> getContentStats(
        @RequestParam(required = false) UUID userId
    ) {
        if (userId != null) {
            return ApiResponse.success(contentService.getContentStats(userId));
        }
        // TODO: 全局统计
        return ApiResponse.success(new ContentService.ContentStats(0L, 0L, 0L));
    }
}

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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 批量操作控制器
 */
@Tag(name = "批量操作", description = "批量查询和操作接口")
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {
    
    private final ContentService contentService;
    
    @Operation(summary = "批量获取内容", description = "根据ID列表批量获取内容")
    @PostMapping("/contents")
    public ApiResponse<Map<String, List<Content>>> getContentsByIds(@RequestBody List<UUID> ids) {
        List<Content> contents = ids.stream()
                .map(id -> {
                    try {
                        return contentService.getContentById(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(content -> content != null)
                .toList();
        return ApiResponse.success(Map.of("contents", contents));
    }
    
    @Operation(summary = "批量更新内容状态", description = "批量标记内容为已读/未读、收藏/取消收藏")
    @PutMapping("/contents/status")
    public ApiResponse<Map<String, Integer>> batchUpdateContentStatus(
            @RequestBody List<UUID> ids,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Boolean isFavorite
    ) {
        int updatedCount = 0;
        for (UUID id : ids) {
            try {
                Content content = contentService.getContentById(id);
                if (isRead != null) {
                    content.setIsRead(isRead);
                }
                if (isFavorite != null) {
                    content.setIsFavorite(isFavorite);
                }
                contentService.updateContent(id, content);
                updatedCount++;
            } catch (Exception e) {
                // 忽略错误，继续处理下一个
            }
        }
        return ApiResponse.success(Map.of("updatedCount", updatedCount));
    }
    
    @Operation(summary = "批量删除内容", description = "批量删除内容")
    @DeleteMapping("/contents")
    public ApiResponse<Map<String, Integer>> batchDeleteContents(@RequestBody List<UUID> ids) {
        int deletedCount = 0;
        for (UUID id : ids) {
            try {
                contentService.deleteContent(id);
                deletedCount++;
            } catch (Exception e) {
                // 忽略错误，继续处理下一个
            }
        }
        return ApiResponse.success(Map.of("deletedCount", deletedCount));
    }
}

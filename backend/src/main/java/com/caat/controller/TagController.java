package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.Tag;
import com.caat.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 标签管理控制器
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理", description = "标签管理接口")
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {
    
    private final TagService tagService;
    
    @Operation(summary = "获取所有标签", description = "获取所有标签列表")
    @GetMapping
    public ApiResponse<List<Tag>> getAllTags() {
        return ApiResponse.success(tagService.getAllTags());
    }
    
    @Operation(summary = "分页获取标签", description = "分页获取标签列表")
    @GetMapping("/page")
    public ApiResponse<Page<Tag>> getTags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(tagService.getTags(pageable));
    }
    
    @Operation(summary = "获取标签详情", description = "根据ID获取标签详情")
    @GetMapping("/{id}")
    public ApiResponse<Tag> getTag(@PathVariable UUID id) {
        return ApiResponse.success(tagService.getTagById(id));
    }
    
    @Operation(summary = "创建标签", description = "创建新标签")
    @PostMapping
    public ApiResponse<Tag> createTag(@RequestBody TagCreateRequest request) {
        Tag tag = tagService.createTag(
                request.getName(),
                request.getColor(),
                request.getCategory()
        );
        return ApiResponse.success(tag);
    }
    
    @Operation(summary = "更新标签", description = "更新标签信息")
    @PutMapping("/{id}")
    public ApiResponse<Tag> updateTag(
            @PathVariable UUID id,
            @RequestBody TagCreateRequest request
    ) {
        Tag tag = tagService.updateTag(
                id,
                request.getName(),
                request.getColor(),
                request.getCategory()
        );
        return ApiResponse.success(tag);
    }
    
    @Operation(summary = "删除标签", description = "删除指定标签")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable UUID id) {
        tagService.deleteTag(id);
        return ApiResponse.success(null);
    }
    
    @Operation(summary = "获取热门标签", description = "获取使用次数最多的标签")
    @GetMapping("/popular")
    public ApiResponse<List<Tag>> getPopularTags(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.success(tagService.getPopularTags(limit));
    }
    
    /**
     * 标签创建请求
     */
    public static class TagCreateRequest {
        private String name;
        private String color;
        private String category;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}

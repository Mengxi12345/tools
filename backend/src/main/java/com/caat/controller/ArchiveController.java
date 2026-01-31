package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.entity.ArchiveRule;
import com.caat.repository.ArchiveRuleRepository;
import com.caat.service.ArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 归档管理控制器
 */
@Tag(name = "归档管理", description = "归档规则和执行接口")
@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
public class ArchiveController {
    
    private final ArchiveService archiveService;
    private final ArchiveRuleRepository archiveRuleRepository;
    
    @Operation(summary = "执行归档规则", description = "手动执行指定的归档规则")
    @PostMapping("/rules/{ruleId}/execute")
    public ApiResponse<Map<String, Object>> executeArchiveRule(@PathVariable UUID ruleId) {
        int count = archiveService.executeArchiveRule(ruleId);
        return ApiResponse.success(Map.of("archivedCount", count));
    }
    
    @Operation(summary = "批量归档", description = "批量归档指定的内容")
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchArchive(
            @RequestBody List<UUID> contentIds,
            @RequestParam String category
    ) {
        int count = archiveService.batchArchive(contentIds, category);
        return ApiResponse.success(Map.of("archivedCount", count));
    }
    
    @Operation(summary = "获取所有归档规则", description = "获取所有归档规则列表")
    @GetMapping("/rules")
    public ApiResponse<List<ArchiveRule>> getAllRules() {
        return ApiResponse.success(archiveRuleRepository.findAll());
    }
    
    @Operation(summary = "获取启用的归档规则", description = "获取所有启用的归档规则")
    @GetMapping("/rules/enabled")
    public ApiResponse<List<ArchiveRule>> getEnabledRules() {
        return ApiResponse.success(archiveRuleRepository.findByIsEnabledTrue());
    }
}

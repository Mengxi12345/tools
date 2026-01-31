package com.caat.controller;

import com.caat.dto.ApiResponse;
import com.caat.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 数据备份控制器
 */
@Tag(name = "数据备份", description = "数据库备份和恢复")
@RestController
@RequestMapping("/api/v1/backup")
@RequiredArgsConstructor
public class BackupController {
    
    private final BackupService backupService;
    
    @Operation(summary = "执行数据库备份", description = "创建完整的数据库备份")
    @PostMapping("/database")
    public ApiResponse<String> performDatabaseBackup() {
        String backupPath = backupService.performDatabaseBackup();
        if (backupPath != null) {
            return ApiResponse.success(backupPath);
        } else {
            return ApiResponse.error(500, "备份失败");
        }
    }
    
    @Operation(summary = "执行增量备份", description = "创建增量备份（自指定时间以来的变更）")
    @PostMapping("/incremental")
    public ApiResponse<String> performIncrementalBackup(
            @RequestParam(required = false) String since
    ) {
        LocalDateTime sinceTime = since != null ? 
                LocalDateTime.parse(since) : LocalDateTime.now().minusDays(1);
        String backupPath = backupService.performIncrementalBackup(sinceTime);
        if (backupPath != null) {
            return ApiResponse.success(backupPath);
        } else {
            return ApiResponse.error(500, "增量备份失败");
        }
    }
    
    @Operation(summary = "列出所有备份", description = "获取所有备份文件列表")
    @GetMapping("/list")
    public ApiResponse<String[]> listBackups() {
        return ApiResponse.success(backupService.listBackups());
    }
}

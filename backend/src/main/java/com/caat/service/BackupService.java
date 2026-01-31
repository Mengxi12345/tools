package com.caat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据备份服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {
    
    @Value("${backup.directory:./backups}")
    private String backupDirectory;
    
    @Value("${backup.enabled:true}")
    private boolean backupEnabled;
    
    /**
     * 执行数据库备份
     */
    public String performDatabaseBackup() {
        if (!backupEnabled) {
            log.warn("备份功能已禁用");
            return null;
        }
        
        try {
            // 创建备份目录
            File backupDir = new File(backupDirectory);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            // 生成备份文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "backup_" + timestamp + ".sql";
            String backupFilePath = backupDirectory + "/" + backupFileName;
            
            // 执行 pg_dump（需要 PostgreSQL 客户端工具）
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "pg_dump",
                    "-h", "localhost",
                    "-U", "caat_user",
                    "-d", "caat_db",
                    "-f", backupFilePath
            );
            
            processBuilder.environment().put("PGPASSWORD", "caat_password");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("数据库备份成功: {}", backupFilePath);
                return backupFilePath;
            } else {
                log.error("数据库备份失败，退出码: {}", exitCode);
                return null;
            }
        } catch (Exception e) {
            log.error("数据库备份异常", e);
            return null;
        }
    }
    
    /**
     * 执行增量备份（仅备份最近变更的数据）
     */
    public String performIncrementalBackup(LocalDateTime since) {
        if (!backupEnabled) {
            log.warn("备份功能已禁用");
            return null;
        }
        
        try {
            File backupDir = new File(backupDirectory);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "incremental_backup_" + timestamp + ".json";
            String backupFilePath = backupDirectory + "/" + backupFileName;
            
            // 这里应该查询数据库获取自指定时间以来的变更数据
            // 简化实现：创建备份元数据文件
            try (FileWriter writer = new FileWriter(backupFilePath)) {
                writer.write("{\n");
                writer.write("  \"backupType\": \"incremental\",\n");
                writer.write("  \"since\": \"" + since + "\",\n");
                writer.write("  \"timestamp\": \"" + LocalDateTime.now() + "\"\n");
                writer.write("}\n");
            }
            
            log.info("增量备份元数据创建成功: {}", backupFilePath);
            return backupFilePath;
        } catch (IOException e) {
            log.error("增量备份失败", e);
            return null;
        }
    }
    
    /**
     * 列出所有备份文件
     */
    public String[] listBackups() {
        File backupDir = new File(backupDirectory);
        if (!backupDir.exists()) {
            return new String[0];
        }
        
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("backup_") || name.startsWith("incremental_backup_"));
        if (files == null) {
            return new String[0];
        }
        
        String[] backupFiles = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            backupFiles[i] = files[i].getName();
        }
        
        return backupFiles;
    }
}

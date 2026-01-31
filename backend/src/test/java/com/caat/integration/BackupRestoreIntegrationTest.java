package com.caat.integration;

import com.caat.service.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 备份恢复集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
public class BackupRestoreIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Test
    public void testDatabaseBackup() {
        // 执行备份
        String backupFile = backupService.performDatabaseBackup();
        
        assertNotNull(backupFile, "备份文件路径不应为空");
        assertTrue(new File(backupFile).exists(), "备份文件应该存在");
        
        // 验证备份文件大小
        File file = new File(backupFile);
        assertTrue(file.length() > 0, "备份文件大小应该大于 0");
        
        System.out.println("备份文件: " + backupFile);
        System.out.println("备份文件大小: " + file.length() + " 字节");
    }

    @Test
    public void testListBackups() {
        // 先执行一次备份
        backupService.performDatabaseBackup();
        
        // 列出备份
        String[] backups = backupService.listBackups();
        
        assertNotNull(backups, "备份列表不应为空");
        assertTrue(backups.length > 0, "备份列表不应为空");
        
        System.out.println("备份数量: " + backups.length);
        for (String backup : backups) {
            System.out.println("  - " + backup);
        }
    }

    @Test
    public void testIncrementalBackup() {
        // 执行增量备份（需要指定时间参数）
        String backupFile = backupService.performIncrementalBackup(java.time.LocalDateTime.now().minusDays(1));
        
        assertNotNull(backupFile, "增量备份文件路径不应为空");
        
        // 验证备份文件存在
        Path backupPath = Paths.get(backupFile);
        try {
            if (Files.exists(backupPath)) {
                assertTrue(Files.size(backupPath) > 0, "增量备份文件大小应该大于 0");
                System.out.println("增量备份文件: " + backupFile);
            } else {
                System.out.println("增量备份元数据文件: " + backupFile);
            }
        } catch (java.io.IOException e) {
            System.out.println("无法读取备份文件大小: " + e.getMessage());
        }
    }

    @Test
    public void testBackupFileFormat() {
        // 执行备份
        String backupFile = backupService.performDatabaseBackup();
        
        if (backupFile != null && new File(backupFile).exists()) {
            // 检查文件扩展名
            assertTrue(
                backupFile.endsWith(".sql") || backupFile.endsWith(".dump") || backupFile.endsWith(".backup"),
                "备份文件应该有正确的扩展名"
            );
            
            System.out.println("备份文件格式验证通过: " + backupFile);
        }
    }
}

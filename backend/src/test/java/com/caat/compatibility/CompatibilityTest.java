package com.caat.compatibility;

import com.caat.service.ContentService;
import com.caat.service.PlatformService;
import com.caat.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 兼容性测试
 */
@SpringBootTest
public class CompatibilityTest {

    @Autowired
    private ContentService contentService;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private UserService userService;

    @Test
    public void testDatabaseCompatibility() {
        // 测试数据库兼容性
        assertDoesNotThrow(() -> {
            contentService.getContents(PageRequest.of(0, 10));
        });
    }

    @Test
    public void testApiCompatibility() {
        // 测试 API 兼容性
        assertDoesNotThrow(() -> {
            platformService.getAllPlatforms();
        });
    }

    @Test
    public void testDataFormatCompatibility() {
        // 测试数据格式兼容性
        assertDoesNotThrow(() -> {
            var stats = contentService.getContentStats(null);
            assertNotNull(stats);
        });
    }
}

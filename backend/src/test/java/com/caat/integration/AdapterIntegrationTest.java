package com.caat.integration;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 平台适配器集成测试
 */
@SpringBootTest
public class AdapterIntegrationTest {

    @Autowired
    private AdapterFactory adapterFactory;

    @Test
    public void testAdapterFactory() {
        // 测试适配器工厂
        List<String> supportedPlatforms = adapterFactory.getSupportedPlatformTypes();
        assertNotNull(supportedPlatforms);
        assertFalse(supportedPlatforms.isEmpty());
        
        // 验证已注册的适配器
        assertTrue(adapterFactory.isSupported("github"));
        assertTrue(adapterFactory.isSupported("MEDIUM"));
        assertTrue(adapterFactory.isSupported("REDDIT"));
        assertTrue(adapterFactory.isSupported("ZHIHU"));
        assertTrue(adapterFactory.isSupported("JUEJIN"));
        assertTrue(adapterFactory.isSupported("CSDN"));
        assertTrue(adapterFactory.isSupported("ZSXQ"));
    }

    @Test
    public void testGetAdapter() {
        // 测试获取适配器
        PlatformAdapter githubAdapter = adapterFactory.getAdapter("github");
        assertNotNull(githubAdapter);
        assertEquals("github", githubAdapter.getPlatformType());
    }

    @Test
    public void testUnsupportedPlatform() {
        // 测试不支持的平台
        assertThrows(Exception.class, () -> {
            adapterFactory.getAdapter("UNKNOWN_PLATFORM");
        });
    }
}

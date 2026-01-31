package com.caat.integration;

import com.caat.controller.ContentController;
import com.caat.entity.ContentDocument;
import com.caat.service.ContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 搜索功能集成测试
 */
@SpringBootTest
@Transactional
public class SearchIntegrationTest {

    @Autowired
    private ContentService contentService;

    @Autowired
    private ContentController contentController;

    @Test
    public void testBasicSearch() {
        // 测试基本搜索功能
        PageRequest pageable = PageRequest.of(0, 10);
        Page<ContentDocument> results = contentService.searchInElasticsearch("test", pageable);
        assertNotNull(results);
    }

    @Test
    public void testRegexSearch() {
        // 测试正则表达式搜索
        PageRequest pageable = PageRequest.of(0, 10);
        Page<ContentDocument> results = contentService.searchByRegex("test.*", pageable);
        assertNotNull(results);
    }

    @Test
    public void testAdvancedSearch() {
        // 测试高级搜索
        PageRequest pageable = PageRequest.of(0, 10);
        Page<ContentDocument> results = contentService.advancedSearch("test", "TEXT", pageable);
        assertNotNull(results);
    }

    @Test
    public void testSearchHistory() {
        // 测试搜索历史记录
        var history = contentService.getSearchHistory(PageRequest.of(0, 10), null);
        assertNotNull(history);
    }

    @Test
    public void testPopularQueries() {
        // 测试热门搜索
        var popular = contentService.getPopularSearchQueries(10);
        assertNotNull(popular);
    }
}

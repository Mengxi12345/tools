package com.caat.performance;

import com.caat.service.ContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * 负载测试
 */
@SpringBootTest
public class LoadTest {

    @Autowired
    private ContentService contentService;

    @Test
    public void testLargeDatasetQuery() {
        // 大数据量查询测试
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 100; i++) {
            contentService.getContents(PageRequest.of(0, 100));
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("大数据量查询测试完成，总耗时: " + (endTime - startTime) + "ms");
    }

    @Test
    public void testPaginationPerformance() {
        // 分页性能测试
        long startTime = System.currentTimeMillis();
        
        for (int page = 0; page < 10; page++) {
            contentService.getContents(PageRequest.of(page, 20));
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("分页查询测试完成，总耗时: " + (endTime - startTime) + "ms");
    }
}

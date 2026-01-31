package com.caat.performance;

import com.caat.service.ContentService;
import com.caat.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能测试
 */
@SpringBootTest
public class PerformanceTest {

    @Autowired
    private ContentService contentService;

    @Autowired
    private StatsService statsService;

    @Test
    public void testConcurrentSearch() throws InterruptedException {
        // 并发搜索测试
        int threadCount = 10;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * requestsPerThread);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    try {
                        contentService.searchByKeyword("test", 
                            org.springframework.data.domain.PageRequest.of(0, 20));
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        System.out.println("并发搜索测试完成，总耗时: " + (endTime - startTime) + "ms");
        assertTrue(endTime - startTime < 10000); // 应该在10秒内完成
    }

    @Test
    public void testStatsPerformance() {
        // 统计性能测试
        long startTime = System.currentTimeMillis();
        
        statsService.getPlatformDistribution();
        statsService.getUserStats();
        statsService.getTagUserStatistics();
        statsService.getContentTypeDistribution();
        statsService.getActiveUsersRanking(10);
        statsService.getContentGrowthTrend(30);
        
        long endTime = System.currentTimeMillis();
        
        System.out.println("统计查询总耗时: " + (endTime - startTime) + "ms");
        assertTrue(endTime - startTime < 5000); // 应该在5秒内完成
    }
}

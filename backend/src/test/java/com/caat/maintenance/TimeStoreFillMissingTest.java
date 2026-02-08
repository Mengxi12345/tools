package com.caat.maintenance;

import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.FetchResult;
import com.caat.adapter.model.PlatformContent;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.repository.TrackedUserRepository;
import com.caat.service.ContentFetchService;
import com.caat.service.PlatformConfigUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TimeStore 文章补漏测试
 * 遍历所有 TimeStore 用户，按现有接口依次查询文章，若数据库已有则跳过，没有则写入。
 *
 * 注意：使用 dev profile，需确保数据库与平台配置正确。
 * 运行：mvn test -Dtest=TimeStoreFillMissingTest
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
public class TimeStoreFillMissingTest {

    private static final String PLATFORM_TYPE = "TIMESTORE";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_CONCURRENT_USERS = 4;

    @Autowired
    @Qualifier("timeStoreAdapter")
    private PlatformAdapter timeStoreAdapter;

    @Autowired
    private TrackedUserRepository trackedUserRepository;

    @Autowired
    private ContentFetchService contentFetchService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 全量补漏：遍历所有 TimeStore 用户，并发拉取并补漏
     */
    @Test
    @DisplayName("TimeStore 全用户补漏")
    void fillMissingForAllTimestoreUsers() {
        List<TrackedUser> users = trackedUserRepository.findByPlatformTypeWithPlatform(PLATFORM_TYPE);
        if (users.isEmpty()) {
            log.warn("未找到任何 TimeStore 用户，跳过补漏");
            return;
        }
        log.info("开始 TimeStore 补漏，共 {} 个用户", users.size());

        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_USERS);
        AtomicInteger totalSaved = new AtomicInteger(0);
        AtomicInteger totalSkipped = new AtomicInteger(0);
        AtomicInteger totalFetched = new AtomicInteger(0);

        try {
            List<CompletableFuture<Void>> futures = users.stream()
                .map(user -> CompletableFuture.runAsync(() -> {
                    try {
                        int[] result = fillMissingForUser(user);
                        totalSaved.addAndGet(result[0]);
                        totalSkipped.addAndGet(result[1]);
                        totalFetched.addAndGet(result[2]);
                    } catch (Exception e) {
                        log.error("补漏失败: userId={}, username={}", user.getUserId(), user.getUsername(), e);
                    }
                }, executor))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }

        log.info("TimeStore 补漏完成: 用户数={}, 新写入={}, 已存在跳过={}, 本次拉取总量={}",
            users.size(), totalSaved.get(), totalSkipped.get(), totalFetched.get());
    }

    /**
     * 单用户补漏：针对 userId=3906890 的用户
     */
    @Test
    @DisplayName("TimeStore 单用户补漏 - 3906890")
    void fillMissingForUser_3906890() {
        List<TrackedUser> users = trackedUserRepository.findByPlatformTypeWithPlatform(PLATFORM_TYPE);
        Optional<TrackedUser> userOpt = users.stream()
            .filter(u -> "3906890".equals(u.getUserId()))
            .findFirst();

        if (userOpt.isEmpty()) {
            log.warn("未找到 userId=3906890 的 TimeStore 用户，请确认该用户已添加并关联 TimeStore 平台");
            return;
        }

        TrackedUser user = userOpt.get();
        log.info("开始单用户补漏: userId={}, username={}", user.getUserId(), user.getUsername());

        int[] result = fillMissingForUser(user);
        log.info("单用户补漏完成: 新写入={}, 已存在跳过={}, 本次拉取总量={}",
            result[0], result[1], result[2]);
    }

    /**
     * 对单个用户执行补漏
     * @return [新写入数, 已存在跳过数, 本次拉取总量]
     */
    private int[] fillMissingForUser(TrackedUser user) {
        Map<String, Object> config = PlatformConfigUtil.mergePlatformConfig(
            user.getPlatform(),
            PlatformConfigUtil.parseConfig(objectMapper, user.getPlatform().getConfig())
        );

        int savedCount = 0;
        int skippedCount = 0;
        int fetchedCount = 0;
        String cursor = null;

        do {
            FetchResult result = timeStoreAdapter.getUserContents(
                user.getUserId(),
                config,
                null,
                null,
                cursor,
                PAGE_SIZE
            );

            if (result == null || result.getContents() == null) {
                break;
            }

            List<PlatformContent> contents = result.getContents();
            fetchedCount += contents.size();

            for (PlatformContent pc : contents) {
                try {
                    var saveResult = contentFetchService.saveContentWithResult(pc, user.getPlatform(), user);
                    if (saveResult.wasNew()) {
                        savedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.warn("保存失败: contentId={}, error={}", pc.getContentId(), e.getMessage());
                }
            }

            if (!result.isHasMore()) {
                break;
            }
            cursor = result.getNextCursor();

            try {
                Thread.sleep(2000 + (long) (Math.random() * 3000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (cursor != null);

        log.info("用户补漏: userId={}, 新写入={}, 跳过={}, 拉取={}", user.getUserId(), savedCount, skippedCount, fetchedCount);
        return new int[]{savedCount, skippedCount, fetchedCount};
    }
}

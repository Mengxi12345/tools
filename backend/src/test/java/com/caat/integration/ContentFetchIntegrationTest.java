package com.caat.integration;

import com.caat.entity.Content;
import com.caat.entity.FetchTask;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.repository.ContentRepository;
import com.caat.repository.FetchTaskRepository;
import com.caat.repository.PlatformRepository;
import com.caat.repository.TrackedUserRepository;
import com.caat.service.ContentFetchService;
import com.caat.service.PlatformService;
import com.caat.service.TrackedUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内容拉取集成测试
 * 测试完整的内容拉取流程：添加平台 -> 添加用户 -> 拉取内容 -> 查看内容
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("内容拉取集成测试")
public class ContentFetchIntegrationTest {

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TrackedUserService trackedUserService;

    @Autowired
    private ContentFetchService contentFetchService;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private TrackedUserRepository trackedUserRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private FetchTaskRepository fetchTaskRepository;

    private Platform githubPlatform;
    private TrackedUser testUser;

    @BeforeEach
    void setUp() {
        // 创建 GitHub 平台（如果不存在）
        Optional<Platform> existingPlatform = platformRepository.findByName("GitHub");
        if (existingPlatform.isEmpty()) {
            Platform platform = new Platform();
            platform.setName("GitHub");
            platform.setType("GITHUB");
            platform.setApiBaseUrl("https://api.github.com");
            platform.setConfig("{\"rateLimit\": 5000}");
            platform.setStatus(Platform.PlatformStatus.ACTIVE);
            githubPlatform = platformService.createPlatform(platform);
        } else {
            githubPlatform = existingPlatform.get();
        }

        // 创建测试用户（使用 GitHub 官方测试用户 octocat）
        Optional<TrackedUser> existingUser = trackedUserRepository.findByPlatformIdAndUserId(
            githubPlatform.getId(), "octocat");
        if (existingUser.isEmpty()) {
            TrackedUser user = new TrackedUser();
            user.setPlatform(githubPlatform);
            user.setUsername("octocat");
            user.setUserId("octocat");
            user.setDisplayName("The Octocat");
            user.setIsActive(true);
            testUser = trackedUserService.createUser(user);
        } else {
            testUser = existingUser.get();
        }
    }

    @Test
    @DisplayName("测试完整的内容拉取流程")
    @Transactional
    void testCompleteContentFetchFlow() throws InterruptedException {
        // 1. 验证平台已创建
        assertNotNull(githubPlatform.getId(), "平台ID不应为空");
        assertEquals("GitHub", githubPlatform.getName(), "平台名称应为GitHub");
        assertEquals("GITHUB", githubPlatform.getType(), "平台类型应为GITHUB");

        // 2. 验证用户已创建
        assertNotNull(testUser.getId(), "用户ID不应为空");
        assertEquals("octocat", testUser.getUsername(), "用户名应为octocat");
        assertEquals(githubPlatform.getId(), testUser.getPlatform().getId(), "用户应关联到GitHub平台");

        // 3. 验证初始状态：没有内容
        Pageable pageable = PageRequest.of(0, 100);
        Page<Content> initialContentsPage = contentRepository.findByUserId(testUser.getId(), pageable);
        assertEquals(0, initialContentsPage.getTotalElements(), "初始状态下应没有内容");

        // 4. 手动触发内容拉取（使用默认模式：从最后拉取时间至今）
        LocalDateTime endTime = LocalDateTime.now();
        contentFetchService.fetchUserContentAsync(testUser.getId(), null, endTime, "normal", null);

        // 5. 等待异步任务完成（最多等待30秒）
        int maxWaitSeconds = 30;
        int waitedSeconds = 0;
        Page<Content> fetchedContentsPage = contentRepository.findByUserId(testUser.getId(), pageable);
        while (fetchedContentsPage.getTotalElements() == 0 && waitedSeconds < maxWaitSeconds) {
            Thread.sleep(2000); // 等待2秒
            waitedSeconds += 2;
            fetchedContentsPage = contentRepository.findByUserId(testUser.getId(), pageable);
        }
        List<Content> fetchedContents = fetchedContentsPage.getContent();

        // 6. 验证内容已拉取
        assertFalse(fetchedContents.isEmpty(), 
            String.format("应该在30秒内拉取到内容，但实际等待了%d秒", waitedSeconds));
        
        // 7. 验证拉取的内容数据
        Content firstContent = fetchedContents.get(0);
        assertNotNull(firstContent.getId(), "内容ID不应为空");
        assertNotNull(firstContent.getTitle(), "内容标题不应为空");
        assertNotNull(firstContent.getUrl(), "内容URL不应为空");
        assertEquals(testUser.getId(), firstContent.getUser().getId(), "内容应关联到测试用户");
        assertEquals(githubPlatform.getId(), firstContent.getPlatform().getId(), "内容应关联到GitHub平台");

        // 8. 验证内容类型（GitHub 可能返回 Issues, PRs, Commits, Repositories）
        assertNotNull(firstContent.getContentType(), "内容类型不应为空");
        assertTrue(
            firstContent.getContentType().equals("ISSUE") ||
            firstContent.getContentType().equals("PULL_REQUEST") ||
            firstContent.getContentType().equals("COMMIT") ||
            firstContent.getContentType().equals("REPOSITORY"),
            "内容类型应为GitHub支持的类型"
        );

        // 9. 验证元数据（metadata字段应为JSON格式）
        if (firstContent.getMetadata() != null && !firstContent.getMetadata().isEmpty()) {
            assertTrue(
                firstContent.getMetadata().startsWith("{") && firstContent.getMetadata().endsWith("}"),
                "元数据应为有效的JSON格式"
            );
        }

        // 10. 验证用户最后拉取时间已更新
        TrackedUser updatedUser = trackedUserRepository.findById(testUser.getId()).orElse(null);
        assertNotNull(updatedUser, "用户应仍然存在");
        assertNotNull(updatedUser.getLastFetchedAt(), "最后拉取时间应已更新");

        System.out.println("✅ 集成测试通过！");
        System.out.println("   - 平台创建：成功");
        System.out.println("   - 用户创建：成功");
        System.out.println("   - 内容拉取：成功（拉取了 " + fetchedContents.size() + " 条内容）");
        System.out.println("   - 内容验证：成功");
    }

    @Test
    @DisplayName("测试自定义时间范围的内容拉取")
    @Transactional
    void testCustomTimeRangeContentFetch() throws InterruptedException {
        // 1. 设置自定义时间范围（过去7天）
        LocalDateTime startTime = LocalDateTime.now().minusDays(7);
        LocalDateTime endTime = LocalDateTime.now();

        // 2. 触发内容拉取
        contentFetchService.fetchUserContentAsync(testUser.getId(), startTime, endTime, "normal", null);

        // 3. 等待异步任务完成
        int maxWaitSeconds = 30;
        int waitedSeconds = 0;
        Pageable pageable = PageRequest.of(0, 100);
        Page<Content> fetchedContentsPage = contentRepository.findByUserId(testUser.getId(), pageable);
        while (fetchedContentsPage.getTotalElements() == 0 && waitedSeconds < maxWaitSeconds) {
            Thread.sleep(2000);
            waitedSeconds += 2;
            fetchedContentsPage = contentRepository.findByUserId(testUser.getId(), pageable);
        }
        List<Content> fetchedContents = fetchedContentsPage.getContent();

        // 4. 验证内容已拉取
        if (!fetchedContents.isEmpty()) {
            Content firstContent = fetchedContents.get(0);
            assertNotNull(firstContent.getCreatedAt(), "内容创建时间不应为空");
            assertTrue(
                firstContent.getCreatedAt().isAfter(startTime) || 
                firstContent.getCreatedAt().isEqual(startTime),
                "内容创建时间应在指定时间范围内"
            );
            System.out.println("✅ 自定义时间范围测试通过！拉取了 " + fetchedContents.size() + " 条内容");
        } else {
            System.out.println("⚠️  自定义时间范围内没有拉取到内容（可能是该时间段内没有新内容）");
        }
    }

    @Test
    @DisplayName("测试内容去重功能")
    @Transactional
    void testContentDeduplication() throws InterruptedException {
        // 1. 第一次拉取
        LocalDateTime endTime1 = LocalDateTime.now();
        contentFetchService.fetchUserContentAsync(testUser.getId(), null, endTime1, "normal", null);

        // 等待第一次拉取完成
        Thread.sleep(5000);
        Pageable pageable = PageRequest.of(0, 1000);
        Page<Content> firstFetchPage = contentRepository.findByUserId(testUser.getId(), pageable);
        int firstFetchCount = (int) firstFetchPage.getTotalElements();

        // 2. 第二次拉取（应该去重）
        LocalDateTime endTime2 = LocalDateTime.now();
        contentFetchService.fetchUserContentAsync(testUser.getId(), null, endTime2, "normal", null);

        // 等待第二次拉取完成
        Thread.sleep(5000);
        Page<Content> secondFetchPage = contentRepository.findByUserId(testUser.getId(), pageable);
        int secondFetchCount = (int) secondFetchPage.getTotalElements();
        List<Content> secondFetch = secondFetchPage.getContent();

        // 3. 验证去重功能（第二次拉取不应大幅增加内容数量）
        // 注意：由于GitHub API可能返回新内容，我们只验证不会重复添加相同的内容
        assertTrue(
            secondFetchCount >= firstFetchCount,
            "第二次拉取后内容数量应大于等于第一次（可能拉取到新内容，但不应重复）"
        );

        // 4. 验证内容的hash唯一性
        long uniqueHashCount = secondFetch.stream()
            .map(Content::getHash)
            .distinct()
            .count();
        assertEquals(secondFetchCount, uniqueHashCount, "所有内容的hash应该是唯一的（去重生效）");

        System.out.println("✅ 内容去重测试通过！");
        System.out.println("   - 第一次拉取：" + firstFetchCount + " 条内容");
        System.out.println("   - 第二次拉取：" + secondFetchCount + " 条内容");
        System.out.println("   - 唯一hash数量：" + uniqueHashCount);
    }
}

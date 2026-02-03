package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.FetchResult;
import com.caat.adapter.model.PlatformContent;
import com.caat.entity.Content;
import com.caat.entity.FetchTask;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.repository.ContentRepository;
import com.caat.repository.FetchTaskRepository;
import com.caat.repository.TrackedUserRepository;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 内容拉取服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentFetchService {
    
    private final TrackedUserRepository trackedUserRepository;
    private final ContentRepository contentRepository;
    private final FetchTaskRepository fetchTaskRepository;
    private final AdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    private final ElasticsearchService elasticsearchService;
    private final NotificationService notificationService;
    private final ContentAssetService contentAssetService;
    private final ZsxqFileService zsxqFileService;
    
    /** 用于单条保存时开启新事务，避免一条失败导致整批回滚（setter 注入打破循环依赖） */
    private ContentFetchService self;

    @Lazy
    @Autowired
    public void setSelf(ContentFetchService self) {
        this.self = self;
    }
    
    /**
     * 手动刷新用户内容（异步执行）
     * @param userId 用户ID
     * @param startTime 开始时间（可为null，使用最后拉取时间）
     * @param endTime 结束时间（可为null，使用当前时间）
     * @param fetchMode "fast"=快速（单页少量），"normal"=完整
     * @param taskId 已创建的任务ID（可为null，会创建新任务）
     */
    @Async
    @Transactional
    public void fetchUserContentAsync(UUID userId, LocalDateTime startTime, LocalDateTime endTime, String fetchMode, UUID taskId) {
        TrackedUser user = trackedUserRepository.findById(userId).orElse(null);
        if (user == null) {
            markTaskFailed(taskId, "用户不存在");
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 获取或创建刷新任务（不做速率限制，点击即拉取并保存）
        FetchTask task;
        if (taskId != null) {
            task = fetchTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                throw new BusinessException(ErrorCode.FETCH_TASK_NOT_FOUND);
            }
        } else {
            task = new FetchTask();
            task.setUser(user);
            task.setTaskType(FetchTask.TaskType.MANUAL);
            task.setStartTime(startTime);
            task.setEndTime(endTime != null ? endTime : LocalDateTime.now());
            task.setStatus(FetchTask.TaskStatus.PENDING);
            task = fetchTaskRepository.save(task);
        }
        
        try {
            task.setStatus(FetchTask.TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(0);
            task.setFetchedCount(0);
            fetchTaskRepository.save(task);
            
            // 完整拉取且未传日期：不传 start/end；若用户已有文章则按已有数量估算起始页向前拉取，拉完后再拉「最新文章时间～现在」
            boolean fullFetchNoDate = (startTime == null && endTime == null);
            LocalDateTime effectiveStart = startTime;
            LocalDateTime effectiveEnd = endTime;
            if (fullFetchNoDate) {
                log.info("完整拉取: 不传时间范围，逐页拉取全部文章 userId={}", userId);
                task.setStartTime(null);
                task.setEndTime(null);
                fetchTaskRepository.save(task);
            } else {
                if (effectiveStart == null) {
                    effectiveStart = contentRepository.findMaxPublishedAtByUserId(userId).orElse(null);
                    if (effectiveStart != null) {
                        log.info("增量拉取: 用户已有存储文章，从最后发布时间至当前 userId={}, startTime={}", userId, effectiveStart);
                    } else {
                        log.info("全量拉取: 用户无存储文章 userId={}", userId);
                    }
                    task.setStartTime(effectiveStart);
                    fetchTaskRepository.save(task);
                }
            }
            
            // 获取平台适配器
            PlatformAdapter adapter = adapterFactory.getAdapter(user.getPlatform().getType());
            
            // 解析平台配置，并合并平台级 apiBaseUrl 供适配器使用（如 TimeStore）
            Map<String, Object> config = mergePlatformConfig(
                user.getPlatform(),
                parseConfig(user.getPlatform().getConfig())
            );
            
            int limit = "fast".equalsIgnoreCase(fetchMode) ? 20 : 150; // normal 时单页可到 100~150
            if ("normal".equalsIgnoreCase(fetchMode)) {
                log.info("完整拉取模式: 逐页拉取并实时保存、更新进度 userId={}, platform={}", userId, user.getPlatform().getType());
            }
            
            // 知识星球：两阶段拉取（第一步用 end_time 向后取旧文章+随机等待+失败重试5次；第二步取最新几页直到遇到第一步时的最新文章即停）
            int totalSaved;
            if ("ZSXQ".equalsIgnoreCase(user.getPlatform().getType()) && fullFetchNoDate) {
                totalSaved = runZsxqTwoPhaseFetch(adapter, config, user, task, limit, userId);
            } else {
                Long existingCount = fullFetchNoDate ? contentRepository.countByUserId(userId) : null;
                boolean hadExistingContent = (existingCount != null && existingCount > 0);
                String cursor = null;
                if (hadExistingContent && fullFetchNoDate) {
                    int estimatedPageSize = 100;
                    int startPage = (int) Math.max(1, (existingCount / estimatedPageSize) + 1);
                    cursor = String.valueOf(startPage);
                    log.info("完整拉取(用户已有文章): 从约第 {} 页开始向前拉取 userId={}, 已有文章数={}", cursor, userId, existingCount);
                }
                log.info("开始拉取用户内容: userId={}, platform={}, startTime={}, endTime={}, limit={}, initialCursor={}",
                    userId, user.getPlatform().getType(), effectiveStart, effectiveEnd, limit, cursor);
                totalSaved = runPaginationPhase(adapter, config, user, task, effectiveStart, effectiveEnd, cursor, limit, 0, userId);
                if (fullFetchNoDate && hadExistingContent) {
                    LocalDateTime maxPub = contentRepository.findMaxPublishedAtByUserId(userId).orElse(null);
                    if (maxPub != null) {
                        log.info("完整拉取(用户已有文章): 再拉取 最新文章时间～现在 userId={}, startTime={}", userId, maxPub);
                        totalSaved += runPaginationPhase(adapter, config, user, task, maxPub, LocalDateTime.now(), null, limit, totalSaved, userId);
                    }
                }
            }
            
            // 更新任务状态
            task.setStatus(FetchTask.TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setProgress(100);
            task.setFetchedCount(totalSaved);
            task.setTotalCount(totalSaved);
            fetchTaskRepository.save(task);
            
            // 更新用户最后拉取时间
            user.setLastFetchedAt(LocalDateTime.now());
            trackedUserRepository.save(user);
            
            log.info("拉取内容完成: userId={}, savedCount={}", userId, totalSaved);
            
        } catch (BusinessException e) {
            log.error("拉取内容失败（业务异常）: userId={}", userId, e);
            task.setStatus(FetchTask.TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            fetchTaskRepository.save(task);
            throw e;
        } catch (Exception e) {
            log.error("拉取内容失败: userId={}", userId, e);
            task.setStatus(FetchTask.TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            fetchTaskRepository.save(task);
        }
    }
    
    /**
     * 知识星球刷新拉取（仅 ZSXQ 使用）：
     * 一、用户没有文章：第一页不带 end_time 拉取 20 条全部保存；后续页用本页「时间最久的一篇」的 create_time 作为 end_time（adapter 的 nextCursor）拉取 20 条全部保存；重复直到某次请求返回没有文章为止。
     * 二、用户已有文章：第一页不带 end_time 拉取 20 条，按条顺序处理，若某条已存在（contentId/hash）则只保存本页中该条之前的新文章并停止；若本页无重复则用本页第 20 条（时间最久）的 create_time 作为 end_time 拉取下一页，同样按条判断，一旦遇到重复则保存本页此前新文章并停止；重复直到某一页出现重复为止。
     */
    private int runZsxqTwoPhaseFetch(PlatformAdapter adapter, Map<String, Object> config, TrackedUser user,
                                    FetchTask task, int limit, UUID userId) throws Exception {
        long existingCount = contentRepository.countByUserId(userId);
        boolean hasExistingContent = (existingCount > 0);

        final int retryMax = 5;
        final int delaySecMin = 2;
        final int delaySecMax = 5;
        /** 单用户最多翻页次数，避免在 API 一直返回 hasMore 时无限请求 */
        final int maxPages = 50;

        String cursor = null;
        log.info("知识星球拉取: userId={}, 已有文章={}, 从最新一页开始，用每页时间最久的一篇的 create_time 作为 end_time 翻页，随机等待 {}～{} 秒，失败重试 {} 次，最多 {} 页",
            userId, hasExistingContent, delaySecMin, delaySecMax, retryMax, maxPages);

        int savedInPhase = 0;
        int pageNum = 0;
        boolean stopDueToDuplicate = false;

        while (true) {
            pageNum++;
            if (pageNum > maxPages) {
                log.warn("知识星球: 已达最大页数 {}，停止拉取 userId={}", maxPages, userId);
                break;
            }
            randomWait(delaySecMin, delaySecMax);
            FetchResult result = null;
            int retries = 0;
            while (retries <= retryMax) {
                try {
                    result = adapter.getUserContents(user.getUserId(), config, null, null, cursor, limit);
                    break;
                } catch (Exception e) {
                    retries++;
                    if (retries > retryMax) {
                        log.error("知识星球 第 {} 页失败，已重试 {} 次: userId={}", pageNum, retryMax, userId, e);
                        throw e;
                    }
                    int sleepSec = delaySecMin + ThreadLocalRandom.current().nextInt(delaySecMax - delaySecMin + 1);
                    Thread.sleep(sleepSec * 1000L);
                    log.warn("知识星球 第 {} 页失败，{} 秒后重试 ({}/{}): {}", pageNum, sleepSec, retries, retryMax, e.getMessage());
                }
            }
            // 本次请求没有文章则停止（无文章场景：直到获取请求没有文章为止；有文章场景：不应出现空页后继续）
            if (result == null || result.getContents() == null || result.getContents().isEmpty()) {
                log.info("知识星球: 本页无文章，结束拉取 userId={}", userId);
                break;
            }

            if (hasExistingContent) {
                // 用户有文章：按条保存，遇到已存在的 contentId 则保存此前新文章并停止
                for (PlatformContent pc : result.getContents()) {
                    if (contentExists(pc)) {
                        log.info("知识星球: 遇到重复文章 contentId={}，保存本页新文章后停止", pc.getContentId());
                        stopDueToDuplicate = true;
                        break;
                    }
                    try {
                        if (self.saveContent(pc, user.getPlatform(), user) != null) savedInPhase++;
                    } catch (Exception e) {
                        log.warn("保存内容失败: contentId={}", pc.getContentId(), e);
                    }
                }
            } else {
                // 用户没有文章：本页全部保存
                for (PlatformContent pc : result.getContents()) {
                    try {
                        if (self.saveContent(pc, user.getPlatform(), user) != null) savedInPhase++;
                    } catch (Exception e) {
                        log.warn("保存内容失败: contentId={}", pc.getContentId(), e);
                    }
                }
            }

            task.setFetchedCount(savedInPhase);
            task.setTotalCount(savedInPhase);
            task.setProgress(result.isHasMore() ? Math.min(99, 99) : 100);
            fetchTaskRepository.save(task);

            if (stopDueToDuplicate || !result.isHasMore()) break;
            cursor = result.getNextCursor();
        }

        task.setProgress(100);
        fetchTaskRepository.save(task);
        log.info("知识星球拉取结束: userId={}, 保存={}", userId, savedInPhase);
        return savedInPhase;
    }

    private void randomWait(int secMin, int secMax) throws InterruptedException {
        int sec = secMin + ThreadLocalRandom.current().nextInt(Math.max(1, secMax - secMin + 1));
        Thread.sleep(sec * 1000L);
    }

    /**
     * 执行一轮分页拉取：按页请求适配器、逐页保存并更新任务进度，返回本阶段新保存条数。
     * @param totalSavedOffset 本阶段开始前已保存总数，用于 task.setFetchedCount(offset + 本阶段保存数)
     * @return 本阶段新保存条数
     */
    private int runPaginationPhase(PlatformAdapter adapter, Map<String, Object> config, TrackedUser user,
                                   FetchTask task, LocalDateTime effectiveStart, LocalDateTime effectiveEnd,
                                   String initialCursor, int limit, int totalSavedOffset, UUID userId) throws Exception {
        String cursor = initialCursor;
        int savedInPhase = 0;
        int pageNum = 0;
        final int maxRetries = 10;
        final int retrySleepSecMin = 10;
        final int retrySleepSecMax = 60;
        do {
            pageNum++;
            int retries = 0;
            FetchResult result = null;
            while (retries <= maxRetries) {
                try {
                    result = adapter.getUserContents(
                        user.getUserId(),
                        config,
                        effectiveStart,
                        effectiveEnd,
                        cursor,
                        limit
                    );
                    break;
                } catch (Exception e) {
                    retries++;
                    if (retries > maxRetries) {
                        log.error("拉取第 {} 页失败，已重试 {} 次，放弃: userId={}", pageNum, maxRetries, userId, e);
                        throw e;
                    }
                    int sleepSec = retrySleepSecMin + ThreadLocalRandom.current().nextInt(retrySleepSecMax - retrySleepSecMin + 1);
                    Thread.sleep(sleepSec * 1000L);
                    log.warn("拉取第 {} 页失败，{} 秒后重试 ({}/{}): {}", pageNum, sleepSec, retries, maxRetries, e.getMessage());
                }
            }
            if (result == null) {
                log.warn("[保存排查] 适配器返回 result=null");
                break;
            }
            if (result.getContents() == null || result.getContents().isEmpty()) {
                if (pageNum == 1) log.warn("[保存排查] 适配器返回空内容");
                if (!result.isHasMore()) break;
                cursor = result.getNextCursor();
                Thread.sleep(2000 + ThreadLocalRandom.current().nextInt(4000));
                continue;
            }
            int savedThisPage = 0;
            for (PlatformContent platformContent : result.getContents()) {
                try {
                    if (self.saveContent(platformContent, user.getPlatform(), user) != null) {
                        savedThisPage++;
                    }
                } catch (Exception e) {
                    log.warn("保存内容失败: contentId={}", platformContent.getContentId(), e);
                }
            }
            savedInPhase += savedThisPage;
            log.info("[保存排查] 第 {} 页保存 {} 条, 本阶段累计 {} 条", pageNum, savedThisPage, savedInPhase);
            task.setFetchedCount(totalSavedOffset + savedInPhase);
            task.setTotalCount(totalSavedOffset + savedInPhase);
            task.setProgress(result.isHasMore() ? Math.min(99, totalSavedOffset + savedInPhase > 0 ? 99 : 0) : 100);
            fetchTaskRepository.save(task);
            if (!result.isHasMore()) break;
            cursor = result.getNextCursor();
            Thread.sleep(2000 + ThreadLocalRandom.current().nextInt(4000));
        } while (true);
        return savedInPhase;
    }

    /**
     * 保存内容到数据库（独立事务：单条失败不影响其他条）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Content saveContent(PlatformContent platformContent, Platform platform, TrackedUser user) {
        // 生成内容哈希
        String hash = generateContentHash(platformContent);
        
        // 检查是否已存在
        if (contentRepository.existsByHash(hash)) {
            log.info("[保存排查] 内容已存在，跳过: contentId={}, hash={}", platformContent.getContentId(), hash);
            return contentRepository.findByHash(hash).orElse(null);
        }
        
        // 创建内容实体
        Content content = new Content();
        content.setPlatform(platform);
        content.setUser(user);
        content.setContentId(platformContent.getContentId());
        content.setTitle(platformContent.getTitle());
        content.setBody(platformContent.getBody());
        content.setUrl(platformContent.getUrl());
        content.setContentType(convertContentType(platformContent.getContentType()));
        List<String> mediaUrls = platformContent.getMediaUrls();
        Map<String, Object> metadataMap = platformContent.getMetadata() != null
            ? new HashMap<>(platformContent.getMetadata()) : new HashMap<>();

        if ("ZSXQ".equalsIgnoreCase(platform.getType())) {
            mediaUrls = downloadZsxqImagesToLocal(mediaUrls);
            List<Map<String, String>> downloadedFiles = downloadZsxqFilesToLocal(platform, metadataMap);
            if (!downloadedFiles.isEmpty()) {
                metadataMap.put("downloaded_file_urls", downloadedFiles);
            }
        }

        content.setMediaUrls(mediaUrls != null ? mediaUrls : platformContent.getMediaUrls());
        LocalDateTime publishedAt = platformContent.getPublishedAt() != null
            ? platformContent.getPublishedAt()
            : LocalDateTime.now();
        content.setPublishedAt(publishedAt);

        // 保存前再次确保 published_at 非空，满足 DB 约束
        if (content.getPublishedAt() == null) {
            content.setPublishedAt(LocalDateTime.now());
        }
        
        // 保存元数据为 JSON（含 talk 与 downloaded_file_urls）
        try {
            if (!metadataMap.isEmpty()) {
                content.setMetadata(objectMapper.writeValueAsString(metadataMap));
            }
        } catch (Exception e) {
            log.warn("保存元数据失败", e);
        }
        
        content.setHash(hash);
        content.setIsRead(false);
        content.setIsFavorite(false);
        
        Content saved = contentRepository.save(content);
        log.info("[保存排查] 保存内容成功: contentId={}, id={}", platformContent.getContentId(), saved.getId());
        if (log.isDebugEnabled()) {
            String title = saved.getTitle();
            String titleShort = title != null && title.length() > 80 ? title.substring(0, 80) + "..." : title;
            log.debug("存储内容: id={}, contentId={}, title={}, userId={}, platform={}",
                    saved.getId(), saved.getContentId(), titleShort,
                    content.getUser() != null ? content.getUser().getId() : null,
                    content.getPlatform() != null ? content.getPlatform().getName() : null);
        }
        // 触发通知规则（如 QQ 群推送）：异步避免阻塞拉取
        try {
            notificationService.checkAndNotify(saved);
        } catch (Exception e) {
            log.warn("通知规则检查或发送失败: contentId={}", saved.getId(), e);
        }
        return saved;
    }
    
    /**
     * 知识星球：将正文中的图片 URL 下载到本地，返回本地 URL 列表（失败则保留原 URL）
     */
    private List<String> downloadZsxqImagesToLocal(List<String> urls) {
        if (urls == null || urls.isEmpty()) return urls;
        List<String> local = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isEmpty()) {
                local.add(url);
                continue;
            }
            String u = url.trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                local.add(url);
                continue;
            }
            try {
                String localUrl = contentAssetService.downloadImageAndSave(u);
                local.add(localUrl != null ? localUrl : url);
            } catch (Exception e) {
                log.warn("下载知识星球图片失败，保留原 URL: url={}", u, e);
                local.add(url);
            }
        }
        return local;
    }

    /**
     * 知识星球：根据 file_ids 调用 API 获取 download_url 并下载到本地，返回 { file_id, local_url } 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> downloadZsxqFilesToLocal(Platform platform, Map<String, Object> metadataMap) {
        List<Map<String, String>> result = new ArrayList<>();
        Object fileIdsObj = metadataMap.get("file_ids");
        if (fileIdsObj == null || !(fileIdsObj instanceof List)) return result;
        List<?> fileIds = (List<?>) fileIdsObj;
        if (fileIds.isEmpty()) return result;
        Map<String, Object> config = mergePlatformConfig(platform, parseConfig(platform.getConfig()));
        for (Object fidObj : fileIds) {
            String fileId = fidObj != null ? fidObj.toString() : null;
            if (fileId == null || fileId.isEmpty()) continue;
            try {
                String downloadUrl = zsxqFileService.getFileDownloadUrl(fileId, config);
                if (downloadUrl == null || downloadUrl.isBlank()) continue;
                String localUrl = contentAssetService.downloadFileAndSave(downloadUrl, null);
                if (localUrl != null) {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("file_id", fileId);
                    entry.put("local_url", localUrl);
                    result.add(entry);
                }
            } catch (Exception e) {
                log.warn("下载知识星球文件失败: fileId={}", fileId, e);
            }
        }
        return result;
    }

    /**
     * 解析平台配置 JSON 字符串为 Map
     */
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(configJson, 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            log.warn("解析平台配置失败，使用空配置: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 将平台实体上的 apiBaseUrl 等合并到 config，供需要自定义 API 地址的适配器使用（如 TimeStore）
     */
    private Map<String, Object> mergePlatformConfig(Platform platform, Map<String, Object> config) {
        if (config == null) {
            config = new HashMap<>();
        } else {
            config = new HashMap<>(config);
        }
        if (platform.getApiBaseUrl() != null && !platform.getApiBaseUrl().isEmpty()
            && !config.containsKey("apiBaseUrl")) {
            config.put("apiBaseUrl", platform.getApiBaseUrl());
        }
        return config;
    }
    
    /**
     * 异步拉取早期失败时更新任务状态，便于前端展示失败原因
     */
    private void markTaskFailed(UUID taskId, String message) {
        if (taskId == null || message == null) return;
        try {
            fetchTaskRepository.findById(taskId).ifPresent(t -> {
                t.setStatus(FetchTask.TaskStatus.FAILED);
                t.setErrorMessage(message);
                t.setCompletedAt(LocalDateTime.now());
                fetchTaskRepository.save(t);
            });
        } catch (Exception e) {
            log.warn("更新任务失败状态异常: taskId={}", taskId, e);
        }
    }
    
    /** 判断该内容是否已存在（按 contentId+url+publishedAt 的 hash） */
    private boolean contentExists(PlatformContent content) {
        return contentRepository.existsByHash(generateContentHash(content));
    }

    /**
     * 生成内容哈希
     */
    private String generateContentHash(PlatformContent content) {
        try {
            LocalDateTime pubAt = content.getPublishedAt() != null ? content.getPublishedAt() : LocalDateTime.now();
            String data = content.getContentId() + "|" + content.getUrl() + "|" + pubAt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("生成哈希失败", e);
            return content.getContentId();
        }
    }
    
    /**
     * 转换内容类型
     */
    private Content.ContentType convertContentType(PlatformContent.ContentType type) {
        if (type == null) {
            return Content.ContentType.TEXT;
        }
        return switch (type) {
            case TEXT -> Content.ContentType.TEXT;
            case IMAGE -> Content.ContentType.IMAGE;
            case VIDEO -> Content.ContentType.VIDEO;
            case LINK -> Content.ContentType.LINK;
        };
    }
}

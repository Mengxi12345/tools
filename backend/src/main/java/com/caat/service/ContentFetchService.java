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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final FetchTaskProgressUpdater fetchTaskProgressUpdater;
    private final ScheduleService scheduleService;


    /** 用于单条保存时开启新事务，避免一条失败导致整批回滚（setter 注入打破循环依赖） */
    private ContentFetchService self;

    @Lazy
    @Autowired
    public void setSelf(ContentFetchService self) {
        this.self = self;
    }
    
    @Async
    @Transactional
    public void fetchUserContentAsync(UUID userId, LocalDateTime startTime, LocalDateTime endTime, UUID taskId) {
        TrackedUser user = trackedUserRepository.findById(userId).orElse(null);
        if (user == null) {
            markTaskFailed(taskId, "用户不存在");
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        FetchTask task = getOrCreateTask(user, startTime, endTime, taskId);
        UUID effectiveTaskId = task.getId();
        try {
            fetchTaskProgressUpdater.updateStatusRunning(effectiveTaskId, LocalDateTime.now());
            boolean fullFetchNoDate = (startTime == null && endTime == null);
            var timeRange = resolveTimeRange(userId, startTime, endTime, fullFetchNoDate);
            fetchTaskProgressUpdater.updateStartEndTime(effectiveTaskId, timeRange.start(), timeRange.end());

            PlatformAdapter adapter = adapterFactory.getAdapter(user.getPlatform().getType());
            Map<String, Object> config = PlatformConfigUtil.mergePlatformConfig(
                user.getPlatform(),
                PlatformConfigUtil.parseConfig(objectMapper, user.getPlatform().getConfig())
            );

            String platformType = user.getPlatform().getType().toUpperCase();

            int totalSaved = switch (platformType) {
                case "ZSXQ" -> runZsxqTwoPhaseFetch(adapter, config, user, task, userId);
                default -> runGenericPagination(adapter, config, user, task, timeRange.start(), timeRange.end(), 100, userId);
            };

            fetchTaskProgressUpdater.updateCompleted(effectiveTaskId, LocalDateTime.now(), totalSaved, totalSaved);
            user.setLastFetchedAt(LocalDateTime.now());
            trackedUserRepository.save(user);
            log.info("拉取内容完成: userId={}, savedCount={}", userId, totalSaved);
        } catch (BusinessException e) {
            log.error("拉取内容失败（业务异常）: userId={}", userId, e);
            fetchTaskProgressUpdater.updateFailed(effectiveTaskId, LocalDateTime.now(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("拉取内容失败: userId={}", userId, e);
            fetchTaskProgressUpdater.updateFailed(effectiveTaskId, LocalDateTime.now(), e.getMessage());
        }
    }

    private FetchTask getOrCreateTask(TrackedUser user, LocalDateTime startTime, LocalDateTime endTime, UUID taskId) {
        if (taskId != null) {
            return fetchTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FETCH_TASK_NOT_FOUND));
        }
        FetchTask task = new FetchTask();
        task.setUser(user);
        task.setTaskType(FetchTask.TaskType.MANUAL);
        task.setStartTime(startTime);
        task.setEndTime(endTime != null ? endTime : LocalDateTime.now());
        return fetchTaskRepository.save(task);
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {}

    private TimeRange resolveTimeRange(UUID userId, LocalDateTime startTime, LocalDateTime endTime, boolean fullFetchNoDate) {
        if (fullFetchNoDate) {
            return new TimeRange(null, null);
        }
        LocalDateTime effectiveStart = startTime != null ? startTime
            : contentRepository.findMaxPublishedAtByUserId(userId).orElse(null);
        return new TimeRange(effectiveStart, endTime);
    }

    /** 通用分页拉取：支持完整拉取时的两阶段（先向前补历史，再拉最新） */
    private int runGenericPagination(PlatformAdapter adapter, Map<String, Object> config, TrackedUser user,
                                    FetchTask task, LocalDateTime effectiveStart, LocalDateTime effectiveEnd,
                                    int limit, UUID userId) throws Exception {
        boolean fullFetchNoDate = (effectiveStart == null && effectiveEnd == null);
        Long existingCount = fullFetchNoDate ? contentRepository.countByUserId(userId) : null;
        boolean hadExistingContent = (existingCount != null && existingCount > 0);
        String cursor = hadExistingContent && fullFetchNoDate
            ? String.valueOf(Math.max(1, (existingCount / 100) + 1))
            : null;

        int totalSaved = runPaginationPhase(adapter, config, user, task, effectiveStart, effectiveEnd, cursor, limit, 0, userId);
        if (fullFetchNoDate && hadExistingContent) {
            LocalDateTime maxPub = contentRepository.findMaxPublishedAtByUserId(userId).orElse(null);
            if (maxPub != null) {
                totalSaved += runPaginationPhase(adapter, config, user, task, maxPub, LocalDateTime.now(), null, limit, totalSaved, userId);
            }
        }
        return totalSaved;
    }
    
    /**
     * 知识星球刷新拉取（仅 ZSXQ 使用）：
     * 一、用户没有文章：第一页不带 end_time 拉取 20 条全部保存；后续页用本页「时间最久的一篇」的 create_time 作为 end_time（adapter 的 nextCursor）拉取 20 条全部保存；重复直到某次请求返回没有文章为止。
     * 二、用户已有文章：第一页不带 end_time 拉取 20 条，按条顺序处理，若某条已存在（contentId/hash）则只保存本页中该条之前的新文章并停止；若本页无重复则用本页第 20 条（时间最久）的 create_time 作为 end_time 拉取下一页，同样按条判断，一旦遇到重复则保存本页此前新文章并停止；重复直到某一页出现重复为止。
     */
    private int runZsxqTwoPhaseFetch(PlatformAdapter adapter, Map<String, Object> config, TrackedUser user,
                                    FetchTask task, UUID userId) throws Exception {
        long existingCount = contentRepository.countByUserId(userId);
        boolean hasExistingContent = (existingCount > 0);

        final int limit = 20;
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

            fetchTaskProgressUpdater.updateProgress(task.getId(), result.isHasMore() ? 99 : 100, savedInPhase, savedInPhase);

            if (stopDueToDuplicate || !result.isHasMore()) break;
            cursor = result.getNextCursor();
        }

        fetchTaskProgressUpdater.updateProgress(task.getId(), 100, savedInPhase, savedInPhase);
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
            int totalSoFar = totalSavedOffset + savedInPhase;
            int progress = result.isHasMore() ? Math.min(99, totalSoFar > 0 ? 99 : 0) : 100;
            fetchTaskProgressUpdater.updateProgress(task.getId(), progress, totalSoFar, totalSoFar);
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
        // 1. 生成内容哈希并做幂等校验
        String hash = generateContentHash(platformContent);
        if (contentRepository.existsByHash(hash)) {
            log.info("[保存排查] 内容已存在，跳过: contentId={}, hash={}", platformContent.getContentId(), hash);
            return contentRepository.findByHash(hash).orElse(null);
        }

        // 2. 构建基础 Content 实体（不含平台特定资产与元数据）
        Content content = new Content();
        content.setPlatform(platform);
        content.setUser(user);
        content.setContentId(platformContent.getContentId());
        content.setTitle(platformContent.getTitle());
        content.setBody(platformContent.getBody());
        content.setUrl(platformContent.getUrl());
        content.setContentType(convertContentType(platformContent.getContentType()));

        // 初始媒体与元数据
        List<String> mediaUrls = platformContent.getMediaUrls();
        Map<String, Object> metadataMap = platformContent.getMetadata() != null
            ? new HashMap<>(platformContent.getMetadata()) : new HashMap<>();

        // 3. 平台特定资产处理（图片下载、附件下载等）
        applyPlatformAssetProcessor(platformContent, platform, user, content, metadataMap);

        // 如果策略未设置 mediaUrls，则回退到原始平台数据
        if (content.getMediaUrls() == null) {
            content.setMediaUrls(mediaUrls != null ? mediaUrls : platformContent.getMediaUrls());
        }

        // 4. 发布时间与 DB 约束兜底
        LocalDateTime publishedAt = platformContent.getPublishedAt() != null
            ? platformContent.getPublishedAt()
            : LocalDateTime.now();
        content.setPublishedAt(publishedAt);
        if (content.getPublishedAt() == null) {
            content.setPublishedAt(LocalDateTime.now());
        }

        // 5. 保存元数据为 JSON（含 talk 与 downloaded_file_urls）
        try {
            if (!metadataMap.isEmpty()) {
                content.setMetadata(objectMapper.writeValueAsString(metadataMap));
            }
        } catch (Exception e) {
            log.warn("保存元数据失败", e);
        }

        // 6. 其他通用字段与落库
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

        // 7. 触发通知规则（如 QQ 群推送）：异步避免阻塞拉取
        try {
            notificationService.checkAndNotify(saved);
        } catch (Exception e) {
            log.warn("通知规则检查或发送失败: contentId={}", saved.getId(), e);
        }
        return saved;
    }

    private void applyPlatformAssetProcessor(PlatformContent platformContent,
                                             Platform platform,
                                             TrackedUser user,
                                             Content content,
                                             Map<String, Object> metadataMap) {
        // 关闭附件下载时，直接保留平台原始 URL，不做任何本地下载与替换
        if (!scheduleService.isContentAssetDownloadEnabled()) {
            return;
        }
        if (platform == null || platform.getType() == null) return;
        String type = platform.getType().toUpperCase();
        try {
            switch (type) {
                case "ZSXQ" -> processZsxqAssets(platformContent, platform, user, content, metadataMap);
                case "TIMESTORE" -> processTimestoreAssets(platformContent, platform, user, content, metadataMap);
                default -> { /* 无平台特定处理 */ }
            }
        } catch (Exception e) {
            log.warn("平台资产处理失败，将使用原始数据: platformType={}, contentId={}, error={}",
                platform.getType(), platformContent.getContentId(), e.getMessage());
        }
    }

    /**
     * 知识星球：下载图片与附件到本地，并更新 mediaUrls 与元数据。
     */
    private void processZsxqAssets(PlatformContent platformContent,
                                   Platform platform,
                                   TrackedUser user,
                                   Content content,
                                   Map<String, Object> metadataMap) {
        List<String> mediaUrls = platformContent.getMediaUrls();
        mediaUrls = downloadImagesToLocal(mediaUrls);
        content.setMediaUrls(mediaUrls);

        List<Map<String, String>> downloadedFiles = downloadZsxqFilesToLocal(platform, metadataMap);
        if (!downloadedFiles.isEmpty()) {
            metadataMap.put("downloaded_file_urls", downloadedFiles);
        }
    }

    /**
     * TimeStore：下载正文与媒体中的图片到本地，并替换 body 与 mediaUrls。
     */
    private void processTimestoreAssets(PlatformContent platformContent,
                                        Platform platform,
                                        TrackedUser user,
                                        Content content,
                                        Map<String, Object> metadataMap) {
        List<String> mediaUrls = platformContent.getMediaUrls();
        Set<String> urlsToDownload = new LinkedHashSet<>();
        if (mediaUrls != null) {
            urlsToDownload.addAll(mediaUrls);
        }
        String body = platformContent.getBody();
        if (body != null && !body.isEmpty()) {
            urlsToDownload.addAll(extractImgUrlsFromHtml(body));
        }
        Map<String, String> remoteToLocal = downloadImagesToLocalMap(new ArrayList<>(urlsToDownload));
        if (remoteToLocal.isEmpty()) {
            return;
        }

        if (mediaUrls != null) {
            List<String> newMediaUrls = new ArrayList<>();
            for (String u : mediaUrls) {
                newMediaUrls.add(remoteToLocal.getOrDefault(u, u));
            }
            content.setMediaUrls(newMediaUrls);
        }
        if (body != null && !body.isEmpty()) {
            content.setBody(replaceRemoteImagesInBody(body, remoteToLocal));
        }
    }
    
    /**
     * 将图片 URL 下载到本地，返回远程 URL -> 本地 URL 映射（失败则保留原 URL）
     */
    private Map<String, String> downloadImagesToLocalMap(List<String> urls) {
        Map<String, String> remoteToLocal = new HashMap<>();
        if (urls == null || urls.isEmpty()) return remoteToLocal;
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            String u = url.trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) continue;
            try {
                String localUrl = contentAssetService.downloadImageAndSave(u);
                if (localUrl != null) remoteToLocal.put(u, localUrl);
            } catch (Exception e) {
                log.warn("下载图片到本地失败，保留原 URL: url={}, error={}", u, e.getMessage());
            }
        }
        return remoteToLocal;
    }

    /**
     * 知识星球：将图片 URL 下载到本地，返回本地 URL 列表（失败则保留原 URL）
     */
    private List<String> downloadImagesToLocal(List<String> urls) {
        if (urls == null || urls.isEmpty()) return urls;
        Map<String, String> map = downloadImagesToLocalMap(urls);
        List<String> local = new ArrayList<>();
        for (String u : urls) {
            local.add(map.getOrDefault(u, u));
        }
        return local;
    }

    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /** 从 HTML 中提取 img src 的 URL 列表 */
    private static List<String> extractImgUrlsFromHtml(String html) {
        List<String> urls = new ArrayList<>();
        if (html == null || html.isEmpty()) return urls;
        Matcher m = IMG_SRC_PATTERN.matcher(html);
        while (m.find()) {
            String src = m.group(1).trim();
            if (!src.isEmpty() && (src.startsWith("http://") || src.startsWith("https://"))) {
                urls.add(src);
            }
        }
        return urls;
    }

    /**
     * 将 body 中的远程图片 URL 替换为本地 URL
     */
    private String replaceRemoteImagesInBody(String body, Map<String, String> remoteToLocal) {
        if (body == null || body.isEmpty() || remoteToLocal == null || remoteToLocal.isEmpty()) return body;
        String result = body;
        for (Map.Entry<String, String> e : remoteToLocal.entrySet()) {
            String remote = e.getKey();
            String local = e.getValue();
            if (remote == null || local == null) continue;
            result = result.replace(remote, local);
        }
        return result;
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
        Map<String, Object> config = PlatformConfigUtil.mergePlatformConfig(
            platform,
            PlatformConfigUtil.parseConfig(objectMapper, platform.getConfig())
        );
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

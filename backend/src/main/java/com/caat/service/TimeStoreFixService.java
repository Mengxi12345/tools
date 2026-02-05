package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.PlatformContent;
import com.caat.entity.Content;
import com.caat.entity.Platform;
import com.caat.repository.ContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TimeStore 相关修复逻辑（图片修复、加密文章修复）。
 *
 * <p>注意：仅封装 TimeStore 平台特定的修复行为，避免泄漏到通用 {@link ContentService} 中。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeStoreFixService {

    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE);

    private static final String ENCRYPTED_MARKER = "......";

    private final ContentRepository contentRepository;
    private final ContentAssetService contentAssetService;
    private final AdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor timestoreFixExecutor;

    /**
     * 修复 TimeStore 已保存文章中的外部图片：下载到本地并更新数据库中的地址。
     *
     * @return 修复的文章数量
     */
    @Transactional
    public int fixTimestoreImages() {
        log.info("TimeStore 图片修复：开始");
        List<Content> contents = contentRepository.findByPlatformTypeWithPlatformAndUser("TIMESTORE");
        if (contents == null || contents.isEmpty()) {
            log.info("TimeStore 图片修复：无 TimeStore 内容，跳过");
            return 0;
        }
        log.info("TimeStore 图片修复：共 {} 篇文章待检查", contents.size());
        int fixedCount = 0;
        int processed = 0;
        for (Content c : contents) {
            processed++;
            try {
                boolean updated = fixContentImages(c);
                if (updated) {
                    fixedCount++;
                    log.info("TimeStore 图片修复：第 {}/{} 篇已更新 contentId={}", processed, contents.size(), c.getContentId());
                }
            } catch (Exception e) {
                log.warn("TimeStore 图片修复失败: 第 {}/{} 篇 contentId={}, id={}, error={}", processed, contents.size(), c.getContentId(), c.getId(), e.getMessage());
            }
        }
        log.info("TimeStore 图片修复完成: 共 {} 篇，成功修复 {} 篇", contents.size(), fixedCount);
        return fixedCount;
    }

    private boolean fixContentImages(Content content) {
        Set<String> urlsToDownload = new LinkedHashSet<>();
        List<String> mediaUrls = content.getMediaUrls();
        if (mediaUrls != null) {
            for (String u : mediaUrls) {
                if (isExternalUrl(u)) urlsToDownload.add(u.trim());
            }
        }
        String body = content.getBody();
        if (body != null && !body.isEmpty()) {
            urlsToDownload.addAll(extractImgUrlsFromHtml(body));
        }
        if (urlsToDownload.isEmpty()) return false;

        log.debug("TimeStore 图片修复：contentId={} 待下载 {} 张图片", content.getContentId(), urlsToDownload.size());
        Map<String, String> remoteToLocal = new HashMap<>();
        for (String u : urlsToDownload) {
            try {
                String localUrl = contentAssetService.downloadImageAndSave(u);
                if (localUrl != null) {
                    remoteToLocal.put(u, localUrl);
                    log.debug("TimeStore 图片修复：下载成功 {} -> {}", u, localUrl);
                }
            } catch (Exception e) {
                log.warn("TimeStore 图片修复：下载失败，保留原 URL: url={}, error={}", u, e.getMessage());
            }
        }
        if (remoteToLocal.isEmpty()) return false;

        boolean updated = false;
        if (mediaUrls != null) {
            List<String> newMediaUrls = new ArrayList<>();
            for (String u : mediaUrls) {
                newMediaUrls.add(remoteToLocal.getOrDefault(u, u));
            }
            content.setMediaUrls(newMediaUrls);
            updated = true;
        }
        if (body != null && !body.isEmpty()) {
            String newBody = replaceRemoteImagesInBody(body, remoteToLocal);
            if (!newBody.equals(body)) {
                content.setBody(newBody);
                updated = true;
            }
        }
        if (updated) {
            contentRepository.save(content);
        }
        return updated;
    }

    private static boolean isExternalUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String u = url.trim();
        return (u.startsWith("http://") || u.startsWith("https://")) && !u.startsWith("/api/v1/uploads/");
    }

    private static List<String> extractImgUrlsFromHtml(String html) {
        List<String> urls = new ArrayList<>();
        if (html == null || html.isEmpty()) return urls;
        Matcher m = IMG_SRC_PATTERN.matcher(html);
        while (m.find()) {
            String src = m.group(1).trim();
            if (!src.isEmpty() && isExternalUrl(src)) urls.add(src);
        }
        return urls;
    }

    private static String replaceRemoteImagesInBody(String body, Map<String, String> remoteToLocal) {
        if (body == null || body.isEmpty() || remoteToLocal == null || remoteToLocal.isEmpty()) return body;
        String result = body;
        for (Map.Entry<String, String> e : remoteToLocal.entrySet()) {
            String remote = e.getKey();
            String local = e.getValue();
            if (remote != null && local != null) result = result.replace(remote, local);
        }
        return result;
    }

    /**
     * 修复 TimeStore 加密文章
     * 扫描所有 TimeStore 文章，title 包含 ENCRYPTED_MARKER 的视为加密文章，并发处理：以 URL 请求最新内容并更新数据库。
     *
     * @return 成功修复的文章数量
     */
    public int fixTimestoreEncryptedArticles() {
        log.info("TimeStore 加密文章修复：开始扫描");
        List<Content> allContents = contentRepository.findByPlatformTypeWithPlatformAndUser("TIMESTORE");
        if (allContents == null || allContents.isEmpty()) {
            log.info("TimeStore 加密文章修复：无 TimeStore 内容");
            return 0;
        }
        List<Content> encrypted = allContents.stream()
            .filter(c -> {
                String title = c.getTitle();
                if (title == null || title.isEmpty()) return false;
                // 检查标题最后几位是否包含加密标记
                int checkLength = Math.min(20, title.length());
                String lastPart = title.substring(title.length() - checkLength);
                return lastPart.contains(ENCRYPTED_MARKER);
            })
            .collect(Collectors.toList());
        if (encrypted.isEmpty()) {
            log.info("TimeStore 加密文章修复：未发现加密文章（title 含省略号）");
            return 0;
        }
        log.info("TimeStore 加密文章修复：共 {} 篇，发现 {} 篇加密，开始并发修复", allContents.size(), encrypted.size());

        Map<UUID, Map<String, Object>> configMap = new HashMap<>();
        PlatformAdapter adapter;
        try {
            adapter = adapterFactory.getAdapter("TIMESTORE");
        } catch (Exception e) {
            log.error("TimeStore 加密文章修复：获取适配器失败", e);
            return 0;
        }

        for (Content c : encrypted) {
            Platform p = c.getPlatform();
            if (p == null) continue;
            if (!configMap.containsKey(p.getId())) {
                try {
                    configMap.put(p.getId(), objectMapper.readValue(p.getConfig(), Map.class));
                } catch (Exception e) {
                    log.warn("TimeStore 加密文章修复：解析配置失败 platformId={}", p.getId(), e);
                }
            }
        }

        AtomicInteger fixed = new AtomicInteger(0);
        AtomicInteger done = new AtomicInteger(0);
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (Content c : encrypted) {
            Platform p = c.getPlatform();
            Map<String, Object> cfg = p != null ? configMap.get(p.getId()) : null;
            if (cfg == null) continue;
            Content content = c;
            Platform platform = p;
            Map<String, Object> config = cfg;
            tasks.add(CompletableFuture.runAsync(() -> {
                int n = done.incrementAndGet();
                log.info("TimeStore 加密文章修复：处理 {}/{} id={} url={}", n, encrypted.size(), content.getId(), content.getUrl());
                if (doFixOneEncryptedArticle(content, adapter, config, platform)) {
                    fixed.incrementAndGet();
                    log.info("TimeStore 加密文章修复：已修复 {}/{} contentId={}", n, encrypted.size(), content.getContentId());
                } else {
                    log.warn("TimeStore 加密文章修复：失败 {}/{} contentId={}", n, encrypted.size(), content.getContentId());
                }
            }, timestoreFixExecutor));
        }
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        int count = fixed.get();
        log.info("TimeStore 加密文章修复完成：共 {} 篇加密，成功修复 {} 篇", encrypted.size(), count);
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean doFixOneEncryptedArticle(Content content, PlatformAdapter adapter,
                                             Map<String, Object> config, Platform platform) {
        String url = content.getUrl();
        if (url == null || url.isBlank()) {
            log.warn("TimeStore 加密文章修复：URL 为空 id={}", content.getId());
            return false;
        }
        java.util.Optional<PlatformContent> fetched = adapter.fetchContentByUrl(url, config);
        if (fetched.isEmpty()) {
            log.warn("TimeStore 加密文章修复：拉取失败 url={}", url);
            return false;
        }
        PlatformContent fresh = fetched.get();
        String title = fresh.getTitle();
        if (title != null && !title.isEmpty()) {
            // 检查标题最后几位是否包含加密标记
            int checkLength = Math.min(20, title.length());
            String lastPart = title.substring(title.length() - checkLength);
            if (lastPart.contains(ENCRYPTED_MARKER)) {
                log.warn("TimeStore 加密文章修复：拉取结果仍加密 contentId={}", fresh.getContentId());
                return false;
            }
        }

        content.setContentId(fresh.getContentId());
        content.setTitle(fresh.getTitle());
        content.setBody(fresh.getBody());
        content.setUrl(fresh.getUrl() != null ? fresh.getUrl() : url);
        if (fresh.getMetadata() != null) {
            try {
                content.setMetadata(objectMapper.writeValueAsString(fresh.getMetadata()));
            } catch (Exception e) {
                log.warn("TimeStore 加密文章修复：序列化元数据失败", e);
            }
        }

        List<String> mediaUrls = fresh.getMediaUrls();
        String body = fresh.getBody();
        Set<String> imgUrls = new LinkedHashSet<>();
        if (mediaUrls != null) imgUrls.addAll(mediaUrls);
        if (body != null && !body.isEmpty()) {
            Matcher m = IMG_SRC_PATTERN.matcher(body);
            while (m.find()) {
                String src = m.group(1).trim();
                if (!src.isEmpty() && (src.startsWith("http://") || src.startsWith("https://"))) {
                    imgUrls.add(src);
                }
            }
        }
        Map<String, String> remoteToLocal = new HashMap<>();
        for (String u : imgUrls) {
            if (u == null || u.isBlank()) continue;
            if (!u.startsWith("http://") && !u.startsWith("https://")) continue;
            try {
                String local = contentAssetService.downloadImageAndSave(u);
                if (local != null) remoteToLocal.put(u, local);
            } catch (Exception e) {
                log.warn("TimeStore 加密文章修复：下载图片失败 url={}", u, e);
            }
        }
        if (!remoteToLocal.isEmpty()) {
            if (mediaUrls != null) {
                List<String> newMedia = new ArrayList<>();
                for (String u : mediaUrls) {
                    newMedia.add(remoteToLocal.getOrDefault(u, u));
                }
                content.setMediaUrls(newMedia);
            }
            if (body != null && !body.isEmpty()) {
                String replaced = body;
                for (Map.Entry<String, String> e : remoteToLocal.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        replaced = replaced.replace(e.getKey(), e.getValue());
                    }
                }
                content.setBody(replaced);
            }
        } else if (mediaUrls != null) {
            content.setMediaUrls(mediaUrls);
        }

        contentRepository.save(content);
        return true;
    }
}


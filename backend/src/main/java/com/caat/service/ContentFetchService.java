package com.caat.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final ObjectMapper objectMapper;
    
    /**
     * 手动刷新用户内容（异步执行）
     */
    @Async
    @Transactional
    public void fetchUserContentAsync(UUID userId, LocalDateTime startTime, LocalDateTime endTime) {
        TrackedUser user = trackedUserRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        // 创建刷新任务
        FetchTask task = new FetchTask();
        task.setUser(user);
        task.setTaskType(FetchTask.TaskType.MANUAL);
        task.setStartTime(startTime);
        task.setEndTime(endTime != null ? endTime : LocalDateTime.now());
        task.setStatus(FetchTask.TaskStatus.PENDING);
        task = fetchTaskRepository.save(task);
        
        try {
            task.setStatus(FetchTask.TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            fetchTaskRepository.save(task);
            
            // TODO: 获取平台适配器并拉取内容
            // PlatformAdapter adapter = getAdapter(user.getPlatform());
            // FetchResult result = adapter.getUserContents(...);
            
            // 模拟拉取过程
            log.info("开始拉取用户内容: userId={}, startTime={}, endTime={}", userId, startTime, endTime);
            
            // 更新任务状态
            task.setStatus(FetchTask.TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setProgress(100);
            fetchTaskRepository.save(task);
            
            // 更新用户最后拉取时间
            user.setLastFetchedAt(LocalDateTime.now());
            trackedUserRepository.save(user);
            
        } catch (Exception e) {
            log.error("拉取内容失败", e);
            task.setStatus(FetchTask.TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            fetchTaskRepository.save(task);
        }
    }
    
    /**
     * 保存内容到数据库
     */
    @Transactional
    public Content saveContent(PlatformContent platformContent, TrackedUser user, Platform platform) {
        // 生成内容哈希
        String hash = generateContentHash(platformContent);
        
        // 检查是否已存在
        if (contentRepository.existsByHash(hash)) {
            log.debug("内容已存在，跳过: hash={}", hash);
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
        content.setMediaUrls(platformContent.getMediaUrls());
        content.setPublishedAt(platformContent.getPublishedAt());
        
        // 保存元数据为 JSON
        try {
            if (platformContent.getMetadata() != null) {
                content.setMetadata(objectMapper.writeValueAsString(platformContent.getMetadata()));
            }
        } catch (Exception e) {
            log.warn("保存元数据失败", e);
        }
        
        content.setHash(hash);
        content.setIsRead(false);
        content.setIsFavorite(false);
        
        return contentRepository.save(content);
    }
    
    /**
     * 生成内容哈希
     */
    private String generateContentHash(PlatformContent content) {
        try {
            String data = content.getContentId() + "|" + content.getUrl() + "|" + content.getPublishedAt();
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

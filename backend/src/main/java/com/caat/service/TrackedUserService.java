package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.ContentRepository;
import com.caat.repository.PlatformRepository;
import com.caat.repository.TrackedUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 追踪用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackedUserService {

    private final TrackedUserRepository trackedUserRepository;
    private final PlatformRepository platformRepository;
    private final ContentRepository contentRepository;
    private final AdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    private final UserAvatarService userAvatarService;
    
    /**
     * 获取所有用户（分页），一次性加载 platform，避免 N+1
     */
    public Page<TrackedUser> getAllUsers(Pageable pageable) {
        return trackedUserRepository.findAllWithPlatform(pageable);
    }
    
    /**
     * 根据标签查询用户（分页）
     */
    public Page<TrackedUser> getUsersByTag(String tag, Pageable pageable) {
        return trackedUserRepository.findByTag(tag, pageable);
    }
    
    /**
     * 根据多个标签查询用户（分页）
     */
    public Page<TrackedUser> getUsersByTags(List<String> tags, Pageable pageable) {
        return trackedUserRepository.findByTagsIn(tags, pageable);
    }
    
    /**
     * 获取所有启用的用户
     */
    public List<TrackedUser> getActiveUsers() {
        return trackedUserRepository.findByIsActiveTrue();
    }
    
    /**
     * 根据 ID 获取用户
     */
    public TrackedUser getUserById(UUID id) {
        return trackedUserRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 批量获取各用户的文章总数（用于列表展示）
     * @param userIds 追踪用户 ID 列表
     * @return userId -> 文章数，未拉取过的用户为 0
     */
    public Map<UUID, Long> getContentCountsByUserIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Object[]> rows = contentRepository.countByUserIds(userIds);
        Map<UUID, Long> map = new HashMap<>();
        for (UUID id : userIds) {
            map.put(id, 0L);
        }
        for (Object[] row : rows) {
            if (row.length >= 2 && row[0] instanceof UUID && row[1] instanceof Number) {
                map.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        return map;
    }
    
    /**
     * 根据平台 ID 获取用户列表
     */
    public List<TrackedUser> getUsersByPlatformId(UUID platformId) {
        return trackedUserRepository.findByPlatformId(platformId);
    }
    
    /**
     * 创建用户
     */
    @Transactional
    public TrackedUser createUser(TrackedUser user) {
        Platform platform = platformRepository.findById(user.getPlatform().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PLATFORM_NOT_FOUND));
        
        if (trackedUserRepository.existsByPlatformIdAndUserId(platform.getId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        
        user.setPlatform(platform);
        resolveAvatarUrl(user);
        TrackedUser saved = trackedUserRepository.save(user);
        if ("TIMESTORE".equalsIgnoreCase(platform.getType())) {
            try {
                refreshProfile(saved.getId());
            } catch (Exception e) {
                log.warn("创建 TimeStore 用户后拉取资料失败: userId={}, error={}", saved.getId(), e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 从平台拉取用户资料（头像、简介）并更新到数据库，仅 TimeStore 等支持 profile 的平台有效
     */
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public TrackedUser refreshProfile(UUID id) {
        TrackedUser user = getUserById(id);
        Platform platform = user.getPlatform();
        if (platform == null) {
            throw new BusinessException(ErrorCode.PLATFORM_NOT_FOUND);
        }
        Map<String, Object> config = buildPlatformConfig(platform);
        PlatformAdapter adapter = adapterFactory.getAdapter(platform.getType());
        Optional<Map<String, String>> profile = adapter.getProfileDetail(user.getUserId(), config);
        if (profile.isEmpty()) {
            log.debug("平台 {} 不支持或未拉取到用户资料: userId={}", platform.getType(), user.getUserId());
            return user;
        }
        Map<String, String> p = profile.get();
        if (p.containsKey("userAvatar")) {
            user.setAvatarUrl(p.get("userAvatar"));
        }
        if (p.containsKey("selfIntroduction")) {
            user.setSelfIntroduction(p.get("selfIntroduction"));
        }
        return trackedUserRepository.save(user);
    }

    private Map<String, Object> buildPlatformConfig(Platform platform) {
        Map<String, Object> config = parseConfig(platform.getConfig());
        if (platform.getApiBaseUrl() != null && !platform.getApiBaseUrl().isEmpty() && !config.containsKey("apiBaseUrl")) {
            config.put("apiBaseUrl", platform.getApiBaseUrl());
        }
        return config;
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            log.warn("解析平台配置失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /** 若头像为 http(s) URL 则下载到本地并替换为本地路径 */
    private void resolveAvatarUrl(TrackedUser user) {
        String url = user.getAvatarUrl();
        if (url == null || url.isBlank()) return;
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                user.setAvatarUrl(userAvatarService.downloadAndSave(trimmed));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("下载用户头像失败，保留原 URL: {}", e.getMessage());
            }
        }
    }

    /**
     * 更新用户
     */
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public TrackedUser updateUser(UUID id, TrackedUser user) {
        TrackedUser existing = getUserById(id);
        existing.setDisplayName(user.getDisplayName());
        String avatarUrl = user.getAvatarUrl();
        if (avatarUrl != null) {
            TrackedUser temp = new TrackedUser();
            temp.setAvatarUrl(avatarUrl);
            resolveAvatarUrl(temp);
            existing.setAvatarUrl(temp.getAvatarUrl());
        } else {
            existing.setAvatarUrl(null);
        }
        if (user.getSelfIntroduction() != null) {
            existing.setSelfIntroduction(user.getSelfIntroduction());
        }
        existing.setGroupId(user.getGroupId());
        existing.setTags(user.getTags());
        existing.setIsActive(user.getIsActive());
        return trackedUserRepository.save(existing);
    }
    
    /**
     * 删除用户
     */
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(UUID id) {
        TrackedUser user = getUserById(id);
        trackedUserRepository.delete(user);
    }
    
    /**
     * 启用/禁用用户
     */
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public TrackedUser toggleUserStatus(UUID id, boolean isActive) {
        TrackedUser user = getUserById(id);
        user.setIsActive(isActive);
        return trackedUserRepository.save(user);
    }
}

package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.adapter.PlatformAdapter;
import com.caat.entity.Platform;
import com.caat.repository.PlatformRepository;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformService {
    
    private final PlatformRepository platformRepository;
    private final AdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    private final PlatformAvatarService platformAvatarService;
    
    /**
     * 获取所有平台
     */
    @Cacheable(value = "platforms", key = "'all'")
    public List<Platform> getAllPlatforms() {
        try {
            return platformRepository.findAll();
        } catch (Exception e) {
            log.error("获取平台列表失败", e);
            return List.of();
        }
    }
    
    /**
     * 根据 ID 获取平台
     */
    @Cacheable(value = "platforms", key = "#id")
    public Platform getPlatformById(UUID id) {
        return platformRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLATFORM_NOT_FOUND));
    }
    
    /**
     * 创建平台
     */
    @Transactional
    @CacheEvict(value = "platforms", key = "'all'")
    public Platform createPlatform(Platform platform) {
        if (platformRepository.existsByName(platform.getName())) {
            throw new BusinessException(ErrorCode.PLATFORM_ALREADY_EXISTS);
        }
        resolveAvatarUrl(platform);
        return platformRepository.save(platform);
    }
    
    /**
     * 更新平台
     */
    @Transactional
    @CacheEvict(value = "platforms", allEntries = true)
    public Platform updatePlatform(UUID id, Platform platform) {
        Platform existing = getPlatformById(id);
        if (platform.getName() != null && !platform.getName().isBlank()) {
            String newName = platform.getName().trim();
            if (platformRepository.existsByNameAndIdNot(newName, id)) {
                throw new BusinessException(ErrorCode.PLATFORM_ALREADY_EXISTS);
            }
            existing.setName(newName);
        }
        existing.setType(platform.getType());
        existing.setApiBaseUrl(platform.getApiBaseUrl());
        existing.setAuthType(platform.getAuthType());
        resolveAvatarUrl(platform);
        existing.setAvatarUrl(platform.getAvatarUrl());
        existing.setConfig(platform.getConfig());
        existing.setStatus(platform.getStatus());
        return platformRepository.save(existing);
    }
    
    /**
     * 删除平台
     */
    @Transactional
    @CacheEvict(value = "platforms", allEntries = true)
    public void deletePlatform(UUID id) {
        Platform platform = getPlatformById(id);
        platformRepository.delete(platform);
    }
    
    /**
     * 测试平台连接
     */
    public boolean testConnection(UUID id) {
        Platform platform = getPlatformById(id);
        
        try {
            // 获取对应的适配器
            PlatformAdapter adapter = adapterFactory.getAdapter(platform.getType());
            
            // 解析平台配置，并合并 apiBaseUrl 供适配器使用（如 TimeStore）
            Map<String, Object> config = mergePlatformConfig(platform, parseConfig(platform.getConfig()));
            
            // 调用适配器测试连接
            boolean success = adapter.testConnection(config);
            
            log.info("平台连接测试结果: platform={}, type={}, success={}", 
                platform.getName(), platform.getType(), success);
            
            return success;
        } catch (BusinessException e) {
            log.error("平台连接测试失败: platform={}, type={}", 
                platform.getName(), platform.getType(), e);
            throw e;
        } catch (Exception e) {
            log.error("平台连接测试异常: platform={}, type={}", 
                platform.getName(), platform.getType(), e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, 
                "平台连接测试失败: " + e.getMessage());
        }
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
     * 将平台实体上的 apiBaseUrl 合并到 config，供 TimeStore 等适配器使用
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

    /** 若头像为 http(s) URL 则下载到本地并替换为本地路径 */
    private void resolveAvatarUrl(Platform platform) {
        String url = platform.getAvatarUrl();
        if (url == null || url.isBlank()) return;
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                platform.setAvatarUrl(platformAvatarService.downloadAndSave(trimmed));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("下载平台头像失败，保留原 URL: {}", e.getMessage());
            }
        }
    }
}

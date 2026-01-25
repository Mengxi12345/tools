package com.caat.service;

import com.caat.entity.Platform;
import com.caat.repository.PlatformRepository;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 平台管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformService {
    
    private final PlatformRepository platformRepository;
    
    /**
     * 获取所有平台
     */
    public List<Platform> getAllPlatforms() {
        return platformRepository.findAll();
    }
    
    /**
     * 根据 ID 获取平台
     */
    public Platform getPlatformById(UUID id) {
        return platformRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLATFORM_NOT_FOUND));
    }
    
    /**
     * 创建平台
     */
    @Transactional
    public Platform createPlatform(Platform platform) {
        if (platformRepository.existsByName(platform.getName())) {
            throw new BusinessException(ErrorCode.PLATFORM_ALREADY_EXISTS);
        }
        return platformRepository.save(platform);
    }
    
    /**
     * 更新平台
     */
    @Transactional
    public Platform updatePlatform(UUID id, Platform platform) {
        Platform existing = getPlatformById(id);
        existing.setType(platform.getType());
        existing.setApiBaseUrl(platform.getApiBaseUrl());
        existing.setAuthType(platform.getAuthType());
        existing.setConfig(platform.getConfig());
        existing.setStatus(platform.getStatus());
        return platformRepository.save(existing);
    }
    
    /**
     * 删除平台
     */
    @Transactional
    public void deletePlatform(UUID id) {
        Platform platform = getPlatformById(id);
        platformRepository.delete(platform);
    }
    
    /**
     * 测试平台连接
     */
    public boolean testConnection(UUID id) {
        Platform platform = getPlatformById(id);
        // TODO: 调用对应的适配器测试连接
        log.info("测试平台连接: {}", platform.getName());
        return true;
    }
}

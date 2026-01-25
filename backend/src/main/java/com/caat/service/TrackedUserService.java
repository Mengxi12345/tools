package com.caat.service;

import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.PlatformRepository;
import com.caat.repository.TrackedUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    
    /**
     * 获取所有用户（分页）
     */
    public Page<TrackedUser> getAllUsers(Pageable pageable) {
        return trackedUserRepository.findAll(pageable);
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
        return trackedUserRepository.save(user);
    }
    
    /**
     * 更新用户
     */
    @Transactional
    public TrackedUser updateUser(UUID id, TrackedUser user) {
        TrackedUser existing = getUserById(id);
        existing.setDisplayName(user.getDisplayName());
        existing.setAvatarUrl(user.getAvatarUrl());
        existing.setGroupId(user.getGroupId());
        existing.setTags(user.getTags());
        existing.setPriority(user.getPriority());
        existing.setIsActive(user.getIsActive());
        return trackedUserRepository.save(existing);
    }
    
    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(UUID id) {
        TrackedUser user = getUserById(id);
        trackedUserRepository.delete(user);
    }
    
    /**
     * 启用/禁用用户
     */
    @Transactional
    public TrackedUser toggleUserStatus(UUID id, boolean isActive) {
        TrackedUser user = getUserById(id);
        user.setIsActive(isActive);
        return trackedUserRepository.save(user);
    }
}

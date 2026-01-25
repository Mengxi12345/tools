package com.caat.service;

import com.caat.entity.Content;
import com.caat.repository.ContentRepository;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 内容管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {
    
    private final ContentRepository contentRepository;
    
    /**
     * 获取内容列表（分页、过滤）
     */
    public Page<Content> getContents(Pageable pageable) {
        return contentRepository.findAll(pageable);
    }
    
    /**
     * 根据用户 ID 获取内容列表
     */
    public Page<Content> getContentsByUserId(UUID userId, Pageable pageable) {
        return contentRepository.findByUserId(userId, pageable);
    }
    
    /**
     * 根据平台 ID 获取内容列表
     */
    public Page<Content> getContentsByPlatformId(UUID platformId, Pageable pageable) {
        return contentRepository.findByPlatformId(platformId, pageable);
    }
    
    /**
     * 根据 ID 获取内容详情
     */
    public Content getContentById(UUID id) {
        return contentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
    }
    
    /**
     * 更新内容（标记已读、收藏等）
     */
    @Transactional
    public Content updateContent(UUID id, Content content) {
        Content existing = getContentById(id);
        if (content.getIsRead() != null) {
            existing.setIsRead(content.getIsRead());
        }
        if (content.getIsFavorite() != null) {
            existing.setIsFavorite(content.getIsFavorite());
        }
        if (content.getNotes() != null) {
            existing.setNotes(content.getNotes());
        }
        return contentRepository.save(existing);
    }
    
    /**
     * 删除内容
     */
    @Transactional
    public void deleteContent(UUID id) {
        Content content = getContentById(id);
        contentRepository.delete(content);
    }
    
    /**
     * 获取内容统计
     */
    public ContentStats getContentStats(UUID userId) {
        Long total = contentRepository.countByUserId(userId);
        Long unread = contentRepository.countUnreadByUserId(userId);
        // TODO: 计算收藏数量
        return new ContentStats(total, unread, 0L);
    }
    
    public record ContentStats(Long total, Long unread, Long favorite) {}
}

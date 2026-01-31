package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.ContentDocument;
import com.caat.repository.ContentDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Elasticsearch 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchService {
    
    private final ContentDocumentRepository contentDocumentRepository;
    
    /**
     * 索引内容到 Elasticsearch
     */
    @Transactional
    public void indexContent(Content content) {
        try {
            ContentDocument document = convertToDocument(content);
            contentDocumentRepository.save(document);
            log.debug("内容已索引到 Elasticsearch: contentId={}", content.getId());
        } catch (Exception e) {
            log.error("索引内容到 Elasticsearch 失败: contentId={}", content.getId(), e);
        }
    }
    
    /**
     * 更新 Elasticsearch 中的内容
     */
    @Transactional
    public void updateContent(Content content) {
        indexContent(content); // Elasticsearch 的 save 操作会自动更新
    }
    
    /**
     * 从 Elasticsearch 删除内容
     */
    @Transactional
    public void deleteContent(UUID contentId) {
        try {
            contentDocumentRepository.deleteById(contentId.toString());
            log.debug("内容已从 Elasticsearch 删除: contentId={}", contentId);
        } catch (Exception e) {
            log.error("从 Elasticsearch 删除内容失败: contentId={}", contentId, e);
        }
    }
    
    /**
     * 全文搜索
     */
    public Page<ContentDocument> search(String query, Pageable pageable) {
        return contentDocumentRepository.findByTitleContainingOrBodyContaining(query, query, pageable);
    }
    
    /**
     * 正则表达式搜索
     */
    public Page<ContentDocument> searchByRegex(String regexPattern, Pageable pageable) {
        try {
            return contentDocumentRepository.searchByRegex(regexPattern, pageable);
        } catch (Exception e) {
            log.error("正则表达式搜索失败: pattern={}", regexPattern, e);
            throw new RuntimeException("正则表达式搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 高级搜索（支持多个条件）
     */
    public Page<ContentDocument> advancedSearch(String query, String contentType, Pageable pageable) {
        return contentDocumentRepository.advancedSearch(query, contentType, pageable);
    }
    
    /**
     * 将 Content 实体转换为 ContentDocument
     */
    private ContentDocument convertToDocument(Content content) {
        ContentDocument document = new ContentDocument();
        document.setId(content.getId().toString());
        document.setPlatformId(content.getPlatform().getId().toString());
        document.setUserId(content.getUser().getId().toString());
        document.setTitle(content.getTitle());
        document.setBody(content.getBody());
        document.setUrl(content.getUrl());
        document.setContentType(content.getContentType().name());
        document.setPublishedAt(content.getPublishedAt());
        document.setIsRead(content.getIsRead());
        document.setIsFavorite(content.getIsFavorite());
        document.setCreatedAt(content.getCreatedAt());
        return document;
    }
}

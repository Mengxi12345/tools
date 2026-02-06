package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Elasticsearch 服务接口
 * 启用 ES 时使用 ElasticsearchServiceImpl，禁用时使用 ElasticsearchServiceDisabled（空实现）
 */
public interface ElasticsearchService {

    void indexContent(Content content);

    void updateContent(Content content);

    void deleteContent(UUID contentId);

    Page<ContentDocument> search(String query, Pageable pageable);

    Page<ContentDocument> searchByRegex(String regexPattern, Pageable pageable);

    Page<ContentDocument> advancedSearch(String query, String contentType, Pageable pageable);
}

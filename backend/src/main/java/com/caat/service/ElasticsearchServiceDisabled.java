package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.ContentDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

/**
 * Elasticsearch 空实现（禁用 ES 时生效）
 * 搜索返回空页，索引/更新/删除为 no-op
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "false")
public class ElasticsearchServiceDisabled implements ElasticsearchService {

    @Override
    public void indexContent(Content content) {
        // no-op
    }

    @Override
    public void updateContent(Content content) {
        // no-op
    }

    @Override
    public void deleteContent(UUID contentId) {
        // no-op
    }

    @Override
    public Page<ContentDocument> search(String query, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    @Override
    public Page<ContentDocument> searchByRegex(String regexPattern, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    @Override
    public Page<ContentDocument> advancedSearch(String query, String contentType, Pageable pageable) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }
}

package com.caat.repository;

import com.caat.entity.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.elasticsearch.annotations.Query;

@Repository
public interface ContentDocumentRepository extends ElasticsearchRepository<ContentDocument, String> {
    Page<ContentDocument> findByTitleContainingOrBodyContaining(String title, String body, Pageable pageable);
    
    /**
     * 使用正则表达式搜索
     */
    @Query("{\"bool\": {\"should\": [{\"regexp\": {\"title\": \"?0\"}}, {\"regexp\": {\"body\": \"?0\"}}]}}")
    Page<ContentDocument> searchByRegex(String regexPattern, Pageable pageable);
    
    /**
     * 高级搜索：支持多个条件组合
     */
    @Query("{\"bool\": {\"must\": [{\"match\": {\"title\": \"?0\"}}], \"filter\": [{\"term\": {\"contentType\": \"?1\"}}]}}")
    Page<ContentDocument> advancedSearch(String query, String contentType, Pageable pageable);
}

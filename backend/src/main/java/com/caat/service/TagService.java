package com.caat.service;

import com.caat.entity.Tag;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.TagRepository;
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
import java.util.UUID;

/**
 * 标签管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {
    
    private final TagRepository tagRepository;
    
    /**
     * 获取所有标签
     */
    @Cacheable(value = "tags", key = "'all'")
    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }
    
    /**
     * 分页获取标签
     */
    public Page<Tag> getTags(Pageable pageable) {
        return tagRepository.findAll(pageable);
    }
    
    /**
     * 根据ID获取标签
     */
    public Tag getTagById(UUID id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    
    /**
     * 根据名称获取标签
     */
    public Tag getTagByName(String name) {
        return tagRepository.findByName(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
    
    /**
     * 创建标签
     */
    @Transactional
    @CacheEvict(value = "tags", allEntries = true)
    public Tag createTag(String name, String color, String category) {
        if (tagRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        
        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(color);
        tag.setCategory(category);
        tag.setUsageCount(0);
        
        return tagRepository.save(tag);
    }
    
    /**
     * 更新标签
     */
    @Transactional
    @CacheEvict(value = "tags", allEntries = true)
    public Tag updateTag(UUID id, String name, String color, String category) {
        Tag tag = getTagById(id);
        if (!tag.getName().equals(name) && tagRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        
        tag.setName(name);
        tag.setColor(color);
        tag.setCategory(category);
        
        return tagRepository.save(tag);
    }
    
    /**
     * 删除标签
     */
    @Transactional
    @CacheEvict(value = "tags", allEntries = true)
    public void deleteTag(UUID id) {
        Tag tag = getTagById(id);
        tagRepository.delete(tag);
    }
    
    /**
     * 增加标签使用次数
     */
    @Transactional
    public void incrementUsageCount(UUID id) {
        Tag tag = getTagById(id);
        tag.setUsageCount(tag.getUsageCount() + 1);
        tagRepository.save(tag);
    }
    
    /**
     * 减少标签使用次数
     */
    @Transactional
    public void decrementUsageCount(UUID id) {
        Tag tag = getTagById(id);
        if (tag.getUsageCount() > 0) {
            tag.setUsageCount(tag.getUsageCount() - 1);
            tagRepository.save(tag);
        }
    }
    
    /**
     * 获取热门标签（按使用次数排序）
     */
    @Cacheable(value = "tags", key = "'popular:' + #limit")
    public List<Tag> getPopularTags(int limit) {
        return tagRepository.findTopNByOrderByUsageCountDesc(limit);
    }
    
}

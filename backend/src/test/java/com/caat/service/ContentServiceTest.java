package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.repository.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ContentService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("内容管理服务测试")
class ContentServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private ContentService contentService;

    private Platform testPlatform;
    private TrackedUser testUser;
    private Content testContent;

    @BeforeEach
    void setUp() {
        testPlatform = new Platform();
        testPlatform.setId(UUID.randomUUID());
        testPlatform.setName("GitHub");
        testPlatform.setType("GITHUB");

        testUser = new TrackedUser();
        testUser.setId(UUID.randomUUID());
        testUser.setPlatform(testPlatform);
        testUser.setUsername("testuser");

        testContent = new Content();
        testContent.setId(UUID.randomUUID());
        testContent.setPlatform(testPlatform);
        testContent.setUser(testUser);
        testContent.setContentId("content-123");
        testContent.setTitle("Test Content");
        testContent.setUrl("https://example.com/content");
        testContent.setContentType(Content.ContentType.TEXT);
        testContent.setPublishedAt(LocalDateTime.now());
        testContent.setHash("hash123");
        testContent.setIsRead(false);
        testContent.setIsFavorite(false);
    }

    @Test
    @DisplayName("测试获取内容列表 - 分页")
    void testGetContents() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> contentPage = new PageImpl<>(Arrays.asList(testContent));
        when(contentRepository.findAll(pageable)).thenReturn(contentPage);

        // When
        Page<Content> result = contentService.getContents(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Content", result.getContent().get(0).getTitle());
        verify(contentRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("测试根据ID获取内容 - 成功")
    void testGetContentById_Success() {
        // Given
        UUID contentId = testContent.getId();
        when(contentRepository.findById(contentId)).thenReturn(Optional.of(testContent));

        // When
        Content result = contentService.getContentById(contentId);

        // Then
        assertNotNull(result);
        assertEquals(contentId, result.getId());
        assertEquals("Test Content", result.getTitle());
        verify(contentRepository, times(1)).findById(contentId);
    }

    @Test
    @DisplayName("测试更新内容 - 成功")
    void testUpdateContent_Success() {
        // Given
        UUID contentId = testContent.getId();
        Content updatedContent = new Content();
        updatedContent.setIsRead(true);
        updatedContent.setIsFavorite(true);
        updatedContent.setNotes("Updated notes");

        when(contentRepository.findById(contentId)).thenReturn(Optional.of(testContent));
        when(contentRepository.save(any(Content.class))).thenAnswer(invocation -> {
            Content saved = invocation.getArgument(0);
            saved.setIsRead(updatedContent.getIsRead());
            saved.setIsFavorite(updatedContent.getIsFavorite());
            saved.setNotes(updatedContent.getNotes());
            return saved;
        });

        // When
        Content result = contentService.updateContent(contentId, updatedContent);

        // Then
        assertNotNull(result);
        assertTrue(result.getIsRead());
        assertTrue(result.getIsFavorite());
        assertEquals("Updated notes", result.getNotes());
        verify(contentRepository, times(1)).findById(contentId);
        verify(contentRepository, times(1)).save(any(Content.class));
    }

    @Test
    @DisplayName("测试删除内容 - 成功")
    void testDeleteContent_Success() {
        // Given
        UUID contentId = testContent.getId();
        when(contentRepository.findById(contentId)).thenReturn(Optional.of(testContent));
        doNothing().when(contentRepository).delete(any(Content.class));

        // When
        contentService.deleteContent(contentId);

        // Then
        verify(contentRepository, times(1)).findById(contentId);
        verify(contentRepository, times(1)).delete(any(Content.class));
    }

    @Test
    @DisplayName("测试根据用户ID获取内容")
    void testGetContentsByUserId() {
        // Given
        UUID userId = testUser.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> contentPage = new PageImpl<>(Arrays.asList(testContent));
        when(contentRepository.findByUserId(userId, pageable)).thenReturn(contentPage);

        // When
        Page<Content> result = contentService.getContentsByUserId(userId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(contentRepository, times(1)).findByUserId(userId, pageable);
    }

    @Test
    @DisplayName("测试根据平台ID获取内容")
    void testGetContentsByPlatformId() {
        // Given
        UUID platformId = testPlatform.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> contentPage = new PageImpl<>(Arrays.asList(testContent));
        when(contentRepository.findByPlatformId(platformId, pageable)).thenReturn(contentPage);

        // When
        Page<Content> result = contentService.getContentsByPlatformId(platformId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(contentRepository, times(1)).findByPlatformId(platformId, pageable);
    }
}

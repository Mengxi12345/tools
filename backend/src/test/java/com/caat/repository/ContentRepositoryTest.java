package com.caat.repository;

import com.caat.entity.Content;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ContentRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private ContentRepository contentRepository;
    
    @Autowired
    private PlatformRepository platformRepository;
    
    @Autowired
    private TrackedUserRepository trackedUserRepository;
    
    private Platform testPlatform;
    private TrackedUser testUser;
    private Content testContent;
    
    @BeforeEach
    void setUp() {
        testPlatform = new Platform();
        testPlatform.setName("Test Platform");
        testPlatform.setType("GITHUB");
        testPlatform.setStatus(Platform.PlatformStatus.ACTIVE);
        testPlatform = entityManager.persistAndFlush(testPlatform);
        
        testUser = new TrackedUser();
        testUser.setPlatform(testPlatform);
        testUser.setUsername("testuser");
        testUser.setUserId("testuser123");
        testUser.setIsActive(true);
        testUser = entityManager.persistAndFlush(testUser);
        
        testContent = new Content();
        testContent.setPlatform(testPlatform);
        testContent.setUser(testUser);
        testContent.setContentId("content123");
        testContent.setTitle("Test Content");
        testContent.setUrl("https://example.com/content");
        testContent.setContentType(Content.ContentType.TEXT);
        testContent.setPublishedAt(LocalDateTime.now());
        testContent.setHash("testhash123");
        testContent.setIsRead(false);
        testContent.setIsFavorite(false);
        testContent = entityManager.persistAndFlush(testContent);
    }
    
    @Test
    void testFindByHash() {
        Optional<Content> found = contentRepository.findByHash("testhash123");
        assertTrue(found.isPresent());
        assertEquals("Test Content", found.get().getTitle());
    }
    
    @Test
    void testExistsByHash() {
        assertTrue(contentRepository.existsByHash("testhash123"));
        assertFalse(contentRepository.existsByHash("non-existent"));
    }
    
    @Test
    void testFindByUserId() {
        Page<Content> contents = contentRepository.findByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertTrue(contents.getTotalElements() > 0);
    }
    
    @Test
    void testFindByPlatformId() {
        Page<Content> contents = contentRepository.findByPlatformId(
                testPlatform.getId(), PageRequest.of(0, 10));
        assertTrue(contents.getTotalElements() > 0);
    }
    
    @Test
    void testCountByUserId() {
        Long count = contentRepository.countByUserId(testUser.getId());
        assertTrue(count > 0);
    }
    
    @Test
    void testCountUnreadByUserId() {
        Long count = contentRepository.countUnreadByUserId(testUser.getId());
        assertNotNull(count);
    }
}

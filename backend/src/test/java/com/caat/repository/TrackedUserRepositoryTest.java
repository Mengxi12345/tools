package com.caat.repository;

import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TrackedUserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private TrackedUserRepository trackedUserRepository;
    
    @Autowired
    private PlatformRepository platformRepository;
    
    private Platform testPlatform;
    private TrackedUser testUser;
    
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
    }
    
    @Test
    void testFindByIsActiveTrue() {
        List<TrackedUser> activeUsers = trackedUserRepository.findByIsActiveTrue();
        assertTrue(activeUsers.size() > 0);
        assertTrue(activeUsers.stream().allMatch(TrackedUser::getIsActive));
    }
    
    @Test
    void testFindByPlatformIdAndUserId() {
        Optional<TrackedUser> found = trackedUserRepository.findByPlatformIdAndUserId(
                testPlatform.getId(), "testuser123");
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
    }
    
    @Test
    void testFindByPlatformIdAndUserIdNotFound() {
        Optional<TrackedUser> found = trackedUserRepository.findByPlatformIdAndUserId(
                testPlatform.getId(), "non-existent");
        assertFalse(found.isPresent());
    }
    
    @Test
    void testSave() {
        TrackedUser newUser = new TrackedUser();
        newUser.setPlatform(testPlatform);
        newUser.setUsername("newuser");
        newUser.setUserId("newuser123");
        newUser.setIsActive(true);
        
        TrackedUser saved = trackedUserRepository.save(newUser);
        assertNotNull(saved.getId());
        assertEquals("newuser", saved.getUsername());
    }
}

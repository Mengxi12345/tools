package com.caat.repository;

import com.caat.entity.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PlatformRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private PlatformRepository platformRepository;
    
    private Platform testPlatform;
    
    @BeforeEach
    void setUp() {
        testPlatform = new Platform();
        testPlatform.setName("Test Platform");
        testPlatform.setType("GITHUB");
        testPlatform.setApiBaseUrl("https://api.github.com");
        testPlatform.setStatus(Platform.PlatformStatus.ACTIVE);
        testPlatform = entityManager.persistAndFlush(testPlatform);
    }
    
    @Test
    void testFindByName() {
        Optional<Platform> found = platformRepository.findByName("Test Platform");
        assertTrue(found.isPresent());
        assertEquals("Test Platform", found.get().getName());
    }
    
    @Test
    void testFindByNameNotFound() {
        Optional<Platform> found = platformRepository.findByName("Non-existent");
        assertFalse(found.isPresent());
    }
    
    @Test
    void testExistsByName() {
        assertTrue(platformRepository.existsByName("Test Platform"));
        assertFalse(platformRepository.existsByName("Non-existent"));
    }
    
    @Test
    void testSave() {
        Platform newPlatform = new Platform();
        newPlatform.setName("New Platform");
        newPlatform.setType("TWITTER");
        newPlatform.setStatus(Platform.PlatformStatus.ACTIVE);
        
        Platform saved = platformRepository.save(newPlatform);
        assertNotNull(saved.getId());
        assertEquals("New Platform", saved.getName());
    }
    
    @Test
    void testDelete() {
        platformRepository.delete(testPlatform);
        entityManager.flush();
        
        Optional<Platform> found = platformRepository.findById(testPlatform.getId());
        assertFalse(found.isPresent());
    }
}

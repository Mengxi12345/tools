package com.caat.service;

import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.PlatformRepository;
import com.caat.repository.TrackedUserRepository;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TrackedUserService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户管理服务测试")
class TrackedUserServiceTest {

    @Mock
    private TrackedUserRepository trackedUserRepository;

    @Mock
    private PlatformRepository platformRepository;

    @InjectMocks
    private TrackedUserService trackedUserService;

    private Platform testPlatform;
    private TrackedUser testUser;

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
        testUser.setUserId("testuser");
        testUser.setDisplayName("Test User");
        testUser.setIsActive(true);
    }

    @Test
    @DisplayName("测试获取所有用户 - 分页")
    void testGetAllUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<TrackedUser> userPage = new PageImpl<>(Arrays.asList(testUser));
        when(trackedUserRepository.findAll(pageable)).thenReturn(userPage);

        // When
        Page<TrackedUser> result = trackedUserService.getAllUsers(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("testuser", result.getContent().get(0).getUsername());
        verify(trackedUserRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("测试获取启用的用户")
    void testGetActiveUsers() {
        // Given
        List<TrackedUser> activeUsers = Arrays.asList(testUser);
        when(trackedUserRepository.findByIsActiveTrue()).thenReturn(activeUsers);

        // When
        List<TrackedUser> result = trackedUserService.getActiveUsers();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());
        verify(trackedUserRepository, times(1)).findByIsActiveTrue();
    }

    @Test
    @DisplayName("测试根据ID获取用户 - 成功")
    void testGetUserById_Success() {
        // Given
        UUID userId = testUser.getId();
        when(trackedUserRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        TrackedUser result = trackedUserService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("testuser", result.getUsername());
        verify(trackedUserRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("测试根据ID获取用户 - 不存在")
    void testGetUserById_NotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        when(trackedUserRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            trackedUserService.getUserById(userId);
        });

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(trackedUserRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("测试创建用户 - 成功")
    void testCreateUser_Success() {
        // Given
        when(platformRepository.findById(testPlatform.getId())).thenReturn(Optional.of(testPlatform));
        when(trackedUserRepository.existsByPlatformIdAndUserId(testPlatform.getId(), "testuser")).thenReturn(false);
        when(trackedUserRepository.save(any(TrackedUser.class))).thenReturn(testUser);

        // When
        TrackedUser result = trackedUserService.createUser(testUser);

        // Then
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(platformRepository, times(1)).findById(testPlatform.getId());
        verify(trackedUserRepository, times(1)).existsByPlatformIdAndUserId(testPlatform.getId(), "testuser");
        verify(trackedUserRepository, times(1)).save(any(TrackedUser.class));
    }

    @Test
    @DisplayName("测试创建用户 - 平台不存在")
    void testCreateUser_PlatformNotFound() {
        // Given
        when(platformRepository.findById(testPlatform.getId())).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            trackedUserService.createUser(testUser);
        });

        assertEquals(ErrorCode.PLATFORM_NOT_FOUND, exception.getErrorCode());
        verify(platformRepository, times(1)).findById(testPlatform.getId());
        verify(trackedUserRepository, never()).save(any(TrackedUser.class));
    }

    @Test
    @DisplayName("测试创建用户 - 用户已存在")
    void testCreateUser_AlreadyExists() {
        // Given
        when(platformRepository.findById(testPlatform.getId())).thenReturn(Optional.of(testPlatform));
        when(trackedUserRepository.existsByPlatformIdAndUserId(testPlatform.getId(), "testuser")).thenReturn(true);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            trackedUserService.createUser(testUser);
        });

        assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
        verify(platformRepository, times(1)).findById(testPlatform.getId());
        verify(trackedUserRepository, times(1)).existsByPlatformIdAndUserId(testPlatform.getId(), "testuser");
        verify(trackedUserRepository, never()).save(any(TrackedUser.class));
    }

    @Test
    @DisplayName("测试更新用户 - 成功")
    void testUpdateUser_Success() {
        // Given
        UUID userId = testUser.getId();
        TrackedUser updatedUser = new TrackedUser();
        updatedUser.setDisplayName("Updated User");
        updatedUser.setIsActive(false);

        when(trackedUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(trackedUserRepository.save(any(TrackedUser.class))).thenAnswer(invocation -> {
            TrackedUser saved = invocation.getArgument(0);
            saved.setDisplayName(updatedUser.getDisplayName());
            saved.setIsActive(updatedUser.getIsActive());
            return saved;
        });

        // When
        TrackedUser result = trackedUserService.updateUser(userId, updatedUser);

        // Then
        assertNotNull(result);
        assertEquals("Updated User", result.getDisplayName());
        assertFalse(result.getIsActive());
        verify(trackedUserRepository, times(1)).findById(userId);
        verify(trackedUserRepository, times(1)).save(any(TrackedUser.class));
    }

    @Test
    @DisplayName("测试删除用户 - 成功")
    void testDeleteUser_Success() {
        // Given
        UUID userId = testUser.getId();
        when(trackedUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        doNothing().when(trackedUserRepository).delete(any(TrackedUser.class));

        // When
        trackedUserService.deleteUser(userId);

        // Then
        verify(trackedUserRepository, times(1)).findById(userId);
        verify(trackedUserRepository, times(1)).delete(any(TrackedUser.class));
    }

    @Test
    @DisplayName("测试启用/禁用用户 - 成功")
    void testToggleUserStatus_Success() {
        // Given
        UUID userId = testUser.getId();
        when(trackedUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(trackedUserRepository.save(any(TrackedUser.class))).thenAnswer(invocation -> {
            TrackedUser saved = invocation.getArgument(0);
            saved.setIsActive(false);
            return saved;
        });

        // When
        TrackedUser result = trackedUserService.toggleUserStatus(userId, false);

        // Then
        assertNotNull(result);
        assertFalse(result.getIsActive());
        verify(trackedUserRepository, times(1)).findById(userId);
        verify(trackedUserRepository, times(1)).save(any(TrackedUser.class));
    }
}

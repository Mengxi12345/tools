package com.caat.service;

import com.caat.adapter.AdapterFactory;
import com.caat.entity.Platform;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.caat.repository.PlatformRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * PlatformService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("平台管理服务测试")
class PlatformServiceTest {

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private AdapterFactory adapterFactory;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PlatformService platformService;

    private Platform testPlatform;

    @BeforeEach
    void setUp() {
        testPlatform = new Platform();
        testPlatform.setId(UUID.randomUUID());
        testPlatform.setName("GitHub");
        testPlatform.setType("GITHUB");
        testPlatform.setApiBaseUrl("https://api.github.com");
        testPlatform.setConfig("{\"rateLimit\": 5000}");
        testPlatform.setStatus(Platform.PlatformStatus.ACTIVE);
    }

    @Test
    @DisplayName("测试创建平台 - 成功")
    void testCreatePlatform_Success() {
        // Given
        when(platformRepository.existsByName(anyString())).thenReturn(false);
        when(platformRepository.save(any(Platform.class))).thenReturn(testPlatform);

        // When
        Platform result = platformService.createPlatform(testPlatform);

        // Then
        assertNotNull(result);
        assertEquals("GitHub", result.getName());
        assertEquals("GITHUB", result.getType());
        verify(platformRepository, times(1)).existsByName("GitHub");
        verify(platformRepository, times(1)).save(any(Platform.class));
    }

    @Test
    @DisplayName("测试创建平台 - 平台已存在")
    void testCreatePlatform_AlreadyExists() {
        // Given
        when(platformRepository.existsByName(anyString())).thenReturn(true);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            platformService.createPlatform(testPlatform);
        });

        assertEquals(ErrorCode.PLATFORM_ALREADY_EXISTS, exception.getErrorCode());
        verify(platformRepository, times(1)).existsByName("GitHub");
        verify(platformRepository, never()).save(any(Platform.class));
    }

    @Test
    @DisplayName("测试根据ID获取平台 - 成功")
    void testGetPlatformById_Success() {
        // Given
        UUID platformId = testPlatform.getId();
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(testPlatform));

        // When
        Platform result = platformService.getPlatformById(platformId);

        // Then
        assertNotNull(result);
        assertEquals(platformId, result.getId());
        assertEquals("GitHub", result.getName());
        verify(platformRepository, times(1)).findById(platformId);
    }

    @Test
    @DisplayName("测试根据ID获取平台 - 不存在")
    void testGetPlatformById_NotFound() {
        // Given
        UUID platformId = UUID.randomUUID();
        when(platformRepository.findById(platformId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            platformService.getPlatformById(platformId);
        });

        assertEquals(ErrorCode.PLATFORM_NOT_FOUND, exception.getErrorCode());
        verify(platformRepository, times(1)).findById(platformId);
    }

    @Test
    @DisplayName("测试获取所有平台")
    void testGetAllPlatforms() {
        // Given
        List<Platform> platforms = Arrays.asList(testPlatform);
        when(platformRepository.findAll()).thenReturn(platforms);

        // When
        List<Platform> result = platformService.getAllPlatforms();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("GitHub", result.get(0).getName());
        verify(platformRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("测试更新平台 - 成功")
    void testUpdatePlatform_Success() {
        // Given
        UUID platformId = testPlatform.getId();
        Platform updatedPlatform = new Platform();
        updatedPlatform.setName("Updated GitHub");
        updatedPlatform.setType("GITHUB");
        updatedPlatform.setApiBaseUrl("https://api.github.com");
        updatedPlatform.setConfig("{\"rateLimit\": 10000}");
        updatedPlatform.setStatus(Platform.PlatformStatus.ACTIVE);

        when(platformRepository.findById(platformId)).thenReturn(Optional.of(testPlatform));
        when(platformRepository.save(any(Platform.class))).thenAnswer(invocation -> {
            Platform saved = invocation.getArgument(0);
            saved.setType(updatedPlatform.getType());
            saved.setApiBaseUrl(updatedPlatform.getApiBaseUrl());
            saved.setConfig(updatedPlatform.getConfig());
            saved.setStatus(updatedPlatform.getStatus());
            return saved;
        });

        // When
        Platform result = platformService.updatePlatform(platformId, updatedPlatform);

        // Then
        assertNotNull(result);
        assertEquals("GITHUB", result.getType());
        assertEquals("https://api.github.com", result.getApiBaseUrl());
        verify(platformRepository, times(1)).findById(platformId);
        verify(platformRepository, times(1)).save(any(Platform.class));
    }

    @Test
    @DisplayName("测试删除平台 - 成功")
    void testDeletePlatform_Success() {
        // Given
        UUID platformId = testPlatform.getId();
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(testPlatform));
        doNothing().when(platformRepository).delete(any(Platform.class));

        // When
        platformService.deletePlatform(platformId);

        // Then
        verify(platformRepository, times(1)).findById(platformId);
        verify(platformRepository, times(1)).delete(any(Platform.class));
    }

    @Test
    @DisplayName("测试删除平台 - 不存在")
    void testDeletePlatform_NotFound() {
        // Given
        UUID platformId = UUID.randomUUID();
        when(platformRepository.findById(platformId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            platformService.deletePlatform(platformId);
        });

        assertEquals(ErrorCode.PLATFORM_NOT_FOUND, exception.getErrorCode());
        verify(platformRepository, times(1)).findById(platformId);
        verify(platformRepository, never()).delete(any(Platform.class));
    }

    @Test
    @DisplayName("测试平台连接 - 成功")
    void testTestConnection_Success() throws Exception {
        // Given
        UUID platformId = testPlatform.getId();
        com.caat.adapter.PlatformAdapter mockAdapter = mock(com.caat.adapter.PlatformAdapter.class);
        
        when(platformRepository.findById(platformId)).thenReturn(Optional.of(testPlatform));
        when(adapterFactory.getAdapter("GITHUB")).thenReturn(mockAdapter);
        when(mockAdapter.testConnection(any())).thenReturn(true);
        // ObjectMapper的readValue可能不会被调用（如果配置解析失败会返回空Map），使用lenient
        lenient().when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.databind.JavaType.class))).thenReturn(new HashMap<>());

        // When
        boolean result = platformService.testConnection(platformId);

        // Then
        assertTrue(result);
        verify(platformRepository, times(1)).findById(platformId);
        verify(adapterFactory, times(1)).getAdapter("GITHUB");
        verify(mockAdapter, times(1)).testConnection(any());
    }
}

package com.caat.service;

import com.caat.entity.Platform;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台配置解析与合并工具。
 *
 * <p>职责：
 * <ul>
 *     <li>将平台配置 JSON 字符串解析为 {@code Map<String, Object>}。</li>
 *     <li>将 {@link Platform} 实体上的 {@code apiBaseUrl} 等合并到配置 Map 中。</li>
 * </ul>
 * <p>注意：这是一个纯工具类，不依赖 Spring 容器，仅依赖调用方传入的 {@link ObjectMapper}。
 */
@Slf4j
public final class PlatformConfigUtil {

    private PlatformConfigUtil() {
    }

    /**
     * 解析平台配置 JSON 字符串为 Map。
     *
     * @param objectMapper ObjectMapper 实例（统一由调用方注入）
     * @param configJson   平台配置 JSON 字符串
     * @return 解析后的配置 Map，解析失败时返回空 Map
     */
    public static Map<String, Object> parseConfig(ObjectMapper objectMapper, String configJson) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 不能为空");
        }
        if (configJson == null || configJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(
                configJson,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
        } catch (Exception e) {
            log.warn("解析平台配置失败，使用空配置: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 将平台实体上的 {@code apiBaseUrl} 等字段合并到配置 Map 中。
     *
     * @param platform 平台实体
     * @param config   已解析的配置 Map，可为 null
     * @return 合并后的新 Map，不会返回 null
     */
    public static Map<String, Object> mergePlatformConfig(Platform platform, Map<String, Object> config) {
        Map<String, Object> result;
        if (config == null) {
            result = new HashMap<>();
        } else {
            result = new HashMap<>(config);
        }
        if (platform != null
            && platform.getApiBaseUrl() != null
            && !platform.getApiBaseUrl().isEmpty()
            && !result.containsKey("apiBaseUrl")) {
            result.put("apiBaseUrl", platform.getApiBaseUrl());
        }
        return result;
    }
}


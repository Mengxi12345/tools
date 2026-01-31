package com.caat.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 平台适配器工厂
 * 根据平台类型获取对应的适配器实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdapterFactory {
    
    private final List<PlatformAdapter> adapters;
    private Map<String, PlatformAdapter> adapterMap;
    
    /**
     * 初始化适配器映射
     */
    private void initAdapterMap() {
        if (adapterMap == null) {
            adapterMap = adapters.stream()
                .collect(Collectors.toMap(
                    adapter -> adapter.getPlatformType().toUpperCase(),
                    Function.identity(),
                    (existing, replacement) -> {
                        log.warn("发现重复的平台适配器类型: {}, 使用第一个", existing.getPlatformType());
                        return existing;
                    }
                ));
            log.info("已注册 {} 个平台适配器: {}", adapterMap.size(), adapterMap.keySet());
        }
    }
    
    /**
     * 根据平台类型获取适配器
     * @param platformType 平台类型（如 "github", "twitter" 等）
     * @return 平台适配器实例
     * @throws com.caat.exception.BusinessException 如果找不到对应的适配器
     */
    public PlatformAdapter getAdapter(String platformType) {
        initAdapterMap();
        
        String key = platformType != null ? platformType.toUpperCase() : "";
        PlatformAdapter adapter = adapterMap.get(key);
        if (adapter == null) {
            throw new com.caat.exception.BusinessException(
                com.caat.exception.ErrorCode.PLATFORM_NOT_FOUND,
                "未找到平台类型为 '" + platformType + "' 的适配器。已注册的适配器类型: " + adapterMap.keySet()
            );
        }
        
        return adapter;
    }
    
    /**
     * 获取所有已注册的平台类型
     * @return 平台类型列表
     */
    public List<String> getSupportedPlatformTypes() {
        initAdapterMap();
        return adapterMap.keySet().stream().sorted().collect(Collectors.toList());
    }
    
    /**
     * 检查是否支持指定的平台类型
     * @param platformType 平台类型
     * @return 是否支持
     */
    public boolean isSupported(String platformType) {
        initAdapterMap();
        String key = platformType != null ? platformType.toUpperCase() : "";
        return adapterMap.containsKey(key);
    }
}

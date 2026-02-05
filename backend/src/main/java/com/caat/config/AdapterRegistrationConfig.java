package com.caat.config;

import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.impl.TimeStoreAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 显式注册平台适配器，确保 TIMESTORE 等适配器被加载（避免因扫描顺序或运行旧构建导致未注册）
 */
@Slf4j
@Configuration
public class AdapterRegistrationConfig {

    @Bean
    public PlatformAdapter timeStoreAdapter(@Qualifier("timeStoreRestTemplate") RestTemplate timeStoreRestTemplate,
                                            ObjectMapper objectMapper) {
        log.info("注册平台适配器: TIMESTORE (TimeStoreAdapter)");
        return new TimeStoreAdapter(timeStoreRestTemplate, objectMapper);
    }
}

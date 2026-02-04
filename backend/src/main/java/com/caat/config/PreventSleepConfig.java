package com.caat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 防止系统休眠配置（macOS）
 * 
 * 当应用启动时，如果检测到在 macOS 上运行，会启动一个后台 caffeinate 进程
 * 防止系统休眠，确保 Quartz 定时任务正常执行。
 * 
 * 注意：此配置仅在非 Docker 环境下生效（通过 app.prevent-sleep.enabled 控制）
 */
@Slf4j
@Configuration
public class PreventSleepConfig {

    @Bean
    @ConditionalOnProperty(name = "app.prevent-sleep.enabled", havingValue = "true", matchIfMissing = false)
    public ApplicationRunner preventSleepRunner() {
        return args -> {
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("mac") || osName.contains("darwin")) {
                log.info("检测到 macOS 系统，启动防休眠守护进程...");
                try {
                    // 启动 caffeinate 进程，防止系统、显示器和磁盘休眠
                    ProcessBuilder pb = new ProcessBuilder("caffeinate", "-dim");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    
                    // 在 JVM 关闭时清理进程
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        log.info("应用关闭，终止防休眠进程...");
                        process.destroy();
                    }));
                    
                    log.info("防休眠守护进程已启动（PID: {}）", process.pid());
                    log.info("使用 caffeinate -dim 防止系统、显示器和磁盘休眠");
                } catch (IOException e) {
                    log.warn("无法启动 caffeinate 进程，防休眠功能可能不可用: {}", e.getMessage());
                    log.warn("建议使用 scripts/run-backend-no-sleep.sh 启动服务以确保防休眠功能");
                }
            } else {
                log.debug("非 macOS 系统（{}），跳过防休眠配置", osName);
            }
        };
    }
}

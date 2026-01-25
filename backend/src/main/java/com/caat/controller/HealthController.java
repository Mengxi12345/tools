package com.caat.controller;

import com.caat.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@Tag(name = "健康检查", description = "系统健康检查接口")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Operation(summary = "健康检查", description = "检查系统运行状态")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "content-aggregator");
        health.put("version", "1.0.0-SNAPSHOT");
        return ApiResponse.success(health);
    }
}

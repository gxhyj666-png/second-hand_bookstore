package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查（部署探针 / 前端联调）。
 */
@RestController
public class HealthController {

    @GetMapping("/v1/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "time", LocalDateTime.now().toString(),
                "app", "textbook-loop"));
    }
}

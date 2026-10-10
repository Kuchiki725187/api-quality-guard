package com.quality.guard.controller;

import com.quality.guard.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：验证应用存活与数据库连通性，供前端和运维探针调用
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Result<Map<String, Object>> health() {
        boolean dbUp = isDatabaseUp();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", "api-quality-guard");
        data.put("status", dbUp ? "UP" : "DEGRADED");
        data.put("database", dbUp ? "UP" : "DOWN");
        data.put("timestamp", LocalDateTime.now());
        return Result.success(data);
    }

    private boolean isDatabaseUp() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (DataAccessException e) {
            log.error("数据库连通性检查失败: {}", e.getMessage());
            return false;
        }
    }
}

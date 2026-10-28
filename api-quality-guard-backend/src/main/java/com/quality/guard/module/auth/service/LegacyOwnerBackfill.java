package com.quality.guard.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.module.auth.config.AuthProperties;
import com.quality.guard.module.auth.entity.SysUser;
import com.quality.guard.module.auth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 将没有属主的历史业务数据归属到管理员账号
 * 必须晚于 AdminAccountInitializer 执行；使用原生 JDBC 绕过属主拦截器
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class LegacyOwnerBackfill implements ApplicationRunner {

    private static final List<String> BUSINESS_TABLES = List.of(
            "api_definition",
            "test_case",
            "test_plan",
            "test_plan_case",
            "test_run",
            "test_result",
            "ai_diagnosis",
            "quality_gate_record");

    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuthProperties authProperties;

    @Value("${aqg.auth.owner-backfill-enabled:true}")
    private boolean backfillEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!backfillEnabled || !authProperties.isSeedEnabled()) {
            return;
        }
        String username = authProperties.getAdminUsername();
        if (!StringUtils.hasText(username)) {
            return;
        }
        SysUser admin = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (admin == null) {
            log.warn("未找到管理员账号 {}，跳过历史数据属主回填", username);
            return;
        }
        for (String table : BUSINESS_TABLES) {
            int updated = jdbcTemplate.update(
                    "UPDATE " + table + " SET owner_id = ? WHERE owner_id IS NULL", admin.getId());
            if (updated > 0) {
                log.info("历史数据属主回填: table={}, rows={}, ownerId={}", table, updated, admin.getId());
            }
        }
    }
}

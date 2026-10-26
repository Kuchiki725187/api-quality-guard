package com.quality.guard.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.module.auth.config.AuthProperties;
import com.quality.guard.module.auth.entity.SysUser;
import com.quality.guard.module.auth.mapper.SysUserMapper;
import com.quality.guard.module.auth.model.AuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时确保存在一个管理员账号（只创建，不覆盖已有账号）
 * 默认 admin/admin123，可用 AQG_ADMIN_USERNAME / AQG_ADMIN_PASSWORD 覆盖
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "admin123";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!authProperties.isSeedEnabled()) {
            return;
        }
        String username = authProperties.getAdminUsername();
        String password = authProperties.getAdminPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("管理员种子账号未配置用户名或密码，跳过创建");
            return;
        }
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName("管理员");
        admin.setRole(AuthConstants.ROLE_ADMIN);
        admin.setEnabled(true);
        try {
            sysUserMapper.insert(admin);
            log.info("已创建初始管理员账号: {}", username);
            if (DEFAULT_PASSWORD.equals(password)) {
                log.warn("管理员账号仍在使用默认密码，部署前请通过 AQG_ADMIN_PASSWORD 修改");
            }
        } catch (DuplicateKeyException e) {
            log.info("管理员账号已被其他实例创建: {}", username);
        }
    }
}

package com.quality.guard.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.auth.entity.SysUser;
import com.quality.guard.module.auth.mapper.SysUserMapper;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.CurrentUser;
import com.quality.guard.module.auth.session.RedisSessionStore;
import com.quality.guard.module.auth.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 管理员用户管理：列表、启用/禁用、重置密码
 * 禁用与重置密码后立即让目标用户的 Redis 会话失效
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisSessionStore sessionStore;

    public PageResult<AdminUserVO> page(long page, long pageSize, String keyword) {
        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<SysUser>()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysUser::getUsername, keyword)
                                .or()
                                .like(SysUser::getDisplayName, keyword))
                        .orderByAsc(SysUser::getId));
        List<AdminUserVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    public void updateStatus(Long id, boolean enabled, CurrentUser currentUser) {
        SysUser target = getById(id);
        if (Boolean.TRUE.equals(target.getEnabled()) == enabled) {
            return;
        }
        if (!enabled) {
            if (Objects.equals(target.getId(), currentUser.getUserId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不能禁用当前登录账号");
            }
            if (AuthConstants.ROLE_ADMIN.equals(target.getRole()) && countEnabledAdmins() <= 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "至少需要保留一个启用的管理员账号");
            }
        }
        target.setEnabled(enabled);
        sysUserMapper.updateById(target);
        if (!enabled) {
            sessionStore.deleteAllForUser(id);
        }
    }

    public void resetPassword(Long id, String newPassword) {
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码过长");
        }
        SysUser target = getById(id);
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(target);
        sessionStore.deleteAllForUser(id);
    }

    private SysUser getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在: " + id);
        }
        return user;
    }

    private long countEnabledAdmins() {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, AuthConstants.ROLE_ADMIN)
                .eq(SysUser::getEnabled, true));
        return count == null ? 0 : count;
    }

    private AdminUserVO toVO(SysUser user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setRole(user.getRole());
        vo.setEnabled(user.getEnabled());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}

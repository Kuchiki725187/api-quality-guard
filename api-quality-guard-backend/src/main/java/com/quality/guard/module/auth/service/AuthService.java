package com.quality.guard.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.auth.config.AuthProperties;
import com.quality.guard.module.auth.dto.ChangePasswordDTO;
import com.quality.guard.module.auth.dto.LoginDTO;
import com.quality.guard.module.auth.dto.RegisterDTO;
import com.quality.guard.module.auth.entity.SysUser;
import com.quality.guard.module.auth.mapper.SysUserMapper;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.CurrentUser;
import com.quality.guard.module.auth.session.RedisSessionStore;
import com.quality.guard.module.auth.vo.LoginVO;
import com.quality.guard.module.auth.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录、注册、登出与当前用户
 * 登录失败统一返回“用户名或密码错误”，不暴露账号是否存在/是否被禁用
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisSessionStore sessionStore;
    private final AuthProperties authProperties;

    public LoginVO login(LoginDTO dto) {
        String username = dto.getUsername().trim();
        SysUser user = findByUsername(username);
        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = sessionStore.create(toCurrentUser(user));
        return new LoginVO(token, sessionStore.ttlSeconds(), toUserVO(user));
    }

    public UserVO register(RegisterDTO dto) {
        if (!authProperties.isRegistrationEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "注册功能已关闭");
        }
        if (dto.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码过长");
        }
        String username = dto.getUsername().trim();
        if (findByUsername(username) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setDisplayName(dto.getDisplayName().trim());
        user.setRole(AuthConstants.ROLE_USER);
        user.setEnabled(true);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        return toUserVO(user);
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessionStore.delete(token);
        }
    }

    public UserVO me(CurrentUser currentUser) {
        return new UserVO(currentUser.getUserId(), currentUser.getUsername(),
                currentUser.getDisplayName(), currentUser.getRole());
    }

    public void changePassword(CurrentUser currentUser, ChangePasswordDTO dto) {
        SysUser user = sysUserMapper.selectById(currentUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前密码不正确");
        }
        if (dto.getNewPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码过长");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        // 修改密码后所有设备下线，前端随后跳回登录页
        sessionStore.deleteAllForUser(currentUser.getUserId());
    }

    private SysUser findByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
    }

    private CurrentUser toCurrentUser(SysUser user) {
        return new CurrentUser(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole());
    }

    private UserVO toUserVO(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole());
    }
}

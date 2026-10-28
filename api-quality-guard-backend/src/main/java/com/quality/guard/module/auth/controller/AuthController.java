package com.quality.guard.module.auth.controller;

import com.quality.guard.common.result.Result;
import com.quality.guard.module.auth.dto.ChangePasswordDTO;
import com.quality.guard.module.auth.dto.LoginDTO;
import com.quality.guard.module.auth.dto.RegisterDTO;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.CurrentUser;
import com.quality.guard.module.auth.service.AuthService;
import com.quality.guard.module.auth.vo.LoginVO;
import com.quality.guard.module.auth.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录、注册、登出与当前用户
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(authService.register(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute(AuthConstants.AUTH_TOKEN_ATTRIBUTE) String token) {
        authService.logout(token);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserVO> me(@RequestAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE) CurrentUser currentUser) {
        return Result.success(authService.me(currentUser));
    }

    /** 自助修改密码：成功后该用户全部会话失效，需要重新登录 */
    @PutMapping("/password")
    public Result<Void> changePassword(
            @RequestAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE) CurrentUser currentUser,
            @Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(currentUser, dto);
        return Result.success();
    }
}

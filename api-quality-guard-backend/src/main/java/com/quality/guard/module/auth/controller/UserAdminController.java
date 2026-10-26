package com.quality.guard.module.auth.controller;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.Result;
import com.quality.guard.module.auth.dto.ResetPasswordDTO;
import com.quality.guard.module.auth.dto.UserStatusDTO;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.CurrentUser;
import com.quality.guard.module.auth.service.UserAdminService;
import com.quality.guard.module.auth.vo.AdminUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理（仅 ADMIN，由 AdminInterceptor 校验角色）
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public Result<PageResult<AdminUserVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(userAdminService.page(page, pageSize, keyword));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusDTO dto,
            @RequestAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE) CurrentUser currentUser) {
        userAdminService.updateStatus(id, dto.getEnabled(), currentUser);
        return Result.success();
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordDTO dto) {
        userAdminService.resetPassword(id, dto.getNewPassword());
        return Result.success();
    }
}

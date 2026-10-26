package com.quality.guard.module.auth.interceptor;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员接口拦截器（必须在 AuthInterceptor 之后执行）
 */
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        Object attribute = request.getAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE);
        if (!(attribute instanceof CurrentUser currentUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (!AuthConstants.ROLE_ADMIN.equals(currentUser.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
        }
        return true;
    }
}

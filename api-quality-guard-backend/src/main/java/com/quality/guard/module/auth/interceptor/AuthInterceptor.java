package com.quality.guard.module.auth.interceptor;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.CurrentUser;
import com.quality.guard.module.auth.session.RedisSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 会话拦截器：解析 Bearer Token，校验 Redis 会话并滑动续期
 * 通过 request attribute 传递当前用户与原始 token，Controller 用 @RequestAttribute 获取
 */
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final RedisSessionStore sessionStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(AuthConstants.BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String token = header.substring(AuthConstants.BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        CurrentUser currentUser = sessionStore.findAndRenew(token);
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        request.setAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE, currentUser);
        request.setAttribute(AuthConstants.AUTH_TOKEN_ATTRIBUTE, token);
        return true;
    }
}

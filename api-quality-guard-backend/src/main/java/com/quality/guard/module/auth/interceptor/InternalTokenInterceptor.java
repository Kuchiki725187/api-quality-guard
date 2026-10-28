package com.quality.guard.module.auth.interceptor;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部服务回调鉴权：/api/agent-tools/** 只接受携带 X-Internal-Token 的 Agent 请求
 * 未配置令牌时拒绝所有请求（fail closed）
 */
public class InternalTokenInterceptor implements HandlerInterceptor {

    @Value("${aqg.internal-token:}")
    private String internalToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "内部接口访问令牌未配置");
        }
        String provided = request.getHeader(AuthConstants.INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(provided) || !MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                internalToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "内部访问令牌无效");
        }
        String userIdHeader = request.getHeader(AuthConstants.INTERNAL_USER_HEADER);
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少内部调用用户上下文");
        }
        long userId;
        try {
            userId = Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "内部调用用户上下文非法");
        }
        if (userId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "内部调用用户上下文非法");
        }
        UserContext.set(userId);
        return true;
    }
}

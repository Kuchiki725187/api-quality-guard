package com.quality.guard.module.auth.model;

/**
 * 认证相关常量
 */
public final class AuthConstants {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    public static final String CURRENT_USER_ATTRIBUTE = "aqg.currentUser";
    public static final String AUTH_TOKEN_ATTRIBUTE = "aqg.authToken";

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private AuthConstants() {
    }
}

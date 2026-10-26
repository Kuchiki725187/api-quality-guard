package com.quality.guard.module.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 会话中的用户快照，登录后写入，请求时由拦截器读取
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    private Long userId;

    private String username;

    private String displayName;

    private String role;
}

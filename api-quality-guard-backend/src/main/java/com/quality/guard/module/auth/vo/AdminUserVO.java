package com.quality.guard.module.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVO {

    private Long id;

    private String username;

    private String displayName;

    private String role;

    private Boolean enabled;

    private LocalDateTime createdAt;
}

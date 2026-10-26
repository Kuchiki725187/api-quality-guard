package com.quality.guard.module.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证配置，对应 application.yml 的 aqg.auth.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "aqg.auth")
public class AuthProperties {

    private long sessionTtlMinutes = 30;

    private boolean registrationEnabled = true;

    private boolean seedEnabled = true;

    private String adminUsername = "admin";

    private String adminPassword = "admin123";
}

package com.quality.guard.config;

import com.quality.guard.module.auth.interceptor.AdminInterceptor;
import com.quality.guard.module.auth.interceptor.AuthInterceptor;
import com.quality.guard.module.auth.interceptor.InternalTokenInterceptor;
import com.quality.guard.module.auth.session.RedisSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 认证拦截器注册
 * 登录/注册/健康检查公开；Agent 工具回调使用内部令牌，不走用户会话
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RedisSessionStore sessionStore;

    @Bean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor(sessionStore);
    }

    @Bean
    public AdminInterceptor adminInterceptor() {
        return new AdminInterceptor();
    }

    @Bean
    public InternalTokenInterceptor internalTokenInterceptor() {
        return new InternalTokenInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/health",
                        "/api/agent-tools/**")
                .order(1);
        registry.addInterceptor(internalTokenInterceptor())
                .addPathPatterns("/api/agent-tools/**")
                .order(2);
        registry.addInterceptor(adminInterceptor())
                .addPathPatterns("/api/admin/**")
                .order(3);
    }
}

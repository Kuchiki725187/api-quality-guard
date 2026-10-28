package com.quality.guard.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.quality.guard.module.auth.model.UserContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页 + 按 owner_id 的用户数据隔离
 * 所有业务表都由拦截器自动追加/填充 owner_id；sys_user 是账号表，不做隔离
 */
@Configuration
@MapperScan("com.quality.guard.module.**.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(UserContext.currentOrSystem());
            }

            @Override
            public String getTenantIdColumn() {
                return "owner_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return "sys_user".equalsIgnoreCase(tableName)
                        || "flyway_schema_history".equalsIgnoreCase(tableName);
            }
        };
    }
}

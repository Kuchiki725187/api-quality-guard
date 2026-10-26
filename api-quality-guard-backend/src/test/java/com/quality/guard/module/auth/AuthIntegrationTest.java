package com.quality.guard.module.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.module.auth.entity.SysUser;
import com.quality.guard.module.auth.mapper.SysUserMapper;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与用户管理集成测试。
 * 使用 Redis 15 号库，测试前后清理 aqg:auth:* 键；数据库依赖测试事务回滚。
 */
@SpringBootTest(properties = {
        "spring.data.redis.database=15",
        "aqg.auth.seed-enabled=false",
        "aqg.internal-token=test-internal-token"
})
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearRedisSessions() {
        Set<String> keys = redisTemplate.keys("aqg:auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void loginProtectsApisAndLogoutInvalidatesToken() throws Exception {
        SysUser admin = createUser(AuthConstants.ROLE_ADMIN, "adminPass123");

        mockMvc.perform(get("/api/apis"))
                .andExpect(status().isUnauthorized());

        String token = login(admin.getUsername(), "adminPass123");

        mockMvc.perform(get("/api/apis").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(admin.getUsername()))
                .andExpect(jsonPath("$.data.role").value(AuthConstants.ROLE_ADMIN));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailuresReturnGenericUnauthorized() throws Exception {
        SysUser user = createUser(AuthConstants.ROLE_USER, "userPass123");

        mockMvc.perform(loginRequest(user.getUsername(), "wrongPass123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        user.setEnabled(false);
        sysUserMapper.updateById(user);

        mockMvc.perform(loginRequest(user.getUsername(), "userPass123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void registerCreatesNormalUserAndRejectsDuplicate() throws Exception {
        String username = "reg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "displayName", "注册用户",
                "password", "register123"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.role").value(AuthConstants.ROLE_USER));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        String token = login(username, "register123");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("注册用户"));
    }

    @Test
    void adminCanManageUsersAndSessionsAreInvalidated() throws Exception {
        SysUser admin = createUser(AuthConstants.ROLE_ADMIN, "adminPass123");
        SysUser user = createUser(AuthConstants.ROLE_USER, "userPass123");
        String adminToken = login(admin.getUsername(), "adminPass123");
        String userToken = login(user.getUsername(), "userPass123");

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", user.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value(user.getUsername()));

        // 管理员不能禁用自己
        mockMvc.perform(put("/api/admin/users/{id}/status", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());

        // 禁用后旧 token 立即失效，且无法再次登录
        mockMvc.perform(put("/api/admin/users/{id}/status", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(loginRequest(user.getUsername(), "userPass123"))
                .andExpect(status().isUnauthorized());

        // 重新启用后可以登录
        mockMvc.perform(put("/api/admin/users/{id}/status", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        String newToken = login(user.getUsername(), "userPass123");

        // 重置密码后旧会话失效，旧密码不可用，新密码可登录
        mockMvc.perform(put("/api/admin/users/{id}/password", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"resetPass456\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(newToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(loginRequest(user.getUsername(), "userPass123"))
                .andExpect(status().isUnauthorized());
        String resetToken = login(user.getUsername(), "resetPass456");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(resetToken)))
                .andExpect(status().isOk());
    }

    @Test
    void agentToolsRequireInternalTokenInsteadOfUserSession() throws Exception {
        ApiDefinition api = new ApiDefinition();
        api.setName("IT-agent-api");
        api.setHttpMethod("GET");
        api.setPath("/it/agent");
        apiDefinitionMapper.insert(api);

        mockMvc.perform(get("/api/agent-tools/api-detail/{id}", api.getId()))
                .andExpect(status().isUnauthorized());

        SysUser user = createUser(AuthConstants.ROLE_USER, "userPass123");
        String token = login(user.getUsername(), "userPass123");
        mockMvc.perform(get("/api/agent-tools/api-detail/{id}", api.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/agent-tools/api-detail/{id}", api.getId())
                        .header(AuthConstants.INTERNAL_TOKEN_HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("IT-agent-api"));
    }

    private SysUser createUser(String role, String rawPassword) {
        SysUser user = new SysUser();
        user.setUsername("it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName("IT-" + role);
        user.setRole(role);
        user.setEnabled(true);
        sysUserMapper.insert(user);
        return user;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String username, String password) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", password)));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(username, password))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return node.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return AuthConstants.BEARER_PREFIX + token;
    }
}

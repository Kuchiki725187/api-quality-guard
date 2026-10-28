package com.quality.guard.module.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.module.auth.entity.SysUser;
import com.quality.guard.module.auth.mapper.SysUserMapper;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.UserContext;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 两个用户之间的业务数据隔离：接口、用例、报告/风险接口。
 */
@SpringBootTest(properties = {
        "spring.data.redis.database=15",
        "aqg.auth.seed-enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class UserIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private TestResultMapper testResultMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearSessions() {
        Set<String> keys = redisTemplate.keys("aqg:auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void usersCannotSeeEachOthersBusinessData() throws Exception {
        SysUser userA = createUser();
        SysUser userB = createUser();
        String tokenA = login(userA.getUsername(), "userPass123");
        String tokenB = login(userB.getUsername(), "userPass123");

        long apiId = createApi(tokenA, "A-隔离接口", "/a/isolation");
        long caseId = createCase(tokenA, apiId, "A-隔离用例");

        mockMvc.perform(get("/api/apis").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/apis").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/apis/{id}", apiId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/cases")
                        .param("apiId", String.valueOf(apiId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/cases")
                        .param("apiId", String.valueOf(apiId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        TestResult failed = new TestResult();
        failed.setRunId(1L);
        failed.setCaseId(caseId);
        failed.setStatus("FAILED");
        failed.setDurationMs(10L);
        UserContext.runAs(userA.getId(), () -> testResultMapper.insert(failed));

        mockMvc.perform(get("/api/reports/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskApis[0].apiId").value(apiId))
                .andExpect(jsonPath("$.data.riskApis[0].name").value("A-隔离接口"));
        mockMvc.perform(get("/api/reports/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskApis.length()").value(0));
    }

    private long createApi(String token, String name, String path) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "httpMethod", "GET",
                "path", path,
                "baseUrl", "http://127.0.0.1:1"));
        MvcResult result = mockMvc.perform(post("/api/apis")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private long createCase(String token, long apiId, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "apiId", apiId,
                "name", name,
                "requestParams", Map.of(),
                "assertionRules", List.of(AssertionRule.statusCode(200))));
        MvcResult result = mockMvc.perform(post("/api/cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private SysUser createUser() {
        SysUser user = new SysUser();
        user.setUsername("it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setPasswordHash(passwordEncoder.encode("userPass123"));
        user.setDisplayName("隔离测试用户");
        user.setRole(AuthConstants.ROLE_USER);
        user.setEnabled(true);
        sysUserMapper.insert(user);
        return user;
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
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

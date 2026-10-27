package com.quality.guard.module.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.infrastructure.agent.AgentChatRequest;
import com.quality.guard.infrastructure.agent.AgentChatResponse;
import com.quality.guard.infrastructure.agent.ChatAgentClient;
import com.quality.guard.module.ai.assistant.session.ChatHistoryStore;
import com.quality.guard.module.auth.entity.SysUser;
import com.quality.guard.module.auth.mapper.SysUserMapper;
import com.quality.guard.module.auth.model.AuthConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 助手聊天代理集成测试（Redis 15 号库，Redis 历史测试后清理）。
 */
@SpringBootTest(properties = {
        "spring.data.redis.database=15",
        "aqg.auth.seed-enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AssistantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChatHistoryStore chatHistoryStore;

    @MockitoBean
    private ChatAgentClient chatAgentClient;

    @AfterEach
    void clearRedisKeys() {
        deleteKeys("aqg:assistant:*");
        deleteKeys("aqg:auth:*");
    }

    @Test
    void chatPersistsHistoryForCurrentUserAndCanBeCleared() throws Exception {
        SysUser user = createUser();
        String token = login(user.getUsername(), "userPass123");
        when(chatAgentClient.chat(any(AgentChatRequest.class)))
                .thenReturn(new AgentChatResponse("**结论**：平台整体良好。", "test-model", 10, 20, 500L, 2));

        mockMvc.perform(post("/api/assistant/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"平台质量怎么样？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("**结论**：平台整体良好。"))
                .andExpect(jsonPath("$.data.rounds").value(2));

        mockMvc.perform(get("/api/assistant/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("平台质量怎么样？"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"));

        mockMvc.perform(post("/api/assistant/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"哪些接口风险最高？\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/assistant/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(chatAgentClient, times(2)).chat(captor.capture());
        List<AgentChatRequest> requests = captor.getAllValues();
        assertEquals(0, requests.get(0).getHistory().size());
        assertEquals(2, requests.get(1).getHistory().size());
        assertEquals("平台质量怎么样？", requests.get(1).getHistory().get(0).getContent());

        mockMvc.perform(delete("/api/assistant/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/assistant/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void chatRequiresLoginAndRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"平台质量怎么样？\"}"))
                .andExpect(status().isUnauthorized());

        SysUser user = createUser();
        String token = login(user.getUsername(), "userPass123");

        mockMvc.perform(post("/api/assistant/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agentFailureDoesNotPersistHistory() throws Exception {
        SysUser user = createUser();
        String token = login(user.getUsername(), "userPass123");
        when(chatAgentClient.chat(any(AgentChatRequest.class)))
                .thenThrow(new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 助手不可用"));

        mockMvc.perform(post("/api/assistant/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"平台质量怎么样？\"}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/assistant/history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void historyKeepsOnlyLatestTenExchanges() {
        Long userId = 9_000_000L + (long) (Math.random() * 100_000);
        for (int i = 1; i <= 11; i++) {
            chatHistoryStore.append(userId, "问题" + i, "回答" + i);
        }

        var exchanges = chatHistoryStore.load(userId);

        assertEquals(10, exchanges.size());
        assertEquals("问题2", exchanges.get(0).getUser());
        assertEquals("问题11", exchanges.get(9).getUser());
        chatHistoryStore.clear(userId);
    }

    private SysUser createUser() {
        SysUser user = new SysUser();
        user.setUsername("it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setPasswordHash(passwordEncoder.encode("userPass123"));
        user.setDisplayName("AI-IT");
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

    private void deleteKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

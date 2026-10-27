package com.quality.guard.infrastructure.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * AI 助手问答客户端：调用独立 Agent 的 /agent/chat
 * 与诊断/增强客户端一致，强制 HTTP/1.1
 */
@Slf4j
@Component
public class ChatAgentClient {

    private final ObjectMapper objectMapper;

    @Value("${aqg.agent.base-url:http://localhost:8090}")
    private String baseUrl;

    @Value("${aqg.agent.timeout-seconds:120}")
    private long timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ChatAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        String url = trimTrailingSlash(baseUrl) + "/agent/chat";
        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "构造问答请求失败");
        }
        log.info("发送 AI 问答请求: questionLength={}, historySize={}",
                request.getQuestion().length(),
                request.getHistory() == null ? 0 : request.getHistory().size());

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 问答调用被中断");
        } catch (IOException e) {
            log.warn("问答 Agent 不可达: {}", e.getMessage());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "AI 助手服务不可用（Agent 未启动或网络异常）");
        }

        if (response.statusCode() == 502) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "AI 问答失败: " + extractMessage(response.body()));
        }
        if (response.statusCode() != 200) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "AI 问答请求被 Agent 拒绝: HTTP " + response.statusCode()
                            + " " + abbreviate(response.body()));
        }

        try {
            AgentChatResponse parsed = objectMapper.readValue(response.body(), AgentChatResponse.class);
            if (parsed.getAnswer() == null || parsed.getAnswer().isBlank()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 助手未返回内容");
            }
            log.info("AI 问答完成: rounds={}, inputTokens={}, outputTokens={}, durationMs={}",
                    parsed.getRounds(), parsed.getInputTokens(),
                    parsed.getOutputTokens(), parsed.getDurationMs());
            return parsed;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("解析问答响应失败: {} body={}", e.getMessage(), abbreviate(response.body()));
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析 AI 问答响应失败");
        }
    }

    private String extractMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("message").asText("未知错误");
        } catch (IOException e) {
            return "未知错误";
        }
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}

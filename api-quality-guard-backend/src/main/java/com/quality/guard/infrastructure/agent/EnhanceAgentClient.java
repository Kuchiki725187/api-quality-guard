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
 * AI 用例增强客户端：调用独立 Agent 的 /agent/enhance。
 * 与诊断客户端一致，强制 HTTP/1.1（避免 h2c 升级导致请求体丢失）。
 */
@Slf4j
@Component
public class EnhanceAgentClient {

    private final ObjectMapper objectMapper;

    @Value("${aqg.agent.base-url:http://localhost:8090}")
    private String baseUrl;

    @Value("${aqg.agent.timeout-seconds:120}")
    private long timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public EnhanceAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentEnhanceResponse enhance(AgentEnhanceRequest request) {
        String url = trimTrailingSlash(baseUrl) + "/agent/enhance";
        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "构造增强请求失败");
        }

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
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 用例增强调用被中断");
        } catch (IOException e) {
            log.warn("增强 Agent 不可达: {}", e.getMessage());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "AI 增强服务不可用（Agent 未启动或网络异常）");
        }

        if (response.statusCode() == 502) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "AI 用例增强失败: " + extractMessage(response.body()));
        }
        if (response.statusCode() != 200) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "AI 增强请求被 Agent 拒绝: HTTP " + response.statusCode()
                            + " " + abbreviate(response.body()));
        }

        try {
            AgentEnhanceResponse parsed = objectMapper.readValue(response.body(), AgentEnhanceResponse.class);
            if (parsed.getCases() == null || parsed.getCases().isEmpty()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 未返回任何候选用例");
            }
            return parsed;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("解析增强响应失败: {} body={}", e.getMessage(), abbreviate(response.body()));
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析 AI 增强响应失败");
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

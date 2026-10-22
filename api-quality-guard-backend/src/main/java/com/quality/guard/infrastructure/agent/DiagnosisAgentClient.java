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
 * 诊断 Agent 客户端,通过 HTTP 调用独立的轻量 Agent 服
 * Agent 地址通过 aqg.agent.base-url 配置（环境变量 AQG_AGENT_BASE_URL）
 * 业务层调用 diagnose()->将请求对象序列化为 JSON->使用 HttpClient 发送 HTTP POST 请求->Agent 执行诊断
 * ->处理 HTTP 响应返回 AgentDiagnosisResponse
 */
@Slf4j
@Component
public class DiagnosisAgentClient {

    private final ObjectMapper objectMapper;

    @Value("${aqg.agent.base-url:http://localhost:8090}")
    private String baseUrl;

    @Value("${aqg.agent.timeout-seconds:120}")
    private long timeoutSeconds;

    // 强制 HTTP/1.1：JDK HttpClient 默认尝试 HTTP/2 明文升级（h2c），uvicorn 等开发服务器不支持，
    // 会导致请求体在升级握手时丢失（Agent 端表现为收到空 body 的 422）
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public DiagnosisAgentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
        //拼接agent请求地址
        String url = trimTrailingSlash(baseUrl) + "/agent/diagnose";
        String body;
        try {
            //将请请求对象转换为JSON字符串
            body = objectMapper.writeValueAsString(request);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "构造诊断请求失败");
        }
        if (body == null || body.isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "诊断请求序列化为空，请检查请求对象");
        }
        log.info("发送诊断请求: url={}, bodyLength={}", url, body.length());
        //构造http请求
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        //发送请求并接收响应
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 诊断调用被中断");
        } catch (IOException e) {
            log.warn("诊断 Agent 不可达: {}", e.getMessage());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "AI 诊断服务不可用（Agent 未启动或网络异常）");
        }

        if (response.statusCode() == 502) {
            String message = extractMessage(response.body());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 诊断失败: " + message);
        }
        if (response.statusCode() != 200) {
            // 带上 Agent 返回的详情（422 校验失败时能直接看到具体字段）
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "AI 诊断请求被 Agent 拒绝: HTTP " + response.statusCode()
                            + " " + abbreviate(response.body())
                            + "（我方发送 bodyLength=" + body.length() + "）");
        }
        //将响应JSON转换为对象
        try {
            //JSON字符串转换为对象
            AgentDiagnosisResponse parsed = objectMapper.readValue(response.body(), AgentDiagnosisResponse.class);
            if (parsed.getDiagnosis() == null) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "AI 诊断响应缺少 diagnosis 内容");
            }
            return parsed;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("解析诊断响应失败: {} body={}", e.getMessage(), abbreviate(response.body()));
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析 AI 诊断响应失败");
        }
    }
        //提取错误信息
    private String extractMessage(String body) {
        try {
            //将JSON字符串转换为JSON树结构
            JsonNode node = objectMapper.readTree(body);
            return node.path("message").asText("未知错误");
        } catch (IOException e) {
            return "未知错误";
        }
    }
    //去掉agent服务的基础地址末尾的/
    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
    //限制日志长度
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}

package com.quality.guard.module.testexec.runner.engine;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.scoring.assertion.AssertionEngine;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.BuiltRequest;
import com.quality.guard.module.testexec.model.ExecutionResult;
import com.quality.guard.module.testexec.model.RequestSnapshot;
import com.quality.guard.module.testexec.model.ResponseSnapshot;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.service.RunRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 执行引擎（核心编排）
 * 单用例执行 = 加载用例 → 构建请求 → 发送 → 计量 → 快照 → 断言 → 落库
 */
@Component
@RequiredArgsConstructor
public class TestExecutionEngine {

    /** 响应体快照上限：超过 64KB 截断存储并标记 */
    private static final int MAX_BODY_SNAPSHOT_BYTES = 64 * 1024;

    private final TestCaseMapper testCaseMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final HttpRequestBuilder httpRequestBuilder;
    private final AssertionEngine assertionEngine;
    private final RunRecordService runRecordService;

    /** 重定向策略 NEVER：测试平台需观察接口的真实响应，不自动跟随跳转 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 执行单个用例（同步）
     *
     * @param caseId          用例 ID
     * @param baseUrlOverride 请求级地址覆盖，可为空
     * @return 落库后的结果实体
     */
    public TestResult executeCase(Long caseId, String baseUrlOverride) {
        // 1. 加载用例与接口定义（不存在 → 404）
        TestCase testCase = testCaseMapper.selectById(caseId);
        if (testCase == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测试用例不存在: " + caseId);
        }
        ApiDefinition apiDefinition = apiDefinitionMapper.selectById(testCase.getApiId());
        if (apiDefinition == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "接口定义不存在: " + testCase.getApiId());
        }

        // 2. 先构建请求：配置类错误（如未配 baseUrl）在此直接 400，不留下 run 记录
        BuiltRequest builtRequest = httpRequestBuilder.build(
                apiDefinition, testCase.getRequestParams(), baseUrlOverride);

        // 3. 创建 run 记录
        Long runId = runRecordService.startSingleCaseRun(caseId);

        // 4. 发送请求并计时（nanoTime 差值需换算毫秒）
        long startNanos = System.nanoTime();
        ExecutionResult executionResult;
        try {
            HttpResponse<String> response = httpClient.send(builtRequest.request(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = elapsedMillis(startNanos);

            // 响应体超限：截断存储并按字符边界处理（UTF-8 多字节字符不能被劈开）
            String rawBody = response.body();
            boolean truncated = rawBody != null
                    && rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_SNAPSHOT_BYTES;
            String bodyText = truncated ? truncateUtf8(rawBody, MAX_BODY_SNAPSHOT_BYTES) : rawBody;

            executionResult = ExecutionResult.builder()
                    .statusCode(response.statusCode())
                    .headers(toHeaderMap(response.headers()))
                    .bodyText(bodyText)
                    .bodyTruncated(truncated)
                    .elapsedMs(elapsedMs)
                    .build();
        } catch (IOException | InterruptedException e) {
            // 5. 失败分支：网络层异常同样要落库（ERROR），不能向上抛——否则 run 悬挂、接口 500
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            long elapsedMs = elapsedMillis(startNanos);
            String errorMessage = classify(e);

            TestResult errorResult = runRecordService.saveResult(
                    runId, caseId, "ERROR",
                    requestSnapshot(builtRequest),
                    null,
                    List.of(),
                    errorMessage,
                    elapsedMs);
            runRecordService.finishRun(runId, "ERROR", 0, 0, 1);
            return errorResult;
        }

        // 6. 断言判定（有响应才到这里）
        List<AssertionRule> rules = testCase.getAssertionRules() == null
                ? List.of() : testCase.getAssertionRules();
        List<AssertionOutcome> outcomes = assertionEngine.evaluate(rules, executionResult);
        boolean passed = assertionEngine.allPassed(outcomes);
        String status = passed ? "PASSED" : "FAILED";

        // 7. 响应快照 + 落库（快照字段名与 ExecutionResult 不完全同名）
        ResponseSnapshot responseSnapshot = ResponseSnapshot.builder()
                .statusCode(executionResult.getStatusCode())
                .headers(executionResult.getHeaders())
                .body(executionResult.getBodyText())
                .bodyTruncated(executionResult.isBodyTruncated())
                .elapsedMs(executionResult.getElapsedMs())
                .build();

        TestResult result = runRecordService.saveResult(
                runId, caseId, status,
                requestSnapshot(builtRequest),
                responseSnapshot,
                outcomes,
                null,
                executionResult.getElapsedMs());

        // 8. run 终态：PASSED→SUCCESS(1,0,0)；FAILED→FAILED(0,1,0)
        runRecordService.finishRun(runId, passed ? "SUCCESS" : "FAILED",
                passed ? 1 : 0, passed ? 0 : 1, 0);

        // 9. 返回落库后的结果
        return result;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private Map<String, String> toHeaderMap(HttpHeaders headers) {
        return headers.map().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private RequestSnapshot requestSnapshot(BuiltRequest builtRequest) {
        return RequestSnapshot.builder()
                .method(builtRequest.request().method())
                .url(builtRequest.url())
                .headers(builtRequest.headers())
                .body(builtRequest.bodyText())
                .timeoutMs(builtRequest.request().timeout().map(Duration::toMillis).orElse(null))
                .build();
    }

    /** 按 UTF-8 字符边界截断：截断点若劈开多字节字符，回退并排除该字符 */
    private String truncateUtf8(String text, int maxBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        int end = maxBytes;
        if ((bytes[end - 1] & 0xC0) == 0x80) {
            while (end > 0 && (bytes[end - 1] & 0xC0) == 0x80) {
                end--;
            }
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    /**
     * 网络层异常分类。
     *HttpConnectTimeoutException 是 HttpTimeoutException 的子类，必须先匹配。
     */
    private String classify(Exception e) {
        if (e instanceof HttpConnectTimeoutException) {
            return "连接超时";
        }
        if (e instanceof HttpTimeoutException) {
            return "读取超时";
        }
        if (e instanceof ConnectException) {
            return "连接被拒绝";
        }
        if (e instanceof UnknownHostException) {
            return "无法解析主机";
        }
        if (e instanceof InterruptedException) {
            return "执行被中断";
        }
        if (e instanceof IOException) {
            return "网络异常: " + e.getMessage();
        }
        return "未知异常: " + e.getClass().getSimpleName();
    }
}

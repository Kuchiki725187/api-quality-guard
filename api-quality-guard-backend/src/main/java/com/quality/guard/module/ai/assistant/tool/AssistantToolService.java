package com.quality.guard.module.ai.assistant.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.module.ai.report.service.PlatformReportService;
import com.quality.guard.module.ai.report.service.RunReportService;
import com.quality.guard.module.ai.report.vo.RunReportVO;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 助手只读工具数据
 * 返回给 Agent 精简摘要
 */
@Service
@RequiredArgsConstructor
public class AssistantToolService {

    private static final int API_LIMIT_MAX = 10;
    private static final int FAILURE_LIMIT_MAX = 20;
    private static final int TEXT_MAX = 200;

    private final PlatformReportService platformReportService;
    private final RunReportService runReportService;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final TestResultMapper testResultMapper;
    private final TestCaseMapper testCaseMapper;
    //将平台相关数据封装
    public Map<String, Object> platformReport() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", platformReportService.overview());
        result.put("quality", platformReportService.quality());
        return result;
    }
    //将大部分接口封装起来
    public List<Map<String, Object>> searchApis(String keyword, int limit) {
        int safeLimit = Math.clamp(limit, 1, API_LIMIT_MAX);
        String text = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<ApiDefinition> wrapper = new LambdaQueryWrapper<>();
        if (!text.isEmpty()) {
            String escaped = escapeLike(text);
            wrapper.and(condition -> condition
                    .like(ApiDefinition::getName, escaped)
                    .or()
                    .like(ApiDefinition::getPath, escaped));
        }
        wrapper.orderByDesc(ApiDefinition::getId)
                .last("LIMIT " + safeLimit);
        return apiDefinitionMapper.selectList(wrapper).stream()
                .map(api -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("apiId", api.getId());
                    item.put("name", api.getName());
                    item.put("method", api.getHttpMethod());
                    item.put("path", api.getPath());
                    item.put("description", truncate(api.getDescription()));
                    return item;
                })
                .toList();
    }

    public RunReportVO runReport(Long runId) {
        return runReportService.runReport(runId);
    }

    public List<Map<String, Object>> recentFailures(int limit) {
        int safeLimit = Math.clamp(limit, 1, FAILURE_LIMIT_MAX);
        List<TestResult> results = testResultMapper.selectList(new LambdaQueryWrapper<TestResult>()
                .in(TestResult::getStatus, "FAILED", "ERROR")
                .orderByDesc(TestResult::getId)
                .last("LIMIT " + safeLimit));
        if (results.isEmpty()) {
            return List.of();
        }
        List<Long> caseIds = results.stream()
                .map(TestResult::getCaseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, TestCase> cases = caseIds.isEmpty() ? Map.of()
                : testCaseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(TestCase::getId, Function.identity()));
        List<Long> apiIds = cases.values().stream()
                .map(TestCase::getApiId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ApiDefinition> apis = apiIds.isEmpty() ? Map.of()
                : apiDefinitionMapper.selectBatchIds(apiIds).stream()
                        .collect(Collectors.toMap(ApiDefinition::getId, Function.identity()));

        return results.stream()
                .map(result -> {
                    TestCase testCase = result.getCaseId() == null ? null : cases.get(result.getCaseId());
                    ApiDefinition api = testCase == null || testCase.getApiId() == null
                            ? null : apis.get(testCase.getApiId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("resultId", result.getId());
                    item.put("runId", result.getRunId());
                    item.put("caseId", result.getCaseId());
                    item.put("caseName", testCase == null ? null : testCase.getName());
                    item.put("apiId", api == null ? null : api.getId());
                    item.put("apiName", api == null ? null : api.getName());
                    item.put("method", api == null ? null : api.getHttpMethod());
                    item.put("path", api == null ? null : api.getPath());
                    item.put("status", result.getStatus());
                    item.put("errorMessage", truncate(result.getErrorMessage()));
                    item.put("failedAssertion", describeFailure(result));
                    item.put("durationMs", result.getDurationMs());
                    item.put("createdAt", result.getCreatedAt());
                    return item;
                })
                .toList();
    }

    private String describeFailure(TestResult result) {
        if ("ERROR".equals(result.getStatus())) {
            return null;
        }
        if (result.getAssertionResults() != null) {
            for (AssertionOutcome outcome : result.getAssertionResults()) {
                if (outcome.isPassed()) {
                    continue;
                }
                if (StringUtils.hasText(outcome.getMessage())) {
                    return truncate(outcome.getMessage());
                }
                String detail = switch (outcome.getType()) {
                    case "STATUS_CODE" -> "HTTP 状态码 期望 " + outcome.getExpected()
                            + "，实际 " + outcome.getActual();
                    case "RESPONSE_TIME" -> "响应时间 期望 < " + outcome.getExpected()
                            + "ms，实际 " + outcome.getActual() + "ms";
                    case "JSON_FIELD" -> outcome.getPath() + " 期望 " + outcome.getOperator()
                            + " " + outcome.getExpected() + "，实际 " + outcome.getActual();
                    default -> outcome.getType() + " 断言失败";
                };
                return truncate(detail);
            }
        }
        return truncate(result.getErrorMessage());
    }

    private String truncate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= TEXT_MAX ? compact : compact.substring(0, TEXT_MAX) + "…";
    }

    private String escapeLike(String text) {
        return text.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

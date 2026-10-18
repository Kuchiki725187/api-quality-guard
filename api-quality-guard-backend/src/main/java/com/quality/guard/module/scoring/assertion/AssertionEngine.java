package com.quality.guard.module.scoring.assertion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.ExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 断言引擎
 * 输入「断言规则 + 实际执行结果」，输出「每条断言的判定明细」。
 * 本引擎只做判定，不发请求、不落库；
 * 请求网络层失败（ERROR）时执行引擎不会调用本引擎，断言不判定。
 */
@Component
@RequiredArgsConstructor
public class AssertionEngine {

    private final ObjectMapper objectMapper;

    /**
     * 逐条判定断言规则。
     *
     * @param rules  用例的断言规则列表（STATUS_CODE / JSON_FIELD / RESPONSE_TIME）
     * @param result 执行结果（statusCode / headers / bodyText / elapsedMs）
     * @return 与 rules 等长的判定明细列表，顺序一致
     */
    public List<AssertionOutcome> evaluate(List<AssertionRule> rules, ExecutionResult result) {
        List<AssertionOutcome> outcomes = new ArrayList<>();
        for (AssertionRule rule : rules) {
            switch (rule.getType()) {
                case "STATUS_CODE" -> outcomes.add(evaluateStatusCode(rule, result));
                case "JSON_FIELD" -> outcomes.add(evaluateJsonField(rule, result));
                case "RESPONSE_TIME" -> outcomes.add(evaluateResponseTime(rule, result));
                default -> outcomes.add(AssertionOutcome.builder()
                        .type(rule.getType())
                        .passed(false)
                        .message("未知断言类型: " + rule.getType())
                        .build());
            }
        }
        return outcomes;
    }

    /**
     * 汇总规则：全部断言通过 → 用例 PASSED，任一不通过 → FAILED。
     */
    public boolean allPassed(List<AssertionOutcome> outcomes) {
        return outcomes.stream().allMatch(AssertionOutcome::isPassed);
    }

    //状态码断言
    private AssertionOutcome evaluateStatusCode(AssertionRule rule, ExecutionResult result) {
        // expected 是 Object（JSON 反序列化后为 Integer）；actual 理论上非空，仍做防御
        Integer actual = result.getStatusCode();
        Integer expected = rule.getExpected() instanceof Number number ? number.intValue() : null;
        boolean passed = actual != null && expected != null && actual.equals(expected);
        return AssertionOutcome.builder()
                .type("STATUS_CODE")
                .expected(rule.getExpected())
                .actual(actual)
                .passed(passed)
                .build();
    }

    // JSON 字段断言
    private AssertionOutcome evaluateJsonField(AssertionRule rule, ExecutionResult result) {
        AssertionOutcome.AssertionOutcomeBuilder builder = AssertionOutcome.builder()
                .type("JSON_FIELD")
                .path(rule.getPath())
                .operator(rule.getOperator())
                .expected(rule.getExpected());

        String bodyText = result.getBodyText();
        if (bodyText == null || bodyText.isBlank()) {
            return builder.actual("响应体为空").passed(false)
                    .message("响应体为空，无法求值")
                    .build();
        }

        JsonNode root;
        try {
            //将字符串格式的数据转换为可读取节点的JSON树结构
            root = objectMapper.readTree(bodyText);
        } catch (JsonProcessingException e) {
            // 请求是成功的（有响应），只是响应体不是 JSON —— 判为断言不通过而非系统异常
            return builder.actual("非 JSON 响应").passed(false)
                    .message("响应体解析失败: " + e.getOriginalMessage())
                    .build();
        }

        JsonNode node = evalPath(root, rule.getPath());
        Object actual = node == null || node.isNull() ? null : node.asText();
        boolean passed;
        String message = null;

        switch (rule.getOperator() == null ? "" : rule.getOperator()) {
            case "EXISTS" -> passed = node != null && !node.isNull();
            case "NOT_EXISTS" -> passed = node == null || node.isNull();
            // EQUALS / NOT_EQUALS 用文本比较：宽容处理 0 与 "0"、true 与 "true" 的类型差异
            case "EQUALS" -> passed = node != null && !node.isNull()
                    && String.valueOf(rule.getExpected()).equals(node.asText());
            case "NOT_EQUALS" -> passed = node != null && !node.isNull()
                    && !String.valueOf(rule.getExpected()).equals(node.asText());
            case "CONTAINS" -> passed = node != null && !node.isNull()
                    && node.asText().contains(String.valueOf(rule.getExpected()));
            case "GREATER_THAN", "LESS_THAN" -> {
                Double actualNumber = node != null && node.isNumber() ? node.asDouble() : null;
                Double expectedNumber = rule.getExpected() instanceof Number number ? number.doubleValue() : null;
                if (actualNumber == null || expectedNumber == null) {
                    passed = false;
                    message = "无法进行数值比较（实际值或期望值不是数字）";
                } else {
                    passed = "GREATER_THAN".equals(rule.getOperator())
                            ? actualNumber > expectedNumber
                            : actualNumber < expectedNumber;
                }
            }
            default -> {
                passed = false;
                message = "未知操作符: " + rule.getOperator();
            }
        }

        return builder.actual(actual).passed(passed).message(message).build();
    }

    //响应时间断言
    private AssertionOutcome evaluateResponseTime(AssertionRule rule, ExecutionResult result) {
        // 响应时间断言的期望值在 expectedMs 字段（不是 expected）
        Long expectedMs = rule.getExpectedMs();
        long actualMs = result.getElapsedMs();
        if (expectedMs == null) {
            return AssertionOutcome.builder()
                    .type("RESPONSE_TIME")
                    .actual(actualMs)
                    .passed(false)
                    .message("未配置期望响应时间(expectedMs)")
                    .build();
        }
        return AssertionOutcome.builder()
                .type("RESPONSE_TIME")
                .expected(expectedMs)
                .actual(actualMs)
                .passed(actualMs < expectedMs)
                .build();
    }

    /**
     * 简单 JSONPath 求值：支持形如 $.a.b.c 的点路径。
     * v1 局限：不支持数组下标（$[0]）、通配符等复杂语法。
     */
    private JsonNode evalPath(JsonNode root, String path) {
        if (path == null || path.isBlank() || !path.startsWith("$")) {
            return null;
        }
        String remaining = path.startsWith("$.") ? path.substring(2) : path.substring(1);
        //路径指向根节点,remaining是空字符串，返回整份JSON数据
        if (remaining.isEmpty()) {
            return root;
        }
        JsonNode node = root;
        for (String segment : remaining.split("\\.")) {
            if (node == null || node.isNull() || !node.isObject()) {
                return null;
            }
            node = node.get(segment);
        }
        return node;
    }
}

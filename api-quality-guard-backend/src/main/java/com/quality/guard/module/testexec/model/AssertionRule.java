package com.quality.guard.module.testexec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条断言规则，对应 test_case.assertion_rules 列（JSON 数组的元素）
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssertionRule {

    /** 断言类型：STATUS_CODE / JSON_FIELD / RESPONSE_TIME */
    private String type;

    /** JSON_FIELD 专用：JSONPath，如 $.code */
    private String path;

    /** 操作符：EQUALS / NOT_EQUALS / EXISTS / NOT_EXISTS / CONTAINS / GREATER_THAN / LESS_THAN */
    private String operator;

    /** 期望值（STATUS_CODE / JSON_FIELD 用） */
    private Object expected;

    /** RESPONSE_TIME 专用：期望耗时上限（毫秒） */
    private Long expectedMs;

    public static AssertionRule statusCode(int expected) {
        return AssertionRule.builder()
                .type("STATUS_CODE")
                .expected(expected)
                .build();
    }

    public static AssertionRule jsonField(String path, String operator, Object expected) {
        return AssertionRule.builder()
                .type("JSON_FIELD")
                .path(path)
                .operator(operator)
                .expected(expected)
                .build();
    }

    public static AssertionRule responseTimeLessThan(long ms) {
        return AssertionRule.builder()
                .type("RESPONSE_TIME")
                .operator("LESS_THAN")
                .expectedMs(ms)
                .build();
    }
}

package com.quality.guard.module.scoring.assertion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.ExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 断言引擎单元测试（纯逻辑，不依赖 Spring / 网络）。
 */
class AssertionEngineTest {

    private final AssertionEngine engine = new AssertionEngine(new ObjectMapper());

    private ExecutionResult result(String body, int status, long elapsedMs) {
        return ExecutionResult.builder()
                .statusCode(status)
                .bodyText(body)
                .elapsedMs(elapsedMs)
                .build();
    }

    private AssertionRule rule(String type, String path, String operator, Object expected, Long expectedMs) {
        return AssertionRule.builder()
                .type(type).path(path).operator(operator)
                .expected(expected).expectedMs(expectedMs)
                .build();
    }

    @Test
    void statusCodePassesAndFails() {
        AssertionOutcome ok = engine.evaluate(
                List.of(rule("STATUS_CODE", null, null, 200, null)), result("{}", 200, 10)).get(0);
        assertTrue(ok.isPassed());
        assertEquals(200, ok.getActual());

        AssertionOutcome bad = engine.evaluate(
                List.of(rule("STATUS_CODE", null, null, 200, null)), result("{}", 500, 10)).get(0);
        assertFalse(bad.isPassed());
        assertEquals(500, bad.getActual());
    }

    @Test
    void jsonFieldEqualsSupportsNumbersNestedPathsAndStrings() {
        // 数字相等（文本比较，宽容类型）
        assertTrue(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.code", "EQUALS", 200, null)),
                result("{\"code\":200}", 200, 10)).get(0).isPassed());

        // 嵌套点路径
        assertTrue(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.data.name", "EQUALS", "张三", null)),
                result("{\"data\":{\"name\":\"张三\"}}", 200, 10)).get(0).isPassed());

        // 值不相等
        assertFalse(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.code", "EQUALS", 0, null)),
                result("{\"code\":200}", 200, 10)).get(0).isPassed());
    }

    @Test
    void jsonFieldExistsAndNotExists() {
        assertTrue(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.data.id", "EXISTS", null, null)),
                result("{\"data\":{\"id\":1}}", 200, 10)).get(0).isPassed());
        assertFalse(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.data.missing", "EXISTS", null, null)),
                result("{\"data\":{\"id\":1}}", 200, 10)).get(0).isPassed());
        assertTrue(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.data.missing", "NOT_EXISTS", null, null)),
                result("{\"data\":{\"id\":1}}", 200, 10)).get(0).isPassed());
    }

    @Test
    void jsonFieldContainsAndComparisons() {
        assertTrue(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.message", "CONTAINS", "success", null)),
                result("{\"message\":\"operation success\"}", 200, 10)).get(0).isPassed());
        assertTrue(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.data.count", "GREATER_THAN", 5, null)),
                result("{\"data\":{\"count\":10}}", 200, 10)).get(0).isPassed());
        assertFalse(engine.evaluate(
                List.of(rule("JSON_FIELD", "$.data.count", "LESS_THAN", 5, null)),
                result("{\"data\":{\"count\":10}}", 200, 10)).get(0).isPassed());
    }

    @Test
    void jsonFieldOnNonJsonBodyFailsWithoutThrowing() {
        AssertionOutcome outcome = engine.evaluate(
                List.of(rule("JSON_FIELD", "$.code", "EQUALS", 0, null)),
                result("<html>not json</html>", 200, 10)).get(0);
        assertFalse(outcome.isPassed());
        assertEquals("非 JSON 响应", outcome.getActual());
    }

    @Test
    void jsonFieldOnEmptyBodyFails() {
        AssertionOutcome outcome = engine.evaluate(
                List.of(rule("JSON_FIELD", "$.code", "EQUALS", 0, null)),
                result(null, 200, 10)).get(0);
        assertFalse(outcome.isPassed());
        assertEquals("响应体为空", outcome.getActual());
    }

    @Test
    void responseTimePassesAndFails() {
        AssertionOutcome ok = engine.evaluate(
                List.of(rule("RESPONSE_TIME", null, "LESS_THAN", null, 500L)),
                result("{}", 200, 128)).get(0);
        assertTrue(ok.isPassed());
        assertEquals(128L, ok.getActual());

        AssertionOutcome bad = engine.evaluate(
                List.of(rule("RESPONSE_TIME", null, "LESS_THAN", null, 100L)),
                result("{}", 200, 250)).get(0);
        assertFalse(bad.isPassed());
    }

    @Test
    void responseTimeWithoutExpectedMsFailsWithMessage() {
        AssertionOutcome outcome = engine.evaluate(
                List.of(rule("RESPONSE_TIME", null, "LESS_THAN", null, null)),
                result("{}", 200, 100)).get(0);
        assertFalse(outcome.isPassed());
        assertTrue(outcome.getMessage().contains("expectedMs"));
    }

    @Test
    void unknownTypeAndOperatorFailWithMessage() {
        AssertionOutcome unknownType = engine.evaluate(
                List.of(rule("SOMETHING_ELSE", null, null, null, null)),
                result("{}", 200, 10)).get(0);
        assertFalse(unknownType.isPassed());
        assertTrue(unknownType.getMessage().contains("未知断言类型"));

        AssertionOutcome unknownOperator = engine.evaluate(
                List.of(rule("JSON_FIELD", "$.code", "REGEX", 1, null)),
                result("{\"code\":1}", 200, 10)).get(0);
        assertFalse(unknownOperator.isPassed());
        assertTrue(unknownOperator.getMessage().contains("未知操作符"));
    }

    @Test
    void allPassedSummarizesCorrectly() {
        List<AssertionOutcome> allOk = engine.evaluate(List.of(
                        rule("STATUS_CODE", null, null, 200, null),
                        rule("JSON_FIELD", "$.code", "EQUALS", 0, null)),
                result("{\"code\":0}", 200, 10));
        assertTrue(engine.allPassed(allOk));

        List<AssertionOutcome> withFailure = engine.evaluate(List.of(
                        rule("STATUS_CODE", null, null, 200, null),
                        rule("JSON_FIELD", "$.code", "EQUALS", 999, null)),
                result("{\"code\":0}", 200, 10));
        assertFalse(engine.allPassed(withFailure));
    }

    @Test
    void missingPathYieldsNullActual() {
        AssertionOutcome outcome = engine.evaluate(
                List.of(rule("JSON_FIELD", "$.missing", "EQUALS", 1, null)),
                result("{\"code\":0}", 200, 10)).get(0);
        assertFalse(outcome.isPassed());
        assertNull(outcome.getActual());
    }
}

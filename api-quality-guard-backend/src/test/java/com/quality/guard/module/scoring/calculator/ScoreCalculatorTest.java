package com.quality.guard.module.scoring.calculator;

import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 评分计算器单元测试（纯逻辑，不依赖 Spring / 数据库）。
 */
class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator(null, null);

    private TestResult result(String status, List<AssertionOutcome> outcomes) {
        TestResult result = new TestResult();
        result.setStatus(status);
        result.setAssertionResults(outcomes);
        return result;
    }

    private AssertionOutcome statusOutcome(boolean passed) {
        return AssertionOutcome.builder().type("STATUS_CODE").passed(passed).build();
    }

    private AssertionOutcome timeOutcome(boolean passed) {
        return AssertionOutcome.builder().type("RESPONSE_TIME").passed(passed).build();
    }

    @Test
    void allPassedWithoutPerformanceRedeploysWeight() {
        ScoreDetail detail = calculator.calculate(List.of(
                result("PASSED", List.of(statusOutcome(true))),
                result("PASSED", List.of(statusOutcome(true)))));

        assertEquals(100.0, detail.getTotalScore());
        assertEquals("优秀", detail.getGrade());
        assertNull(detail.getPerformanceScore());
        // 性能维度缺席：0.7/0.85 = 0.8235，0.15/0.85 = 0.1765
        assertEquals(0.8235, detail.getFunctionalWeight());
        assertEquals(0.1765, detail.getStabilityWeight());
        assertEquals(0.0, detail.getPerformanceWeight());
    }

    @Test
    void halfFailedScoresLow() {
        ScoreDetail detail = calculator.calculate(List.of(
                result("PASSED", List.of(statusOutcome(true))),
                result("FAILED", List.of(statusOutcome(false)))));

        assertEquals(50.0, detail.getFunctionalScore());
        assertEquals(100.0, detail.getStabilityScore());
        // 50 * 0.8235 + 100 * 0.1765 ≈ 58.8
        assertEquals(58.8, detail.getTotalScore());
        assertEquals("不合格", detail.getGrade());
    }

    @Test
    void performanceDimensionIncludedWhenPresent() {
        ScoreDetail detail = calculator.calculate(List.of(
                result("PASSED", List.of(statusOutcome(true), timeOutcome(true))),
                result("PASSED", List.of(statusOutcome(true), timeOutcome(true))),
                result("FAILED", List.of(statusOutcome(false), timeOutcome(false)))));

        assertEquals(66.7, detail.getFunctionalScore());
        assertEquals(66.7, detail.getPerformanceScore());
        assertEquals(100.0, detail.getStabilityScore());
        assertEquals(71.7, detail.getTotalScore());
        assertEquals("及格", detail.getGrade());
        assertEquals(3, detail.getPerfTotal());
        assertEquals(2, detail.getPerfPassed());
    }

    @Test
    void errorsReduceStability() {
        ScoreDetail detail = calculator.calculate(List.of(
                result("PASSED", List.of(statusOutcome(true))),
                result("ERROR", List.of())));

        assertEquals(50.0, detail.getFunctionalScore());
        assertEquals(50.0, detail.getStabilityScore());
        assertEquals(50.0, detail.getTotalScore());
    }

    @Test
    void gradeBoundaries() {
        assertEquals("优秀", ScoreCalculator.gradeOf(90));
        assertEquals("良好", ScoreCalculator.gradeOf(89.9));
        assertEquals("良好", ScoreCalculator.gradeOf(80));
        assertEquals("及格", ScoreCalculator.gradeOf(79.9));
        assertEquals("及格", ScoreCalculator.gradeOf(60));
        assertEquals("不合格", ScoreCalculator.gradeOf(59.9));
    }
}

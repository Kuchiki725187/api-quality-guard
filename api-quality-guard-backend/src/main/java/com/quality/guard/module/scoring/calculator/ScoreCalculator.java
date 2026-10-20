package com.quality.guard.module.scoring.calculator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 质量评分计算器（M2 核心）：
 * 三维度加权评分——功能通过率 70% + 性能达标率 15% + 稳定性 15%。
 * 计算口径：
 * - 功能通过率 = PASSED 用例数 / 用例总数 × 100（ERROR 计入分母、不计入分子）
 * - 性能达标率 = 含响应时间断言的用例中"全部响应时间断言通过"的比例 × 100；
 *   本次运行没有响应时间断言时该维度不计入，权重按比例摊回功能/稳定两维（0.7/0.85、0.15/0.85）
 * - 稳定性 = (1 − ERROR 用例数 / 用例总数) × 100
 * - 总分 = Σ(维度分 × 实际权重)，保留 1 位小数；等级：≥90 优秀 / ≥80 良好 / ≥60 及格 / <60 不合格
 * - 明细记录维度得分、实际权重与计算基数，任何人可据此复算
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private static final double FUNCTIONAL_WEIGHT = 0.70;
    private static final double PERFORMANCE_WEIGHT = 0.15;
    private static final double STABILITY_WEIGHT = 0.15;

    private final TestRunMapper testRunMapper;
    private final TestResultMapper testResultMapper;

    /** 对终态 SUCCESS/FAILED 的 run 计算评分并落库；其他状态不评分（数据不完整） */
    public ScoreDetail calculateForRun(Long runId) {
        TestRun run = testRunMapper.selectById(runId);
        if (run == null) {
            return null;
        }
        if (!"SUCCESS".equals(run.getStatus()) && !"FAILED".equals(run.getStatus())) {
            return null;
        }
        List<TestResult> results = testResultMapper.selectList(
                new LambdaQueryWrapper<TestResult>().eq(TestResult::getRunId, runId));
        if (results.isEmpty()) {
            return null;
        }

        ScoreDetail detail = calculate(results);
        // score_detail 是 JSON 列，必须通过实体的 TypeHandler 序列化写入
        run.setScore(BigDecimal.valueOf(detail.getTotalScore()));
        run.setScoreDetail(detail);
        testRunMapper.updateById(run);
        return detail;
    }

    /** 纯计算（不落库，便于单元测试） */
    public ScoreDetail calculate(List<TestResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(r -> "PASSED".equals(r.getStatus())).count();
        int errors = (int) results.stream().filter(r -> "ERROR".equals(r.getStatus())).count();

        double functional = passed * 100.0 / total;
        double stability = (1.0 - (double) errors / total) * 100.0;

        // 性能维度：只统计包含响应时间断言的用例
        int perfTotal = 0;
        int perfPassed = 0;
        for (TestResult result : results) {
            List<AssertionOutcome> outcomes = result.getAssertionResults();
            if (outcomes == null || outcomes.isEmpty()) {
                continue;
            }
            List<AssertionOutcome> timeOutcomes = outcomes.stream()
                    .filter(o -> "RESPONSE_TIME".equals(o.getType()))
                    .toList();
            if (timeOutcomes.isEmpty()) {
                continue;
            }
            perfTotal++;
            if (timeOutcomes.stream().allMatch(AssertionOutcome::isPassed)) {
                perfPassed++;
            }
        }

        double functionalWeight = FUNCTIONAL_WEIGHT;
        double performanceWeight = PERFORMANCE_WEIGHT;
        double stabilityWeight = STABILITY_WEIGHT;
        Double performance = null;
        double totalScore;
        if (perfTotal > 0) {
            performance = perfPassed * 100.0 / perfTotal;
            totalScore = functional * functionalWeight
                    + performance * performanceWeight
                    + stability * stabilityWeight;
        } else {
            // 无响应时间断言：性能权重按比例摊回功能与稳定两维
            double remaining = FUNCTIONAL_WEIGHT + STABILITY_WEIGHT;
            functionalWeight = FUNCTIONAL_WEIGHT / remaining;
            stabilityWeight = STABILITY_WEIGHT / remaining;
            performanceWeight = 0;
            totalScore = functional * functionalWeight + stability * stabilityWeight;
        }

        double roundedTotal = round1(totalScore);
        return ScoreDetail.builder()
                .totalScore(roundedTotal)
                .grade(gradeOf(roundedTotal))
                .functionalScore(round1(functional))
                .performanceScore(performance == null ? null : round1(performance))
                .stabilityScore(round1(stability))
                .functionalWeight(round4(functionalWeight))
                .performanceWeight(round4(performanceWeight))
                .stabilityWeight(round4(stabilityWeight))
                .totalCount(total)
                .passedCount(passed)
                .errorCount(errors)
                .perfTotal(perfTotal)
                .perfPassed(perfPassed)
                .build();
    }

    public static String gradeOf(double score) {
        if (score >= 90) {
            return "优秀";
        }
        if (score >= 80) {
            return "良好";
        }
        if (score >= 60) {
            return "及格";
        }
        return "不合格";
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}

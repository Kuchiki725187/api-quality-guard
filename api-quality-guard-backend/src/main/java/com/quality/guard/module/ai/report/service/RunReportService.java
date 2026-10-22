package com.quality.guard.module.ai.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.ai.report.vo.RunReportVO;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 运行报告聚合
 * 执行统计取自 test_run，响应耗时与来源分布按本次 run 的 test_result 明细现场计算，
 * 保证报告与明细数据一致；逻辑删除的用例无法反查来源，归入 UNKNOWN
 */
@Service
@RequiredArgsConstructor
public class RunReportService {

    private static final List<String> GENERATION_TYPE_ORDER = List.of("RULE", "MANUAL", "AI", "UNKNOWN");
    private static final String STATUS_PASSED = "PASSED";
    private static final String STATUS_ERROR = "ERROR";

    private final TestRunMapper testRunMapper;
    private final TestResultMapper testResultMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestPlanMapper testPlanMapper;

    public RunReportVO runReport(Long runId) {
        TestRun run = testRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行任务不存在: " + runId);
        }
        List<TestResult> results = testResultMapper.selectList(
                new LambdaQueryWrapper<TestResult>().eq(TestResult::getRunId, runId));

        List<Long> responseDurations = results.stream()
                .filter(result -> !STATUS_ERROR.equals(result.getStatus()))
                .map(TestResult::getDurationMs)
                .filter(Objects::nonNull)
                .toList();

        RunReportVO vo = new RunReportVO();
        vo.setRunId(run.getId());
        vo.setPlanName(loadPlanName(run.getPlanId()));
        vo.setStatus(run.getStatus());
        vo.setTotalCount(run.getTotalCount());
        vo.setSuccessCount(run.getSuccessCount());
        vo.setFailedCount(run.getFailedCount());
        vo.setErrorCount(run.getErrorCount());
        vo.setPassRate(passRate(run));
        vo.setDurationMs(runDuration(run));
        vo.setScore(run.getScore());
        vo.setScoreDetail(run.getScoreDetail());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setResponseTime(responseTime(responseDurations));
        vo.setDurationDistribution(durationDistribution(responseDurations));
        vo.setGenerationTypeBreakdown(generationTypeBreakdown(results));
        return vo;
    }

    private String loadPlanName(Long planId) {
        if (planId == null) {
            return null;
        }
        TestPlan plan = testPlanMapper.selectById(planId);
        return plan == null ? null : plan.getName();
    }

    private Double passRate(TestRun run) {
        int total = run.getTotalCount() == null ? 0 : run.getTotalCount();
        if (total == 0) {
            return 0.0;
        }
        int success = run.getSuccessCount() == null ? 0 : run.getSuccessCount();
        return Math.round(success * 10000.0 / total) / 10000.0;
    }

    private Long runDuration(TestRun run) {
        if (run.getStartedAt() == null || run.getFinishedAt() == null) {
            return null;
        }
        return Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis();
    }

    private RunReportVO.ResponseTime responseTime(List<Long> durations) {
        if (durations.isEmpty()) {
            return null;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long sum = 0;
        for (long duration : durations) {
            min = Math.min(min, duration);
            max = Math.max(max, duration);
            sum += duration;
        }
        return new RunReportVO.ResponseTime(min, Math.round((double) sum / durations.size()), max);
    }

    private List<RunReportVO.DurationBucket> durationDistribution(List<Long> durations) {
        int fast = 0;
        int normal = 0;
        int slow = 0;
        int verySlow = 0;
        for (long duration : durations) {
            if (duration <= 100) {
                fast++;
            } else if (duration <= 500) {
                normal++;
            } else if (duration <= 1000) {
                slow++;
            } else {
                verySlow++;
            }
        }
        return List.of(
                new RunReportVO.DurationBucket("0-100ms", fast),
                new RunReportVO.DurationBucket("100-500ms", normal),
                new RunReportVO.DurationBucket("500-1000ms", slow),
                new RunReportVO.DurationBucket(">1000ms", verySlow));
    }
    //统计不同生成方式生成的测试用例的执行结果
    private List<RunReportVO.GenerationTypeStat> generationTypeBreakdown(List<TestResult> results) {
        //加载用例的生成方式
        Map<Long, String> generationTypes = loadGenerationTypes(results);
        //统计容器，int[1]表示某个生成方式总的用例，int[2]表示通过的用例
        Map<String, int[]> stats = new LinkedHashMap<>();
        for (String type : GENERATION_TYPE_ORDER) {
            stats.put(type, new int[2]);
        }
        for (TestResult result : results) {
            String type = generationTypes.getOrDefault(result.getCaseId(), "UNKNOWN");
            int[] stat = stats.computeIfAbsent(type, key -> new int[2]);
            //统计总数
            stat[0]++;
            //统计通过数
            if (STATUS_PASSED.equals(result.getStatus())) {
                stat[1]++;
            }
        }
        return stats.entrySet().stream()
                .filter(entry -> entry.getValue()[0] > 0)
                .map(entry -> new RunReportVO.GenerationTypeStat(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    private Map<Long, String> loadGenerationTypes(List<TestResult> results) {
        List<Long> caseIds = results.stream()
                .map(TestResult::getCaseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        return testCaseMapper.selectBatchIds(caseIds).stream()
                .filter(testCase -> testCase.getGenerationType() != null)
                .collect(Collectors.toMap(TestCase::getId, TestCase::getGenerationType));
    }
}

package com.quality.guard.module.ai.report;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.ai.report.service.RunReportService;
import com.quality.guard.module.ai.report.vo.RunReportVO;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行报告聚合集成测试（同步只读逻辑，@Transactional 回滚）。
 */
@SpringBootTest
@Transactional
class RunReportIntegrationTest {

    @Autowired
    private RunReportService runReportService;

    @Autowired
    private TestRunMapper testRunMapper;

    @Autowired
    private TestResultMapper testResultMapper;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private TestPlanMapper testPlanMapper;

    @Test
    void aggregatesRunStatisticsAndResponseDistribution() {
        TestPlan plan = createPlan("IT-订单回归");
        TestRun run = createRun(plan.getId(), "FAILED", 4, 2, 1, 1);
        Long ruleCaseId = createCase("RULE");
        Long aiCaseId = createCase("AI");
        Long manualCaseId = createCase("MANUAL");

        createResult(run.getId(), ruleCaseId, "PASSED", 80L);
        createResult(run.getId(), aiCaseId, "PASSED", 200L);
        createResult(run.getId(), manualCaseId, "FAILED", 600L);
        createResult(run.getId(), ruleCaseId, "ERROR", 1500L);

        RunReportVO report = runReportService.runReport(run.getId());

        assertEquals(run.getId(), report.getRunId());
        assertEquals("IT-订单回归", report.getPlanName());
        assertEquals("FAILED", report.getStatus());
        assertEquals(4, report.getTotalCount());
        assertEquals(2, report.getSuccessCount());
        assertEquals(1, report.getFailedCount());
        assertEquals(1, report.getErrorCount());
        assertEquals(0.5, report.getPassRate(), 1e-9);
        assertEquals(2000L, report.getDurationMs());

        // ERROR 结果没有 HTTP 响应，不参与响应时间统计
        assertNotNull(report.getResponseTime());
        assertEquals(80L, report.getResponseTime().getMinMs());
        assertEquals(293L, report.getResponseTime().getAvgMs());
        assertEquals(600L, report.getResponseTime().getMaxMs());
        assertEquals(List.of(1, 1, 1, 0), report.getDurationDistribution().stream()
                .map(RunReportVO.DurationBucket::getCount)
                .toList());

        List<RunReportVO.GenerationTypeStat> breakdown = report.getGenerationTypeBreakdown();
        assertEquals(3, breakdown.size());
        assertEquals("RULE", breakdown.get(0).getGenerationType());
        assertEquals(2, breakdown.get(0).getTotal());
        assertEquals(1, breakdown.get(0).getPassed());
        assertEquals("MANUAL", breakdown.get(1).getGenerationType());
        assertEquals(1, breakdown.get(1).getTotal());
        assertEquals(0, breakdown.get(1).getPassed());
        assertEquals("AI", breakdown.get(2).getGenerationType());
        assertEquals(1, breakdown.get(2).getTotal());
        assertEquals(1, breakdown.get(2).getPassed());
    }

    @Test
    void emptyRunReturnsZeroedReport() {
        TestRun run = createRun(null, "SUCCESS", 0, 0, 0, 0);

        RunReportVO report = runReportService.runReport(run.getId());

        assertNull(report.getPlanName());
        assertEquals(0.0, report.getPassRate(), 1e-9);
        assertNull(report.getResponseTime());
        assertEquals(4, report.getDurationDistribution().size());
        assertTrue(report.getDurationDistribution().stream()
                .allMatch(bucket -> bucket.getCount() == 0));
        assertTrue(report.getGenerationTypeBreakdown().isEmpty());
    }

    @Test
    void missingCaseFallsBackToUnknownGenerationType() {
        TestRun run = createRun(null, "SUCCESS", 1, 1, 0, 0);
        createResult(run.getId(), 999_999_999L, "PASSED", 50L);

        RunReportVO report = runReportService.runReport(run.getId());

        assertEquals(1, report.getGenerationTypeBreakdown().size());
        assertEquals("UNKNOWN", report.getGenerationTypeBreakdown().get(0).getGenerationType());
        assertEquals(1, report.getGenerationTypeBreakdown().get(0).getPassed());
    }

    @Test
    void missingRunThrowsNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> runReportService.runReport(-1L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
    }

    private TestPlan createPlan(String name) {
        TestPlan plan = new TestPlan();
        plan.setName(name);
        testPlanMapper.insert(plan);
        return plan;
    }

    private TestRun createRun(Long planId, String status, int total, int success, int failed, int error) {
        TestRun run = new TestRun();
        run.setPlanId(planId);
        run.setStatus(status);
        run.setTotalCount(total);
        run.setSuccessCount(success);
        run.setFailedCount(failed);
        run.setErrorCount(error);
        run.setStartedAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 1, 0, 0, 2));
        testRunMapper.insert(run);
        return run;
    }

    private Long createCase(String generationType) {
        TestCase testCase = new TestCase();
        testCase.setApiId(1L);
        testCase.setName("IT-用例-" + UUID.randomUUID().toString().substring(0, 8));
        testCase.setGenerationType(generationType);
        testCaseMapper.insert(testCase);
        return testCase.getId();
    }

    private void createResult(Long runId, Long caseId, String status, Long durationMs) {
        TestResult result = new TestResult();
        result.setRunId(runId);
        result.setCaseId(caseId);
        result.setStatus(status);
        result.setDurationMs(durationMs);
        testResultMapper.insert(result);
    }
}

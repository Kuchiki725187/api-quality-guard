package com.quality.guard.module.ai.report;

import com.quality.guard.module.ai.report.service.PlatformReportService;
import com.quality.guard.module.ai.report.vo.PlatformOverviewVO;
import com.quality.guard.module.ai.report.vo.PlatformQualityVO;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.ParamDef;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台概览与四维质量评分集成测试。
 * 概览是全局聚合，为了断言确定，测试事务内先清空相关表（回滚后恢复原数据）。
 */
@SpringBootTest
@Transactional
class PlatformReportIntegrationTest {

    @Autowired
    private PlatformReportService platformReportService;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private TestPlanMapper testPlanMapper;

    @Autowired
    private TestRunMapper testRunMapper;

    @Autowired
    private TestResultMapper testResultMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanPlatformData() {
        jdbcTemplate.execute("DELETE FROM ai_diagnosis");
        jdbcTemplate.execute("DELETE FROM quality_gate_record");
        jdbcTemplate.execute("DELETE FROM test_plan_case");
        jdbcTemplate.execute("DELETE FROM test_result");
        jdbcTemplate.execute("DELETE FROM test_case");
        jdbcTemplate.execute("DELETE FROM test_run");
        jdbcTemplate.execute("DELETE FROM test_plan");
        jdbcTemplate.execute("DELETE FROM api_definition");
    }

    @Test
    void overviewAndQualityReflectRealData() {
        ApiDefinition compliant1 = createApi("用户登录", "POST", "/login", "用户登录接口",
                List.of(param("username", "string")), responseSchema());
        createApi("未完成接口", "GET", "/incomplete", null, null, null);
        ApiDefinition compliant2 = createApi("订单查询", "GET", "/orders/{id}", "按 id 查询订单",
                null, responseSchema());

        TestCase ruleCase = createCase(compliant1.getId(), "RULE");
        TestCase manualCase = createCase(compliant2.getId(), "MANUAL");
        TestPlan plan = createPlan("订单回归");

        LocalDateTime now = LocalDateTime.now();
        TestRun todaySuccess = createRun(plan.getId(), "SUCCESS", "90.0", now);
        TestRun todayFailed = createRun(plan.getId(), "FAILED", "80.0", now);
        TestRun lastWeek = createRun(plan.getId(), "SUCCESS", "70.0", now.minusDays(8));

        createResult(todaySuccess.getId(), ruleCase.getId(), "PASSED", 100,
                List.of(statusOutcome(true), responseTimeOutcome(true, 100)), now);
        createResult(todayFailed.getId(), ruleCase.getId(), "FAILED", 800,
                List.of(responseTimeOutcome(false, 800)), now);
        createResult(lastWeek.getId(), manualCase.getId(), "PASSED", 50,
                List.of(responseTimeOutcome(true, 50)), now.minusDays(8));
        createResult(todayFailed.getId(), manualCase.getId(), "ERROR", 5000, null, now);

        PlatformOverviewVO overview = platformReportService.overview();
        assertEquals(3, overview.getApiCount());
        assertEquals(2, overview.getCaseCount());
        assertEquals(1, overview.getPlanCount());
        assertEquals(3, overview.getRunCount());
        assertEquals(2, overview.getTodayRunCount());
        assertEquals(2, overview.getCoveredApiCount());
        assertEquals(66.7, overview.getCoverage(), 1e-9);
        assertEquals(3, overview.getApiWeekAdded());
        assertEquals(80.0, overview.getAvgScore(), 1e-9);
        assertEquals(15.0, overview.getWeekDelta(), 1e-9);
        assertEquals(3, overview.getRecentRuns().size());
        assertEquals(todayFailed.getId(), overview.getLatestRun().getRunId());

        List<PlatformOverviewVO.ScorePoint> trend = overview.getScoreTrend();
        assertEquals(14, trend.size());
        assertEquals(85.0, scoreOf(trend, LocalDate.now()), 1e-9);
        assertEquals(70.0, scoreOf(trend, LocalDate.now().minusDays(8)), 1e-9);

        List<PlatformOverviewVO.RiskApi> risks = overview.getRiskApis();
        assertEquals(2, risks.size());
        assertEquals("高", risks.get(0).getRisk());
        assertEquals(compliant2.getId(), risks.get(0).getApiId());
        assertEquals("中", risks.get(1).getRisk());
        assertEquals(compliant1.getId(), risks.get(1).getApiId());
        assertTrue(risks.get(1).getIssue().contains("响应时间"));

        PlatformQualityVO quality = platformReportService.quality();
        assertEquals(66.7, dimension(quality, "specification").getScore(), 1e-9);
        assertEquals(66.7, dimension(quality, "coverage").getScore(), 1e-9);
        assertEquals(66.7, dimension(quality, "performance").getScore(), 1e-9);
        assertEquals(75.0, dimension(quality, "stability").getScore(), 1e-9);
        assertEquals(68.8, quality.getTotal(), 1e-9);
        assertEquals("及格", quality.getGrade());
        assertEquals(15.0, quality.getWeekDelta(), 1e-9);
        assertEquals(14, quality.getScoreTrend().size());
    }

    @Test
    void unavailableDimensionsRedistributeWeight() {
        createApi("合规接口", "GET", "/ok", "有描述", null, responseSchema());

        PlatformQualityVO quality = platformReportService.quality();

        assertEquals(100.0, dimension(quality, "specification").getScore(), 1e-9);
        assertEquals(0.0, dimension(quality, "coverage").getScore(), 1e-9);
        assertNull(dimension(quality, "performance").getScore());
        assertNull(dimension(quality, "stability").getScore());
        assertEquals(50.0, quality.getTotal(), 1e-9);
        assertEquals("不合格", quality.getGrade());
        assertNull(quality.getWeekDelta());

        PlatformOverviewVO overview = platformReportService.overview();
        assertEquals(0.0, overview.getCoverage(), 1e-9);
        assertNull(overview.getAvgScore());
        assertNull(overview.getWeekDelta());
        assertTrue(overview.getRecentRuns().isEmpty());
        assertTrue(overview.getRiskApis().isEmpty());
    }

    @Test
    void emptyPlatformReturnsNoData() {
        PlatformQualityVO quality = platformReportService.quality();

        assertEquals(0.0, quality.getTotal(), 1e-9);
        assertEquals("暂无数据", quality.getGrade());
        assertTrue(quality.getDimensions().stream().allMatch(dimension -> dimension.getScore() == null));

        PlatformOverviewVO overview = platformReportService.overview();
        assertEquals(0, overview.getApiCount());
        assertEquals(0.0, overview.getCoverage(), 1e-9);
        assertEquals(14, overview.getScoreTrend().size());
        assertTrue(overview.getScoreTrend().stream().allMatch(point -> point.getScore() == null));
    }

    private ApiDefinition createApi(String name, String method, String path, String description,
                                    List<ParamDef> params, ResponseSchemaDef responseSchema) {
        ApiDefinition api = new ApiDefinition();
        api.setName(name);
        api.setHttpMethod(method);
        api.setPath(path);
        api.setDescription(description);
        if (params != null) {
            RequestSchemaDef requestSchema = new RequestSchemaDef();
            requestSchema.setParams(params);
            api.setRequestSchema(requestSchema);
        }
        api.setResponseSchema(responseSchema);
        api.setCreatedAt(LocalDateTime.now());
        apiDefinitionMapper.insert(api);
        return api;
    }

    private ParamDef param(String name, String type) {
        ParamDef param = new ParamDef();
        param.setName(name);
        param.setIn("body");
        param.setType(type);
        return param;
    }

    private ResponseSchemaDef responseSchema() {
        ResponseSchemaDef schema = new ResponseSchemaDef();
        schema.setSuccessStatusCode(200);
        return schema;
    }

    private TestCase createCase(Long apiId, String generationType) {
        TestCase testCase = new TestCase();
        testCase.setApiId(apiId);
        testCase.setName("IT-平台报告用例-" + generationType);
        testCase.setGenerationType(generationType);
        testCaseMapper.insert(testCase);
        return testCase;
    }

    private TestPlan createPlan(String name) {
        TestPlan plan = new TestPlan();
        plan.setName(name);
        testPlanMapper.insert(plan);
        return plan;
    }

    private TestRun createRun(Long planId, String status, String score, LocalDateTime time) {
        TestRun run = new TestRun();
        run.setPlanId(planId);
        run.setStatus(status);
        run.setTotalCount(1);
        run.setSuccessCount("SUCCESS".equals(status) ? 1 : 0);
        run.setFailedCount("FAILED".equals(status) ? 1 : 0);
        run.setErrorCount("ERROR".equals(status) ? 1 : 0);
        run.setScore(new BigDecimal(score));
        run.setStartedAt(time.minusSeconds(1));
        run.setFinishedAt(time);
        run.setCreatedAt(time);
        testRunMapper.insert(run);
        return run;
    }

    private void createResult(Long runId, Long caseId, String status, long durationMs,
                              List<AssertionOutcome> outcomes, LocalDateTime time) {
        TestResult result = new TestResult();
        result.setRunId(runId);
        result.setCaseId(caseId);
        result.setStatus(status);
        result.setDurationMs(durationMs);
        result.setAssertionResults(outcomes);
        result.setCreatedAt(time);
        testResultMapper.insert(result);
    }

    private AssertionOutcome statusOutcome(boolean passed) {
        return AssertionOutcome.builder()
                .type("STATUS_CODE")
                .expected(200)
                .actual(200)
                .passed(passed)
                .build();
    }

    private AssertionOutcome responseTimeOutcome(boolean passed, long actualMs) {
        return AssertionOutcome.builder()
                .type("RESPONSE_TIME")
                .expected(500L)
                .actual(actualMs)
                .passed(passed)
                .build();
    }

    private PlatformQualityVO.Dimension dimension(PlatformQualityVO quality, String key) {
        return quality.getDimensions().stream()
                .filter(dimension -> key.equals(dimension.getKey()))
                .findFirst()
                .orElseThrow();
    }

    private Double scoreOf(List<PlatformOverviewVO.ScorePoint> trend, LocalDate day) {
        String key = day.format(DateTimeFormatter.ofPattern("MM-dd"));
        return trend.stream()
                .filter(point -> key.equals(point.getDate()))
                .findFirst()
                .map(PlatformOverviewVO.ScorePoint::getScore)
                .orElseThrow();
    }
}

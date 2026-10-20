package com.quality.guard.module.testexec.runner;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.plan.entity.PlanCase;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.PlanCaseMapper;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import com.quality.guard.module.testexec.runner.scheduler.PlanRunService;
import com.quality.guard.module.testexec.runner.scheduler.StaleRunRecovery;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计划批量调度集成测试。
 * 注意：异步执行在独立线程/连接中进行，不能用 @Transactional 回滚，
 * 因此使用 "IT-" 前缀标记测试数据，并在 @AfterEach 物理清理。
 */
@SpringBootTest
class PlanRunIntegrationTest {

    private static HttpServer mockServer;
    private static int mockPort;

    @Autowired private PlanRunService planRunService;
    @Autowired private StaleRunRecovery staleRunRecovery;
    @Autowired private TestPlanMapper testPlanMapper;
    @Autowired private PlanCaseMapper planCaseMapper;
    @Autowired private TestCaseMapper testCaseMapper;
    @Autowired private ApiDefinitionMapper apiDefinitionMapper;
    @Autowired private TestRunMapper testRunMapper;
    @Autowired private TestResultMapper testResultMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startMockServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.createContext("/ok", exchange -> {
            byte[] body = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        mockServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        mockServer.start();
        mockPort = mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @AfterEach
    void cleanup() {
        String planIds = "SELECT id FROM test_plan WHERE name LIKE 'IT-%'";
        jdbcTemplate.update("DELETE tr FROM test_result tr JOIN test_run r ON tr.run_id = r.id WHERE r.plan_id IN (" + planIds + ")");
        jdbcTemplate.update("DELETE FROM test_run WHERE plan_id IN (" + planIds + ")");
        jdbcTemplate.update("DELETE FROM test_plan_case WHERE plan_id IN (" + planIds + ")");
        jdbcTemplate.update("DELETE FROM test_plan WHERE name LIKE 'IT-%'");
        jdbcTemplate.update("DELETE FROM test_case WHERE name LIKE 'IT-%'");
        jdbcTemplate.update("DELETE FROM api_definition WHERE name LIKE 'IT-%'");
    }

    // ---------- 测试数据构造 ----------

    private Long createCase(String path, List<AssertionRule> rules) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ApiDefinition api = new ApiDefinition();
        api.setName("IT-接口-" + suffix);
        api.setHttpMethod("GET");
        api.setPath(path);
        api.setBaseUrl("http://127.0.0.1:" + mockPort);
        apiDefinitionMapper.insert(api);

        TestCase testCase = new TestCase();
        testCase.setApiId(api.getId());
        testCase.setName("IT-用例-" + suffix);
        testCase.setRequestParams(new RequestParams());
        testCase.setAssertionRules(rules);
        testCase.setGenerationType("MANUAL");
        testCaseMapper.insert(testCase);
        return testCase.getId();
    }

    private Long createPlan(String name, List<Long> caseIds) {
        TestPlan plan = new TestPlan();
        plan.setName("IT-" + name);
        testPlanMapper.insert(plan);
        for (Long caseId : caseIds) {
            PlanCase planCase = new PlanCase();
            planCase.setPlanId(plan.getId());
            planCase.setCaseId(caseId);
            planCaseMapper.insert(planCase);
        }
        return plan.getId();
    }

    private TestRun awaitTerminal(Long runId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            TestRun run = testRunMapper.selectById(runId);
            if (run != null && !"PENDING".equals(run.getStatus()) && !"RUNNING".equals(run.getStatus())) {
                return run;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("run 未在超时内进入终态: " + runId);
    }

    /** 等待终态且评分已写入（评分在终态写入之后完成，轮询可能先读到未评分状态） */
    private TestRun awaitScored(Long runId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            TestRun run = testRunMapper.selectById(runId);
            if (run != null && run.getScore() != null) {
                return run;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("评分未在超时内写入: " + runId);
    }

    private long countDetails(Long runId) {
        Long count = testResultMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestResult>()
                        .eq(TestResult::getRunId, runId));
        return count == null ? 0 : count;
    }

    // ---------- 测试 ----------

    @Test
    void allPassedRunHasConsistentStatistics() throws Exception {
        AssertionRule statusOk = AssertionRule.builder().type("STATUS_CODE").expected(200).build();
        List<Long> caseIds = List.of(
                createCase("/ok", List.of(statusOk)),
                createCase("/ok", List.of(statusOk)),
                createCase("/ok", List.of(statusOk)));
        Long planId = createPlan("全部通过", caseIds);

        Long runId = planRunService.submit(planId, null);
        TestRun run = awaitScored(runId, 10_000);

        assertEquals("SUCCESS", run.getStatus());
        assertEquals(3, run.getTotalCount());
        assertEquals(3, run.getSuccessCount());
        assertEquals(0, run.getFailedCount());
        assertEquals(0, run.getErrorCount());
        assertEquals(3, countDetails(runId));
        assertNotNull(run.getStartedAt());
        assertNotNull(run.getFinishedAt());

        // 全部通过的运行评分应为 100（优秀）
        assertEquals(0, new java.math.BigDecimal("100.00").compareTo(run.getScore()));
        assertNotNull(run.getScoreDetail());
        assertEquals("优秀", run.getScoreDetail().getGrade());
    }

    @Test
    void failedAssertionMarksRunFailed() throws Exception {
        Long caseId = createCase("/ok", List.of(
                AssertionRule.builder().type("STATUS_CODE").expected(200).build()));
        Long failCaseId = createCase("/ok", List.of(
                AssertionRule.builder().type("JSON_FIELD").path("$.code")
                        .operator("EQUALS").expected(999).build()));
        Long planId = createPlan("含失败", List.of(caseId, failCaseId));

        Long runId = planRunService.submit(planId, null);
        TestRun run = awaitTerminal(runId, 10_000);

        assertEquals("FAILED", run.getStatus());
        assertEquals(1, run.getSuccessCount());
        assertEquals(1, run.getFailedCount());
        assertEquals(0, run.getErrorCount());
        assertEquals(2, countDetails(runId));
    }

    @Test
    void cancelPendingRunTurnsCancelled() {
        // 手工构造 PENDING run（不提交执行），验证 cancel 的 PENDING 分支
        Long planId = createPlan("待取消", List.of(
                createCase("/ok", List.of(
                        AssertionRule.builder().type("STATUS_CODE").expected(200).build()))));
        TestRun pending = new TestRun();
        pending.setPlanId(planId);
        pending.setStatus("PENDING");
        pending.setTotalCount(1);
        pending.setSuccessCount(0);
        pending.setFailedCount(0);
        pending.setErrorCount(0);
        testRunMapper.insert(pending);

        var vo = planRunService.cancel(pending.getId());

        assertEquals("CANCELLED", vo.getStatus());
        assertEquals("CANCELLED", testRunMapper.selectById(pending.getId()).getStatus());
    }

    @Test
    void cancelRunningRunEndsCancelledWithConsistentDetails() throws Exception {
        AssertionRule statusOk = AssertionRule.builder().type("STATUS_CODE").expected(200).build();
        List<Long> caseIds = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            caseIds.add(createCase("/slow", List.of(statusOk)));
        }
        Long planId = createPlan("运行中取消", caseIds);

        Long runId = planRunService.submit(planId, null);

        // 等待进入 RUNNING 后立即取消
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            TestRun current = testRunMapper.selectById(runId);
            if (current != null && "RUNNING".equals(current.getStatus())) {
                break;
            }
            Thread.sleep(20);
        }
        planRunService.cancel(runId);

        TestRun run = awaitTerminal(runId, 15_000);
        assertEquals("CANCELLED", run.getStatus());

        // 统计与明细一致（只统计已完成的用例）
        long details = countDetails(runId);
        assertEquals(details, run.getSuccessCount() + run.getFailedCount() + run.getErrorCount());
        assertTrue(details <= 12);
    }

    @Test
    void cancelTerminalRunThrows409() throws Exception {
        Long caseId = createCase("/ok", List.of(
                AssertionRule.builder().type("STATUS_CODE").expected(200).build()));
        Long planId = createPlan("终态取消", List.of(caseId));

        Long runId = planRunService.submit(planId, null);
        awaitTerminal(runId, 10_000);

        BusinessException e = assertThrows(BusinessException.class, () -> planRunService.cancel(runId));
        assertEquals(409, e.getCode());
    }

    @Test
    void staleRecoveryMarksUnfinishedRunsAsError() {
        Long planId = createPlan("重启恢复", List.of(
                createCase("/ok", List.of(
                        AssertionRule.builder().type("STATUS_CODE").expected(200).build()))));

        TestRun pending = new TestRun();
        pending.setPlanId(planId);
        pending.setStatus("PENDING");
        pending.setTotalCount(1);
        pending.setSuccessCount(0);
        pending.setFailedCount(0);
        pending.setErrorCount(0);
        testRunMapper.insert(pending);

        TestRun running = new TestRun();
        running.setPlanId(planId);
        running.setStatus("RUNNING");
        running.setTotalCount(1);
        running.setSuccessCount(0);
        running.setFailedCount(0);
        running.setErrorCount(0);
        running.setStartedAt(LocalDateTime.now());
        testRunMapper.insert(running);

        TestRun finished = new TestRun();
        finished.setPlanId(planId);
        finished.setStatus("SUCCESS");
        finished.setTotalCount(1);
        finished.setSuccessCount(1);
        finished.setFailedCount(0);
        finished.setErrorCount(0);
        finished.setStartedAt(LocalDateTime.now());
        finished.setFinishedAt(LocalDateTime.now());
        testRunMapper.insert(finished);

        staleRunRecovery.run(null);

        assertEquals("ERROR", testRunMapper.selectById(pending.getId()).getStatus());
        assertEquals("ERROR", testRunMapper.selectById(running.getId()).getStatus());
        assertEquals("SUCCESS", testRunMapper.selectById(finished.getId()).getStatus());
    }
}

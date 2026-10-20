package com.quality.guard.module.gate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.module.gate.dto.GateCheckDTO;
import com.quality.guard.module.gate.entity.QualityGateRecord;
import com.quality.guard.module.gate.mapper.QualityGateRecordMapper;
import com.quality.guard.module.gate.service.GateCheckService;
import com.quality.guard.module.gate.vo.GateResultVO;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门禁判定集成测试（同步逻辑，@Transactional 回滚即可）。
 */
@SpringBootTest
@Transactional
class GateCheckIntegrationTest {

    @Autowired
    private GateCheckService gateCheckService;

    @Autowired
    private TestRunMapper testRunMapper;

    @Autowired
    private TestPlanMapper testPlanMapper;

    @Autowired
    private QualityGateRecordMapper gateRecordMapper;

    private TestRun createRun(Long planId, String status, String score, int failed, int error) {
        TestRun run = new TestRun();
        run.setPlanId(planId);
        run.setStatus(status);
        run.setTotalCount(1 + failed + error);
        run.setSuccessCount(1);
        run.setFailedCount(failed);
        run.setErrorCount(error);
        if (score != null) {
            run.setScore(new BigDecimal(score));
        }
        run.setStartedAt(LocalDateTime.now());
        run.setFinishedAt(LocalDateTime.now());
        testRunMapper.insert(run);
        return run;
    }

    private GateCheckDTO dto(Long runId) {
        GateCheckDTO dto = new GateCheckDTO();
        dto.setRunId(runId);
        return dto;
    }

    private long recordCount(Long runId) {
        Long count = gateRecordMapper.selectCount(
                new LambdaQueryWrapper<QualityGateRecord>()
                        .eq(QualityGateRecord::getRunId, runId));
        return count == null ? 0 : count;
    }

    @Test
    void allRulesPassAndRecordPersisted() {
        TestRun run = createRun(null, "SUCCESS", "95.0", 0, 0);

        GateResultVO result = gateCheckService.check(dto(run.getId()));

        assertTrue(result.isPassed());
        assertTrue(result.getViolations().isEmpty());
        assertEquals(95.0, result.getScore().doubleValue());
        assertNotNull(result.getRecordId());
        assertEquals(1, recordCount(run.getId()));

        QualityGateRecord record = gateRecordMapper.selectById(result.getRecordId());
        assertTrue(Boolean.TRUE.equals(record.getPassed()));
        assertNotNull(record.getThresholdSnapshot());
        assertNotNull(record.getViolations());
    }

    @Test
    void violationsBlockGate() {
        TestRun run = createRun(null, "FAILED", "70.0", 1, 2);

        GateResultVO result = gateCheckService.check(dto(run.getId()));

        assertFalse(result.isPassed());
        // 总分不达标 + 失败超限 + 异常超限，三条违规
        assertEquals(3, result.getViolations().size());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRule().contains("总分")));
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRule().contains("失败")));
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRule().contains("异常")));
    }

    @Test
    void requestLevelThresholdOverrideWorks() {
        TestRun run = createRun(null, "SUCCESS", "70.0", 0, 0);

        GateCheckDTO dto = dto(run.getId());
        dto.setScoreThreshold(60);
        assertTrue(gateCheckService.check(dto).isPassed());
    }

    @Test
    void planLevelGateConfigApplied() {
        TestPlan plan = new TestPlan();
        plan.setName("门禁阈值覆盖计划");
        Map<String, Object> gateConfig = new HashMap<>();
        gateConfig.put("scoreThreshold", 60);
        gateConfig.put("maxFailedCount", 1);
        Map<String, Object> executionConfig = new HashMap<>();
        executionConfig.put("gate", gateConfig);
        plan.setExecutionConfig(executionConfig);
        testPlanMapper.insert(plan);

        TestRun run = createRun(plan.getId(), "SUCCESS", "75.0", 1, 0);

        assertTrue(gateCheckService.check(dto(run.getId())).isPassed());
    }

    @Test
    void nonTerminalRunRejectedWith409() {
        TestRun run = createRun(null, "RUNNING", null, 0, 0);

        BusinessException e = assertThrows(BusinessException.class,
                () -> gateCheckService.check(dto(run.getId())));
        assertEquals(409, e.getCode());
    }

    @Test
    void missingRunThrows404() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> gateCheckService.check(dto(999999L)));
        assertEquals(404, e.getCode());
    }

    @Test
    void missingScoreIsViolation() {
        TestRun run = createRun(null, "SUCCESS", null, 0, 0);

        GateResultVO result = gateCheckService.check(dto(run.getId()));

        assertFalse(result.isPassed());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRule().contains("缺少评分")));
    }
}

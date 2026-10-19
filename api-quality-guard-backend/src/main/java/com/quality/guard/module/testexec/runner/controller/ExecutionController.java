package com.quality.guard.module.testexec.runner.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.Result;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.dto.CaseRunDTO;
import com.quality.guard.module.testexec.runner.engine.TestExecutionEngine;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import com.quality.guard.module.testexec.runner.vo.TestResultVO;
import com.quality.guard.module.testexec.runner.vo.TestRunVO;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 执行接口：单用例同步执行、执行结果查询、运行任务查询。
 */
@RestController
@RequiredArgsConstructor
public class ExecutionController {

    private final TestExecutionEngine testExecutionEngine;
    private final TestResultMapper testResultMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestRunMapper testRunMapper;
    private final TestPlanMapper testPlanMapper;

    /** 执行单个用例（同步，返回完整结果） */
    @PostMapping("/api/cases/{id}/run")
    public Result<TestResultVO> runCase(@PathVariable Long id, @RequestBody(required = false) CaseRunDTO dto) {
        String baseUrlOverride = dto == null ? null : dto.getBaseUrl();
        TestResult result = testExecutionEngine.executeCase(id, baseUrlOverride);
        TestCase testCase = testCaseMapper.selectById(result.getCaseId());
        return Result.success(toVO(result, testCase == null ? null : testCase.getName()));
    }

    /** 查询单条执行结果 */
    @GetMapping("/api/results/{id}")
    public Result<TestResultVO> resultDetail(@PathVariable Long id) {
        TestResult result = testResultMapper.selectById(id);
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行结果不存在: " + id);
        }
        TestCase testCase = testCaseMapper.selectById(result.getCaseId());
        return Result.success(toVO(result, testCase == null ? null : testCase.getName()));
    }

    /** 分页查询运行任务 */
    @GetMapping("/api/runs")
    public Result<PageResult<TestRunVO>> pageRuns(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String status) {
        Page<TestRun> result = testRunMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<TestRun>()
                        .eq(planId != null, TestRun::getPlanId, planId)
                        .eq(StringUtils.hasText(status), TestRun::getStatus, status)
                        .orderByDesc(TestRun::getId));
        Map<Long, String> planNames = loadPlanNames(result.getRecords());
        // 单用例调试的 run planId 为 null——不能对不可变 Map 做 null key 查询（会 NPE）
        List<TestRunVO> records = result.getRecords().stream()
                .map(run -> toRunVO(run,
                        run.getPlanId() == null ? null : planNames.get(run.getPlanId())))
                .toList();
        return Result.success(PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records));
    }

    /** 运行任务详情（前端轮询进度） */
    @GetMapping("/api/runs/{id}")
    public Result<TestRunVO> runDetail(@PathVariable Long id) {
        TestRun run = testRunMapper.selectById(id);
        if (run == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行任务不存在: " + id);
        }
        String planName = null;
        if (run.getPlanId() != null) {
            TestPlan plan = testPlanMapper.selectById(run.getPlanId());
            planName = plan == null ? null : plan.getName();
        }
        return Result.success(toRunVO(run, planName));
    }

    /** 分页查询某次运行的结果列表 */
    @GetMapping("/api/runs/{id}/results")
    public Result<PageResult<TestResultVO>> runResults(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status) {
        Page<TestResult> result = testResultMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<TestResult>()
                        .eq(TestResult::getRunId, id)
                        .eq(StringUtils.hasText(status), TestResult::getStatus, status)
                        .orderByAsc(TestResult::getId));
        List<Long> caseIds = result.getRecords().stream().map(TestResult::getCaseId).distinct().toList();
        Map<Long, String> caseNames = caseIds.isEmpty() ? Map.of()
                : testCaseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(TestCase::getId, TestCase::getName));
        List<TestResultVO> records = result.getRecords().stream()
                .map(entity -> toVO(entity, caseNames.get(entity.getCaseId())))
                .toList();
        return Result.success(PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records));
    }

    private Map<Long, String> loadPlanNames(List<TestRun> runs) {
        List<Long> planIds = runs.stream()
                .map(TestRun::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return testPlanMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(TestPlan::getId, TestPlan::getName));
    }

    private TestResultVO toVO(TestResult entity, String caseName) {
        TestResultVO vo = new TestResultVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setCaseId(entity.getCaseId());
        vo.setCaseName(caseName);
        vo.setStatus(entity.getStatus());
        vo.setRequestSnapshot(entity.getRequestSnapshot());
        vo.setResponseSnapshot(entity.getResponseSnapshot());
        vo.setAssertionResults(entity.getAssertionResults());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setDurationMs(entity.getDurationMs());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private TestRunVO toRunVO(TestRun run, String planName) {
        TestRunVO vo = new TestRunVO();
        vo.setId(run.getId());
        vo.setPlanId(run.getPlanId());
        vo.setPlanName(planName);
        vo.setStatus(run.getStatus());
        vo.setTotalCount(run.getTotalCount());
        vo.setSuccessCount(run.getSuccessCount());
        vo.setFailedCount(run.getFailedCount());
        vo.setErrorCount(run.getErrorCount());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setCreatedAt(run.getCreatedAt());
        return vo;
    }
}

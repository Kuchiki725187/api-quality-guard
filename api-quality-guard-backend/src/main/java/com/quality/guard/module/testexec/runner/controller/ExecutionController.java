package com.quality.guard.module.testexec.runner.controller;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.Result;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.runner.dto.CaseRunDTO;
import com.quality.guard.module.testexec.runner.engine.TestExecutionEngine;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.vo.TestResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 执行接口：单用例同步执行 + 结果查询
 */
@RestController
@RequiredArgsConstructor
public class ExecutionController {

    private final TestExecutionEngine testExecutionEngine;
    private final TestResultMapper testResultMapper;
    private final TestCaseMapper testCaseMapper;

    /** 执行单个用例（同步，返回完整结果） */
    @PostMapping("/api/cases/{id}/run")
    public Result<TestResultVO> runCase(@PathVariable Long id, @RequestBody(required = false) CaseRunDTO dto) {
        String baseUrlOverride = dto == null ? null : dto.getBaseUrl();
        //执行引擎执行测试用例
        TestResult result = testExecutionEngine.executeCase(id, baseUrlOverride);
        return Result.success(toVO(result));
    }

    /** 查询单条执行结果 */
    @GetMapping("/api/results/{id}")
    public Result<TestResultVO> resultDetail(@PathVariable Long id) {
        TestResult result = testResultMapper.selectById(id);
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行结果不存在: " + id);
        }
        return Result.success(toVO(result));
    }

    private TestResultVO toVO(TestResult entity) {
        TestResultVO vo = new TestResultVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setCaseId(entity.getCaseId());
        vo.setStatus(entity.getStatus());
        vo.setRequestSnapshot(entity.getRequestSnapshot());
        vo.setResponseSnapshot(entity.getResponseSnapshot());
        vo.setAssertionResults(entity.getAssertionResults());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setDurationMs(entity.getDurationMs());
        vo.setCreatedAt(entity.getCreatedAt());
        TestCase testCase = testCaseMapper.selectById(entity.getCaseId());
        vo.setCaseName(testCase == null ? null : testCase.getName());
        return vo;
    }
}

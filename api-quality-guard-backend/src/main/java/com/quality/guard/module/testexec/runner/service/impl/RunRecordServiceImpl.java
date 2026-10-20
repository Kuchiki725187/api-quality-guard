package com.quality.guard.module.testexec.runner.service.impl;

import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.RequestSnapshot;
import com.quality.guard.module.testexec.model.ResponseSnapshot;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import com.quality.guard.module.scoring.calculator.ScoreCalculator;
import com.quality.guard.module.testexec.runner.service.RunRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RunRecordServiceImpl implements RunRecordService {

    private final TestRunMapper testRunMapper;
    private final TestResultMapper testResultMapper;
    private final ScoreCalculator scoreCalculator;

    @Override
    public Long startSingleCaseRun(Long caseId) {
        TestRun run = new TestRun();
        run.setPlanId(null);
        run.setStatus("RUNNING");
        run.setTotalCount(1);
        run.setSuccessCount(0);
        run.setFailedCount(0);
        run.setErrorCount(0);
        run.setStartedAt(LocalDateTime.now());
        testRunMapper.insert(run);
        return run.getId();
    }

    @Override
    public TestResult saveResult(Long runId, Long caseId, String status,
                                 RequestSnapshot requestSnapshot,
                                 ResponseSnapshot responseSnapshot,
                                 List<AssertionOutcome> assertionResults,
                                 String errorMessage,
                                 long durationMs) {
        TestResult entity = new TestResult();
        entity.setRunId(runId);
        entity.setCaseId(caseId);
        entity.setStatus(status);
        entity.setRequestSnapshot(requestSnapshot);
        entity.setResponseSnapshot(responseSnapshot);
        entity.setAssertionResults(assertionResults);
        entity.setErrorMessage(errorMessage);
        entity.setDurationMs(durationMs);
        testResultMapper.insert(entity);
        return entity;
    }

    @Override
    public void finishRun(Long runId, String status, int successCount, int failedCount, int errorCount) {
        TestRun run = testRunMapper.selectById(runId);
        if (run == null) {
            return;
        }
        run.setStatus(status);
        run.setSuccessCount(successCount);
        run.setFailedCount(failedCount);
        run.setErrorCount(errorCount);
        run.setFinishedAt(LocalDateTime.now());
        testRunMapper.updateById(run);

        // 仅正常完成的运行参与评分（取消/任务级异常的运行数据不完整，不评分）
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            scoreCalculator.calculateForRun(runId);
        }
    }
}

package com.quality.guard.module.testexec.runner.service;

import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.RequestSnapshot;
import com.quality.guard.module.testexec.model.ResponseSnapshot;
import com.quality.guard.module.testexec.runner.entity.TestResult;

import java.util.List;

/**
 * 执行记录的落库服务：封装 run / result 两张表的写入细节，
 * 供执行引擎在编排流程中调用。
 */
public interface RunRecordService {

    /** 为单用例执行创建 run 记录（RUNNING 状态、total=1），返回 runId */
    Long startSingleCaseRun(Long caseId);

    /** 保存单条执行结果，返回落库后的实体（含自增 id 与 createdAt 回填） */
    TestResult saveResult(Long runId, Long caseId, String status,
                          RequestSnapshot requestSnapshot,
                          ResponseSnapshot responseSnapshot,
                          List<AssertionOutcome> assertionResults,
                          String errorMessage,
                          long durationMs);

    /** 结束 run：写入终态与统计、finishedAt（单用例执行的统计为 1/0/0 或 0/1/0 或 0/0/1） */
    void finishRun(Long runId, String status, int successCount, int failedCount, int errorCount);
}

package com.quality.guard.module.testexec.runner.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.runner.engine.TestExecutionEngine;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import com.quality.guard.module.testexec.runner.service.RunRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 批量执行器
 * 把一个 run 下的多条用例分发到有界线程池并发执行，结束后汇总 run 终态与统计
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanRunExecutor {

    private final TestExecutionEngine testExecutionEngine;
    private final TestRunMapper testRunMapper;
    private final RunCancellationRegistry cancellationRegistry;
    private final RunRecordService runRecordService;
    private final ThreadPoolExecutor planRunExecutor;

    /** 提交 run 的执行任务（立即返回；任务在线程池中异步执行） */
    public void submit(Long runId, List<Long> caseIds, String baseUrlOverride) {
        try {
            planRunExecutor.execute(() -> executeRun(runId, caseIds, baseUrlOverride));
        } catch (RejectedExecutionException e) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "执行队列已满，请稍后重试");
        }
    }

    /** 执行一个 run 的全部用例（线程池任务体） */
    public void executeRun(Long runId, List<Long> caseIds, String baseUrlOverride) {
        try {
            // 1. 状态抢占：PENDING → RUNNING（条件更新，affected=0 说明已取消或重复执行）
            int affectedRows = testRunMapper.update(null, new LambdaUpdateWrapper<TestRun>()
                    .set(TestRun::getStatus, "RUNNING")
                    .set(TestRun::getStartedAt, LocalDateTime.now())
                    .eq(TestRun::getId, runId)
                    .eq(TestRun::getStatus, "PENDING"));
            if (affectedRows == 0) {
                log.info("run {} 抢占 PENDING 失败（已取消或已被执行），直接退出", runId);
                return;
            }

            // 2. 注册取消标志（幂等）
            cancellationRegistry.register(runId);

            // 3. 并发分发并等待完成
            executeCases(runId, caseIds, baseUrlOverride);

            // 4. 从明细聚合统计：counts == 明细条数，满足"统计与明细一致"
            Map<String, Integer> counts = loadStatusCounts(runId);
            int success = counts.getOrDefault("PASSED", 0);
            int failed = counts.getOrDefault("FAILED", 0);
            int error = counts.getOrDefault("ERROR", 0);
            boolean cancelled = cancellationRegistry.isCancelled(runId);

            // 5. 终态决策 + 条件写入（WHERE status='RUNNING'，防止覆盖并发写入的终态）
            String finalStatus;
            if (cancelled) {
                finalStatus = "CANCELLED";
            } else if (success == caseIds.size() && failed == 0 && error == 0) {
                finalStatus = "SUCCESS";
            } else {
                finalStatus = "FAILED";
            }

            int updated = testRunMapper.update(null, new LambdaUpdateWrapper<TestRun>()
                    .set(TestRun::getStatus, finalStatus)
                    .set(TestRun::getSuccessCount, success)
                    .set(TestRun::getFailedCount, failed)
                    .set(TestRun::getErrorCount, error)
                    .set(TestRun::getFinishedAt, LocalDateTime.now())
                    .eq(TestRun::getId, runId)
                    .eq(TestRun::getStatus, "RUNNING"));
            if (updated == 0) {
                log.warn("run {} 终态写入失败：状态已被并发修改", runId);
            }
            log.info("run {} 执行结束: status={}, success={}, failed={}, error={}",
                    runId, finalStatus, success, failed, error);
        } catch (Exception e) {
            // 调度级异常兜底：置 ERROR，不能静默丢失
            log.error("run {} 调度异常", runId, e);
            testRunMapper.update(null, new LambdaUpdateWrapper<TestRun>()
                    .set(TestRun::getStatus, "ERROR")
                    .set(TestRun::getFinishedAt, LocalDateTime.now())
                    .eq(TestRun::getId, runId)
                    .in(TestRun::getStatus, "PENDING", "RUNNING"));
        } finally {
            // 清理取消标志登记，防止内存泄漏
            cancellationRegistry.remove(runId);
        }
    }

    private void executeCases(Long runId, List<Long> caseIds, String baseUrlOverride) {
        List<Future<?>> futures = new ArrayList<>();
        for (Long caseId : caseIds) {
            // 协作式取消：已请求取消则不再提交剩余用例
            if (cancellationRegistry.isCancelled(runId)) {
                log.info("run {} 已请求取消，停止提交剩余用例", runId);
                break;
            }
            Future<?> future = planRunExecutor.submit(() -> {
                try {
                    testExecutionEngine.executeCaseInRun(caseId, baseUrlOverride, runId);
                } catch (RuntimeException e) {
                    // 配置类异常（如用例/接口已被删除）不会产生结果——必须补记 ERROR 明细，
                    // 否则按明细聚合的统计会少一条，任务统计与明细对不上
                    log.error("用例执行异常，runId={}, caseId={}", runId, caseId, e);
                    runRecordService.saveResult(runId, caseId, "ERROR",
                            null, null, List.of(),
                            "执行异常: " + e.getMessage(), 0);
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                log.error("测试任务执行失败", e.getCause());
            }
        }
    }

    /** 按状态聚合明细数量 */
    private Map<String, Integer> loadStatusCounts(Long runId) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : testRunMapper.selectStatisticsByRunId(runId)) {
            Object status = row.get("status");
            Object count = row.get("cnt");
            if (status != null && count instanceof Number number) {
                counts.put(String.valueOf(status), number.intValue());
            }
        }
        return counts;
    }
}

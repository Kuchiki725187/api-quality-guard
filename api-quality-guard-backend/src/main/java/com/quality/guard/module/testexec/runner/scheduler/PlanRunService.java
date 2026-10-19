package com.quality.guard.module.testexec.runner.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.plan.dto.PlanRunSubmitDTO;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.PlanCaseMapper;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import com.quality.guard.module.testexec.runner.vo.TestRunVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计划执行编排
 * 提交接口负责"创建 run + 交出执行权"，取消接口负责"状态校验 + 协作式取消"
 */
@Service
@RequiredArgsConstructor
public class PlanRunService {

    private final TestPlanMapper testPlanMapper;
    private final PlanCaseMapper planCaseMapper;
    private final TestRunMapper testRunMapper;
    private final PlanRunExecutor planRunExecutor;
    private final RunCancellationRegistry cancellationRegistry;

    /**
     * 提交计划执行：创建 run（PENDING）→ 异步提交 → 立即返回 runId
     */
    public Long submit(Long planId, PlanRunSubmitDTO dto) {
        requirePlan(planId);
        List<Long> caseIds = caseIdsOf(planId);
        if (caseIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "计划未关联任何用例，无法执行");
        }

        TestRun pendingRun = createPendingRun(planId, caseIds.size());
        Long runId = pendingRun.getId();
        try {
            planRunExecutor.submit(runId, caseIds, baseUrlOverride(dto));
        } catch (RuntimeException e) {
            // 提交失败（如线程池队列满返回 503）：把 run 置为 ERROR，避免永远停在 PENDING
            testRunMapper.update(null, new LambdaUpdateWrapper<TestRun>()
                    .set(TestRun::getStatus, "ERROR")
                    .set(TestRun::getFinishedAt, LocalDateTime.now())
                    .eq(TestRun::getId, runId));
            throw e;
        }
        return runId;
    }

    /**
     * 取消运行：
     * - 终态 → 409（非法状态转换）
     * - PENDING → 条件更新为 CANCELLED；若条件更新失败（执行器已抢占为 RUNNING）→ 退化为标志取消
     * - RUNNING → 只置协作式取消标志，终态 CANCELLED 由执行器收尾写入
     */
    public TestRunVO cancel(Long runId) {
        TestRun run = testRunMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行任务不存在: " + runId);
        }
        String status = run.getStatus();
        if (!"PENDING".equals(status) && !"RUNNING".equals(status)) {
            throw new BusinessException(ResultCode.CONFLICT, "任务已处于终态，无法取消: " + status);
        }

        if ("PENDING".equals(status)) {
            // 条件更新（CAS）：只有当前仍是 PENDING 才改成 CANCELLED，
            // 防止"查询时是 PENDING、写入时执行器已改成 RUNNING"的竞态覆盖
            int affected = testRunMapper.update(null, new LambdaUpdateWrapper<TestRun>()
                    .set(TestRun::getStatus, "CANCELLED")
                    .set(TestRun::getFinishedAt, LocalDateTime.now())
                    .eq(TestRun::getId, runId)
                    .eq(TestRun::getStatus, "PENDING"));
            if (affected > 0) {
                return statusVO(runId);
            }
            // 抢占失败：执行器已把状态推进到 RUNNING，下面退化为协作式取消
        }

        cancellationRegistry.markCancelled(runId);
        return statusVO(runId);
    }

    private TestRunVO statusVO(Long runId) {
        TestRun run = testRunMapper.selectById(runId);
        TestRunVO vo = new TestRunVO();
        if (run == null) {
            vo.setId(runId);
            return vo;
        }
        vo.setId(run.getId());
        vo.setPlanId(run.getPlanId());
        vo.setStatus(run.getStatus());
        vo.setTotalCount(run.getTotalCount());
        vo.setSuccessCount(run.getSuccessCount());
        vo.setFailedCount(run.getFailedCount());
        vo.setErrorCount(run.getErrorCount());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        return vo;
    }

    private TestPlan requirePlan(Long planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测试计划不存在: " + planId);
        }
        return plan;
    }

    private TestRun createPendingRun(Long planId, int total) {
        TestRun run = new TestRun();
        run.setPlanId(planId);
        run.setStatus("PENDING");
        run.setTotalCount(total);
        run.setSuccessCount(0);
        run.setFailedCount(0);
        run.setErrorCount(0);
        testRunMapper.insert(run);
        return run;
    }

    private String baseUrlOverride(PlanRunSubmitDTO dto) {
        return dto == null ? null : dto.getBaseUrl();
    }

    private List<Long> caseIdsOf(Long planId) {
        return planCaseMapper.selectCaseIds(planId);
    }
}

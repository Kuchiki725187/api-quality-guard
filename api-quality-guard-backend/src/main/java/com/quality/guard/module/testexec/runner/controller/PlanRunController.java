package com.quality.guard.module.testexec.runner.controller;

import com.quality.guard.common.result.Result;
import com.quality.guard.module.testexec.plan.dto.PlanRunSubmitDTO;
import com.quality.guard.module.testexec.runner.scheduler.PlanRunService;
import com.quality.guard.module.testexec.runner.vo.TestRunVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 计划执行接口：异步提交批量任务、取消运行。
 */
@RestController
@RequiredArgsConstructor
public class PlanRunController {

    private final PlanRunService planRunService;

    /** 触发计划执行（异步，立即返回 runId） */
    @PostMapping("/api/plans/{id}/runs")
    public Result<Map<String, Object>> submit(@PathVariable Long id,
                                              @RequestBody(required = false) PlanRunSubmitDTO dto) {
        Long runId = planRunService.submit(id, dto);
        return Result.success(Map.of("runId", runId, "status", "PENDING"));
    }

    /** 取消运行（仅 PENDING/RUNNING 可取消） */
    @PostMapping("/api/runs/{id}/cancel")
    public Result<TestRunVO> cancel(@PathVariable Long id) {
        return Result.success(planRunService.cancel(id));
    }
}

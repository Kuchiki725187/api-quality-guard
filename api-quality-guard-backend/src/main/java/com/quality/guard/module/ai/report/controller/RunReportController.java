package com.quality.guard.module.ai.report.controller;

import com.quality.guard.common.result.Result;
import com.quality.guard.module.ai.report.service.RunReportService;
import com.quality.guard.module.ai.report.vo.RunReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行报告接口
 */
@RestController
@RequiredArgsConstructor
public class RunReportController {

    private final RunReportService runReportService;

    @GetMapping("/api/reports/runs/{id}")
    public Result<RunReportVO> runReport(@PathVariable Long id) {
        return Result.success(runReportService.runReport(id));
    }
}

package com.quality.guard.module.ai.report.controller;

import com.quality.guard.common.result.Result;
import com.quality.guard.module.ai.report.service.PlatformReportService;
import com.quality.guard.module.ai.report.vo.PlatformOverviewVO;
import com.quality.guard.module.ai.report.vo.PlatformQualityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台级报告接口：工作台概览与四维质量评分
 */
@RestController
@RequiredArgsConstructor
public class PlatformReportController {

    private final PlatformReportService platformReportService;

    @GetMapping("/api/reports/overview")
    public Result<PlatformOverviewVO> overview() {
        return Result.success(platformReportService.overview());
    }

    @GetMapping("/api/reports/quality")
    public Result<PlatformQualityVO> quality() {
        return Result.success(platformReportService.quality());
    }
}

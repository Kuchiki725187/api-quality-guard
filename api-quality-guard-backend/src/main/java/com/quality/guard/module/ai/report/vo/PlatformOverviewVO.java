package com.quality.guard.module.ai.report.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台概览：工作台统计、运行趋势、最近任务、风险接口
 */
@Data
public class PlatformOverviewVO {

    private Integer apiCount;

    private Integer caseCount;

    private Integer planCount;

    private Integer runCount;

    private Integer todayRunCount;

    /** 至少有一条用例的接口数 */
    private Integer coveredApiCount;

    /** 测试覆盖率（百分比，0-100，1 位小数） */
    private Double coverage;

    /** 近 7 天新增接口数 */
    private Integer apiWeekAdded;

    /** 近 14 天已评分运行的平均分 */
    private Double avgScore;

    /** 本周与前一周平均运行分的差值，数据不足时为 null */
    private Double weekDelta;

    private RunSummary latestRun;

    /** 近 14 天每日运行平均分，无数据的日期 score 为 null */
    private List<ScorePoint> scoreTrend;

    private List<RunSummary> recentRuns;

    private List<RiskApi> riskApis;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunSummary {

        private Long runId;
        private String planName;
        private String status;
        private BigDecimal score;
        private LocalDateTime createdAt;
        private LocalDateTime finishedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorePoint {

        private String date;
        private Double score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskApi {

        private Long apiId;
        private String name;
        private String method;
        private String path;
        /** 高 / 中 */
        private String risk;
        private String issue;
    }
}

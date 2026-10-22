package com.quality.guard.module.ai.report.vo;

import com.quality.guard.module.scoring.calculator.ScoreDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单次运行报告：执行统计、响应时间分布、用例来源表现
 * 响应时间仅统计拿到 HTTP 响应的结果（ERROR 不计入）
 */
@Data
public class RunReportVO {

    private Long runId;

    private String planName;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer errorCount;

    /** 通过率 = successCount / totalCount（ERROR 计入分母） */
    private Double passRate;

    /** 运行总耗时，未结束时为 null */
    private Long durationMs;

    /** 综合评分，未评分时为 null */
    private BigDecimal score;

    private ScoreDetail scoreDetail;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 没有有效响应结果时为 null */
    private ResponseTime responseTime;

    /** 分桶区间固定，便于跨运行对比 */
    private List<DurationBucket> durationDistribution;

    private List<GenerationTypeStat> generationTypeBreakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseTime {

        private Long minMs;
        private Long avgMs;
        private Long maxMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DurationBucket {

        private String range;
        private Integer count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationTypeStat {

        private String generationType;
        private Integer total;
        private Integer passed;
    }
}

package com.quality.guard.module.ai.report.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 平台质量评分：四维等权（规范性 / 覆盖率 / 性能 / 稳定性），
 * 无数据的维度不参与加权，权重按比例摊回其余维度
 */
@Data
public class PlatformQualityVO {

    private Double total;

    private String grade;

    /** 运行评分周环比（近 7 天 vs 前 7 天），数据不足时为 null */
    private Double weekDelta;

    private List<Dimension> dimensions;

    /** 近 14 天每日运行平均分，与工作台趋势一致 */
    private List<ScorePoint> scoreTrend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimension {

        private String key;
        private String name;
        /** 无数据时为 null */
        private Double score;
        private Double weight;
        private String detail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorePoint {

        private String date;
        private Double score;
    }
}

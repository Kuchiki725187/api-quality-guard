package com.quality.guard.module.scoring.calculator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评分明细（test_run.score_detail JSON）：记录各维度得分、实际权重与计算基数，
 * 保证评分"可解释、可复现"——任何人拿这份明细都能复算总分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreDetail {

    /** 综合得分（0-100，1 位小数） */
    private Double totalScore;

    /** 等级：优秀 / 良好 / 及格 / 不合格 */
    private String grade;

    /** 功能通过率维度得分 */
    private Double functionalScore;

    /** 性能达标率维度得分；null 表示本次运行没有响应时间断言，该维度不计入 */
    private Double performanceScore;

    /** 稳定性维度得分 */
    private Double stabilityScore;

    /** 实际使用的权重（性能维度缺失时按比例摊回其他两维） */
    private Double functionalWeight;
    private Double performanceWeight;
    private Double stabilityWeight;

    /** 计算基数：用例总数 / 通过数 / 异常数 / 含响应时间断言的用例数 / 其中达标数 */
    private Integer totalCount;
    private Integer passedCount;
    private Integer errorCount;
    private Integer perfTotal;
    private Integer perfPassed;
}

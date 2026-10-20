package com.quality.guard.module.testexec.runner.vo;

import com.quality.guard.module.scoring.calculator.ScoreDetail;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试运行（执行任务）视图对象。
 */
@Data
public class TestRunVO {

    private Long id;

    private Long planId;

    private String planName;

    /** PENDING / RUNNING / SUCCESS / FAILED / ERROR / CANCELLED */
    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer errorCount;

    /** 综合质量评分（0-100），未评分时为 null */
    private BigDecimal score;

    /** 评分维度明细 */
    private ScoreDetail scoreDetail;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}

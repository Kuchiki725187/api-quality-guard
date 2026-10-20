package com.quality.guard.module.testexec.runner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.quality.guard.module.scoring.calculator.ScoreDetail;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试运行，对应 test_run 表
 * 一次执行任务 = 一个 run：批量执行（plan_id 非空）或单用例调试（plan_id 为空、total=1）
 */
@Data
@TableName(value = "test_run", autoResultMap = true)
public class TestRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联测试计划；为空表示非计划触发的执行 */
    private Long planId;

    /** PENDING / RUNNING / SUCCESS / FAILED / ERROR / CANCELLED */
    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer errorCount;

    /** 综合质量评分（0-100），终态 SUCCESS/FAILED 时计算 */
    private BigDecimal score;

    /** 评分维度明细（JSON） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ScoreDetail scoreDetail;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

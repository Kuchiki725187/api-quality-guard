package com.quality.guard.module.testexec.runner.vo;

import lombok.Data;

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

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}

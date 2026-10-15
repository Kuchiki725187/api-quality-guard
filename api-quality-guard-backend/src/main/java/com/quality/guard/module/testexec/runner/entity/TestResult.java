package com.quality.guard.module.testexec.runner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.RequestSnapshot;
import com.quality.guard.module.testexec.model.ResponseSnapshot;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单用例执行结果，对应 test_result 表。
 * 快照与断言明细为 JSON 列，经 JacksonTypeHandler 映射。
 */
@Data
@TableName(value = "test_result", autoResultMap = true)
public class TestResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long caseId;

    /** PASSED / FAILED / ERROR */
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private RequestSnapshot requestSnapshot;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private ResponseSnapshot responseSnapshot;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AssertionOutcome> assertionResults;

    /** 网络层失败分类信息 */
    private String errorMessage;

    private Long durationMs;

    private LocalDateTime createdAt;
}

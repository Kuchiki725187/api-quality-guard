package com.quality.guard.module.testexec.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试计划-用例关联，对应 test_plan_case 表（多对多中间表）。
 */
@Data
@TableName("test_plan_case")
public class PlanCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long caseId;

    private LocalDateTime createdAt;
}

package com.quality.guard.module.testexec.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 测试计划，对应 test_plan 表。关联用例通过 test_plan_case 维护。
 */
@Data
@TableName(value = "test_plan", autoResultMap = true)
public class TestPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 执行配置：并发度、超时、baseUrl 覆盖等（v1 预留，调度实现按需读取） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> executionConfig;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

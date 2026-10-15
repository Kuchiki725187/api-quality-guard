package com.quality.guard.module.testexec.casegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试用例，对应 test_case 表。
 * request_params / assertion_rules 为 JSON 列，经 JacksonTypeHandler 映射为对象。
 */
@Data
@TableName(value = "test_case", autoResultMap = true)
public class TestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属接口定义 ID */
    private Long apiId;

    private String name;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private RequestParams requestParams;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AssertionRule> assertionRules;

    /** 生成来源：MANUAL / RULE / AI（见 GenerationType） */
    private String generationType;

    /** 生成来源说明（策略、取值依据等） */
    private String generationNote;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

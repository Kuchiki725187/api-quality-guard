package com.quality.guard.module.testexec.plan.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 更新测试计划：字段均可选；caseIds 传入时整体替换用例关联。
 */
@Data
public class PlanUpdateDTO {

    @Size(max = 255, message = "计划名称不能超过 255 字符")
    private String name;

    @Size(max = 1000, message = "描述不能超过 1000 字符")
    private String description;

    private Map<String, Object> executionConfig;

    private List<Long> caseIds;
}

package com.quality.guard.module.testexec.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建测试计划。
 */
@Data
public class PlanCreateDTO {

    @NotBlank(message = "计划名称不能为空")
    @Size(max = 255, message = "计划名称不能超过 255 字符")
    private String name;

    @Size(max = 1000, message = "描述不能超过 1000 字符")
    private String description;

    /** 执行配置（并发度/baseUrl 覆盖等），可为空 */
    private Map<String, Object> executionConfig;

    @NotEmpty(message = "至少关联一个用例")
    private List<Long> caseIds;
}

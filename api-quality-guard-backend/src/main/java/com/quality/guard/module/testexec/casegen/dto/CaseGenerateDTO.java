package com.quality.guard.module.testexec.casegen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 规则生成请求：对指定接口按策略批量生成用例。
 */
@Data
public class CaseGenerateDTO {

    @NotNull(message = "apiId 不能为空")
    private Long apiId;

    /** 启用的策略：NORMAL / REQUIRED_MISSING / TYPE_MISMATCH / BOUNDARY；为空表示全部 */
    private List<String> strategies;
}

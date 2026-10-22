package com.quality.guard.module.ai.enhance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 用例增强请求
 */
@Data
public class EnhanceRequestDTO {

    @NotNull(message = "apiId 不能为空")
    private Long apiId;

    /** 业务背景，例如"同一订单仅可取消一次" */
    @Size(max = 2000, message = "业务背景不能超过 2000 字符")
    private String extraContext;

    /** 期望生成条数 1~10 */
    @Min(value = 1, message = "生成条数至少为 1")
    @Max(value = 10, message = "生成条数最多为 10")
    private Integer count = 3;
}

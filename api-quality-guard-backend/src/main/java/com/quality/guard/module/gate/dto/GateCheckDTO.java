package com.quality.guard.module.gate.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 门禁检查请求：runId 必填；三个阈值可选，省略时取计划配置或默认值。
 * 优先级：请求覆盖 > 计划 executionConfig.gate > 默认（80 / 0 / 0）。
 */
@Data
public class GateCheckDTO {

    @NotNull(message = "runId 不能为空")
    private Long runId;

    /** 质量总分下限，默认 80 */
    private Integer scoreThreshold;

    /** 允许的失败用例数上限，默认 0 */
    private Integer maxFailedCount;

    /** 允许的执行异常数上限，默认 0 */
    private Integer maxErrorCount;
}

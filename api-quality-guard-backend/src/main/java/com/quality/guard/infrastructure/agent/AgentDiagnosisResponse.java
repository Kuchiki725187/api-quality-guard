package com.quality.guard.infrastructure.agent;

import com.quality.guard.module.ai.diagnosis.model.DiagnosisPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 诊断 Agent 的响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDiagnosisResponse {

    private DiagnosisPayload diagnosis;

    /** 模型调用轮数（含工具循环） */
    private int rounds;

    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    /** Agent 总耗时（毫秒） */
    private long durationMs;

    /** 多轮模型原始输出（用于排查） */
    private String rawOutput;
}

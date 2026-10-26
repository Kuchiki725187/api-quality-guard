package com.quality.guard.infrastructure.agent;

import com.quality.guard.module.ai.enhance.model.EnhancedCasePayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 用例增强响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEnhanceResponse {

    private List<EnhancedCasePayload> cases;

    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    private long durationMs;

    private String rawOutput;
}

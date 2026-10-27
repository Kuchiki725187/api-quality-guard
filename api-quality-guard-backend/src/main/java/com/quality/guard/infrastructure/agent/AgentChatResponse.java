package com.quality.guard.infrastructure.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {

    private String answer;

    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    private Long durationMs;

    private Integer rounds;
}

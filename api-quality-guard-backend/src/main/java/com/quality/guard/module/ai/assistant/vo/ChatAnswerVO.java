package com.quality.guard.module.ai.assistant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnswerVO {

    private String answer;

    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    private Long durationMs;

    private Integer rounds;
}

package com.quality.guard.module.ai.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Redis 中保存的一轮问答
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatExchange {

    private String user;

    private String assistant;

    private LocalDateTime createdAt;
}

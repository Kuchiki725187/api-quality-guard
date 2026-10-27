package com.quality.guard.module.ai.assistant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO {

    /** user / assistant */
    private String role;

    private String content;

    private LocalDateTime createdAt;
}

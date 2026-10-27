package com.quality.guard.infrastructure.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatMessage {

    /** user / assistant */
    private String role;

    private String content;
}

package com.quality.guard.infrastructure.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 发送给 Agent 的自然语言问答请求；history 只包含此前的 user/assistant 最终消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequest {

    private String question;

    private List<AgentChatMessage> history;

    /** 请求用户；Agent 工具回调据此按用户隔离数据 */
    private Long userId;
}

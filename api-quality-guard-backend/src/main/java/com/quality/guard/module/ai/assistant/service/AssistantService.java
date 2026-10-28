package com.quality.guard.module.ai.assistant.service;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.infrastructure.agent.AgentChatMessage;
import com.quality.guard.infrastructure.agent.AgentChatRequest;
import com.quality.guard.infrastructure.agent.AgentChatResponse;
import com.quality.guard.infrastructure.agent.ChatAgentClient;
import com.quality.guard.module.ai.assistant.model.ChatExchange;
import com.quality.guard.module.ai.assistant.session.ChatHistoryStore;
import com.quality.guard.module.ai.assistant.vo.ChatAnswerVO;
import com.quality.guard.module.ai.assistant.vo.ChatMessageVO;
import com.quality.guard.module.auth.model.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手问答：服务端维护历史，Agent 本身保持无状态
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final int HISTORY_EXCHANGES_FOR_AGENT = 3;
    private static final int MESSAGE_MAX_CHARS = 1000;

    private final ChatHistoryStore historyStore;
    private final ChatAgentClient chatAgentClient;

    public ChatAnswerVO chat(CurrentUser user, String message) {
        String question = message.trim();
        List<ChatExchange> exchanges = historyStore.load(user.getUserId());
        AgentChatRequest request = new AgentChatRequest(
                question, buildAgentHistory(exchanges), user.getUserId());
        AgentChatResponse response = chatAgentClient.chat(request);
        if (!StringUtils.hasText(response.getAnswer())) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 助手未返回内容");
        }
        historyStore.append(user.getUserId(), question, response.getAnswer());
        return new ChatAnswerVO(
                response.getAnswer(),
                response.getModel(),
                response.getInputTokens(),
                response.getOutputTokens(),
                response.getDurationMs(),
                response.getRounds());
    }

    public List<ChatMessageVO> history(CurrentUser user) {
        List<ChatMessageVO> messages = new ArrayList<>();
        for (ChatExchange exchange : historyStore.load(user.getUserId())) {
            messages.add(new ChatMessageVO("user", exchange.getUser(), exchange.getCreatedAt()));
            messages.add(new ChatMessageVO("assistant", exchange.getAssistant(), exchange.getCreatedAt()));
        }
        return messages;
    }

    public void clear(CurrentUser user) {
        historyStore.clear(user.getUserId());
    }

    private List<AgentChatMessage> buildAgentHistory(List<ChatExchange> exchanges) {
        int from = Math.max(0, exchanges.size() - HISTORY_EXCHANGES_FOR_AGENT);
        List<AgentChatMessage> history = new ArrayList<>();
        for (int i = from; i < exchanges.size(); i++) {
            ChatExchange exchange = exchanges.get(i);
            history.add(new AgentChatMessage("user", truncate(exchange.getUser())));
            history.add(new AgentChatMessage("assistant", truncate(exchange.getAssistant())));
        }
        return history;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MESSAGE_MAX_CHARS
                ? text
                : text.substring(0, MESSAGE_MAX_CHARS) + "…";
    }
}

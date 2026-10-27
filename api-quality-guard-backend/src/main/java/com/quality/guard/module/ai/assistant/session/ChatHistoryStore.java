package com.quality.guard.module.ai.assistant.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.ai.assistant.model.ChatExchange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手对话历史
 * Redis LIST，每个元素是一轮问答；Lua 原子执行追加 + 截断 + 过期，避免多标签页覆盖
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistoryStore {

    private static final String KEY_PREFIX = "aqg:assistant:history:";
    private static final int MAX_EXCHANGES = 10;

    private static final DefaultRedisScript<Long> APPEND_SCRIPT = new DefaultRedisScript<>("""
            redis.call('RPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], -10, -1)
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aqg.assistant.history-ttl-minutes:120}")
    private long ttlMinutes;

    public List<ChatExchange> load(Long userId) {
        try {
            List<String> raw = redisTemplate.opsForList().range(key(userId), 0, -1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<ChatExchange> exchanges = new ArrayList<>();
            for (String json : raw) {
                try {
                    exchanges.add(objectMapper.readValue(json, ChatExchange.class));
                } catch (JsonProcessingException e) {
                    log.warn("问答历史条目无法解析，已跳过: {}", e.getMessage());
                }
            }
            return exchanges;
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    /** 追加一轮；保存失败只记日志，不影响已生成的回答 */
    public void append(Long userId, String question, String answer) {
        ChatExchange exchange = new ChatExchange(question, answer, LocalDateTime.now());
        String json;
        try {
            json = objectMapper.writeValueAsString(exchange);
        } catch (JsonProcessingException e) {
            log.warn("问答历史序列化失败: {}", e.getMessage());
            return;
        }
        long ttlSeconds = ttlSeconds();
        try {
            redisTemplate.execute(APPEND_SCRIPT, List.of(key(userId)),
                    json, String.valueOf(ttlSeconds));
        } catch (DataAccessException e) {
            log.warn("问答历史保存失败: {}", e.getMessage());
        }
    }

    public void clear(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private long ttlSeconds() {
        long minutes = ttlMinutes > 0 ? ttlMinutes : 120;
        return minutes * 60;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private BusinessException unavailable(DataAccessException e) {
        log.warn("Redis 问答历史服务异常: {}", e.getMessage());
        return new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "问答历史服务不可用");
    }
}

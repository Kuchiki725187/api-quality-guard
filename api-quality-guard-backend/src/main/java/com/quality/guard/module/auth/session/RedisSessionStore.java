package com.quality.guard.module.auth.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.auth.config.AuthProperties;
import com.quality.guard.module.auth.model.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Redis 会话存储
 * - key 中只保存 token 的 SHA-256，Redis 泄露也无法直接冒用客户端 token
 * - 每次访问滑动续期；同时维护 userId -> token 集合，用于禁用/重置密码时批量踢下线
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSessionStore {

    private static final String SESSION_KEY_PREFIX = "aqg:auth:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "aqg:auth:user-sessions:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    public String create(CurrentUser user) {
        String token = randomToken();
        String tokenHash = hashToken(token);
        Duration ttl = ttl();
        try {
            redisTemplate.opsForValue().set(sessionKey(tokenHash),
                    objectMapper.writeValueAsString(user), ttl);
            String userKey = userSessionsKey(user.getUserId());
            redisTemplate.opsForSet().add(userKey, tokenHash);
            redisTemplate.expire(userKey, ttl);
        } catch (JsonProcessingException e) {
            log.warn("会话序列化失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "创建会话失败");
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
        return token;
    }

    /** 校验 token 并滑动续期；不存在/已过期返回 null */
    public CurrentUser findAndRenew(String token) {
        String tokenHash = hashToken(token);
        String sessionKey = sessionKey(tokenHash);
        String json;
        try {
            json = redisTemplate.opsForValue().get(sessionKey);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
        if (json == null) {
            return null;
        }
        CurrentUser user;
        try {
            user = objectMapper.readValue(json, CurrentUser.class);
        } catch (JsonProcessingException e) {
            log.warn("会话数据无法解析，按失效处理: {}", e.getMessage());
            try {
                redisTemplate.delete(sessionKey);
            } catch (DataAccessException ignored) {
                // 已按未登录处理
            }
            return null;
        }
        Duration ttl = ttl();
        try {
            redisTemplate.expire(sessionKey, ttl);
            String userKey = userSessionsKey(user.getUserId());
            redisTemplate.opsForSet().add(userKey, tokenHash);
            redisTemplate.expire(userKey, ttl);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
        return user;
    }

    public void delete(String token) {
        String tokenHash = hashToken(token);
        String sessionKey = sessionKey(tokenHash);
        try {
            String json = redisTemplate.opsForValue().get(sessionKey);
            redisTemplate.delete(sessionKey);
            if (json != null) {
                try {
                    CurrentUser user = objectMapper.readValue(json, CurrentUser.class);
                    redisTemplate.opsForSet().remove(userSessionsKey(user.getUserId()), tokenHash);
                } catch (JsonProcessingException ignored) {
                    // token 本身已删除，索引残留会随 TTL 清理
                }
            }
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    /** 使某用户的全部会话失效（禁用账号、重置密码时调用） */
    public void deleteAllForUser(Long userId) {
        String userKey = userSessionsKey(userId);
        try {
            Set<String> tokenHashes = redisTemplate.opsForSet().members(userKey);
            if (tokenHashes != null && !tokenHashes.isEmpty()) {
                List<String> sessionKeys = tokenHashes.stream()
                        .map(hash -> SESSION_KEY_PREFIX + hash)
                        .toList();
                redisTemplate.delete(sessionKeys);
            }
            redisTemplate.delete(userKey);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    public long ttlSeconds() {
        return ttl().toSeconds();
    }

    private Duration ttl() {
        long minutes = authProperties.getSessionTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 30);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String sessionKey(String tokenHash) {
        return SESSION_KEY_PREFIX + tokenHash;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_KEY_PREFIX + userId;
    }

    private BusinessException unavailable(DataAccessException e) {
        log.warn("Redis 会话服务异常: {}", e.getMessage());
        return new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "会话服务不可用");
    }
}

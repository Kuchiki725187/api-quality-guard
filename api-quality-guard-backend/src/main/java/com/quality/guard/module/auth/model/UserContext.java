package com.quality.guard.module.auth.model;

import java.util.function.Supplier;

/**
 * 当前请求的数据属主上下文
 * Web 请求由 AuthInterceptor / InternalTokenInterceptor 写入；异步任务用 runAs 包裹
 * 没有上下文时使用系统属主 0（测试、启动恢复等系统场景）
 */
public final class UserContext {

    public static final long SYSTEM_OWNER_ID = 0L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        if (userId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(userId);
        }
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static long currentOrSystem() {
        Long userId = CURRENT.get();
        return userId == null ? SYSTEM_OWNER_ID : userId;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runAs(long ownerId, Runnable action) {
        Long previous = CURRENT.get();
        CURRENT.set(ownerId);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAs(long ownerId, Supplier<T> action) {
        Long previous = CURRENT.get();
        CURRENT.set(ownerId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(Long previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}

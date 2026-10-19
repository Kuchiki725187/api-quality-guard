package com.quality.guard.module.testexec.runner.scheduler;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行任务的取消标志登记表。
 * 取消是"协作式"的：标记后由调度执行器在提交下一个用例前检查并停止；
 * 已在执行的用例允许自然完成。任务结束后必须 remove 防止内存泄漏
 */
@Component
public class RunCancellationRegistry {

    private final Map<Long, AtomicBoolean> cancelledMap = new ConcurrentHashMap<>();

    /** 注册（幂等），返回该 run 的取消标志 */
    public AtomicBoolean register(Long runId) {
        return cancelledMap.computeIfAbsent(runId, key -> new AtomicBoolean(false));
    }

    public boolean isCancelled(Long runId) {
        AtomicBoolean flag = cancelledMap.get(runId);
        return flag != null && flag.get();
    }

    public void markCancelled(Long runId) {
        // computeIfAbsent 在 key 已存在时不会执行 lambda
        // 不能写成 computeIfAbsent(..., new AtomicBoolean(true)) 来置位——必须先取（或创建）再 set(true)
        cancelledMap.computeIfAbsent(runId, key -> new AtomicBoolean()).set(true);
    }

    /** run 结束后清理，防止 Map 无限增长 */
    public void remove(Long runId) {
        cancelledMap.remove(runId);
    }
}

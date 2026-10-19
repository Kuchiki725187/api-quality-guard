package com.quality.guard.module.testexec.runner.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行线程池配置
 * 默认参数与策略
 * - core=4 / max=8：执行任务以 HTTP 等待为主（IO 密集），线程数不需要很大
 * - 队列 ArrayBlockingQueue(200)：**必须有界**，防止任务无限堆积撑爆内存
 * - 拒绝策略 CallerRunsPolicy：队列满时由调用线程（提交任务的线程）自己执行，形成背压
 *   保证任务的"提交"在资源耗尽时变慢而不是被静默丢弃；也因此不会出现子任务全部被拒的死锁
 *
 */
@Configuration
public class ExecutorConfig {

    /** 注意 Bean 名不能叫 planRunExecutor——与 @Component PlanRunExecutor 的默认 Bean 名冲突 */
    @Bean(name = "planRunExecutorPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor planRunExecutorPool() {
        AtomicInteger sequence = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                4,
                8,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                runnable -> {
                    Thread thread = new Thread(runnable, "api-quality-guard-run-" + sequence.getAndIncrement());
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}

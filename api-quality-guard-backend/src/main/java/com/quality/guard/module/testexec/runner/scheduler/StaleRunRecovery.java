package com.quality.guard.module.testexec.runner.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 服务重启恢复：进程重启后，数据库里遗留的 PENDING/RUNNING 任务已经不可能再执行完成
 * （执行线程在内存中，重启即丢失），启动时统一置为 ERROR，避免任务永远卡在执行中
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleRunRecovery implements ApplicationRunner {

    private final TestRunMapper testRunMapper;

    @Override
    public void run(ApplicationArguments args) {

        int affected = testRunMapper.update(null, new LambdaUpdateWrapper<TestRun>()
               .set(TestRun::getStatus, "ERROR")
               .set(TestRun::getFinishedAt, LocalDateTime.now())
                .in(TestRun::getStatus, "PENDING", "RUNNING"));

         if (affected > 0) {
            log.warn("启动恢复：{} 个未完成执行任务已标记为 ERROR", affected);
        }
    }
}

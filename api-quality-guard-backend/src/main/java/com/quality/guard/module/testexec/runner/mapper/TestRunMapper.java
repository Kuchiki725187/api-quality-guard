package com.quality.guard.module.testexec.runner.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TestRunMapper extends BaseMapper<TestRun> {

    /**
     * 按状态聚合某次运行的明细数量（返回行：status / cnt）。
     * 统计直接以 test_result 明细为准，是"任务统计与明细记录一致"的保证。
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM test_result WHERE run_id = #{runId} GROUP BY status")
    List<Map<String, Object>> selectStatisticsByRunId(@Param("runId") Long runId);

    /**
     * 启动恢复属于系统级操作，需要覆盖所有用户；显式忽略 owner_id 隔离。
     */
    @Update("UPDATE test_run SET status = 'ERROR', finished_at = #{finishedAt} "
            + "WHERE status IN ('PENDING', 'RUNNING')")
    @InterceptorIgnore(tenantLine = "true")
    int markUnfinishedAsError(@Param("finishedAt") LocalDateTime finishedAt);
}

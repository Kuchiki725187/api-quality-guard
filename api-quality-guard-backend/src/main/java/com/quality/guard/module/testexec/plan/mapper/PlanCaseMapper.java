package com.quality.guard.module.testexec.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quality.guard.module.testexec.plan.entity.PlanCase;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PlanCaseMapper extends BaseMapper<PlanCase> {

    /** 按计划查询关联用例 ID（保持关联插入顺序） */
    @Select("SELECT case_id FROM test_plan_case WHERE plan_id = #{planId} ORDER BY id")
    List<Long> selectCaseIds(@Param("planId") Long planId);
}

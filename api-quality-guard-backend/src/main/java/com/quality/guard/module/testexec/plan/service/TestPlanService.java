package com.quality.guard.module.testexec.plan.service;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.module.testexec.plan.dto.PlanCreateDTO;
import com.quality.guard.module.testexec.plan.dto.PlanUpdateDTO;
import com.quality.guard.module.testexec.plan.vo.TestPlanVO;

public interface TestPlanService {

    Long create(PlanCreateDTO dto);

    PageResult<TestPlanVO> page(long page, long pageSize, String name);

    /** 详情：含 caseIds 与用例摘要列表 */
    TestPlanVO detail(Long id);

    /** 更新：caseIds 非空时整体替换关联 */
    void update(Long id, PlanUpdateDTO dto);

    /** 逻辑删除计划并清理关联；存在 PENDING/RUNNING 的运行任务时拒绝（409） */
    void delete(Long id);
}

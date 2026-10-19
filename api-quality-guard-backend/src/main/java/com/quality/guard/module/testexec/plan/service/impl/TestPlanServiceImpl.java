package com.quality.guard.module.testexec.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.plan.dto.PlanCreateDTO;
import com.quality.guard.module.testexec.plan.dto.PlanUpdateDTO;
import com.quality.guard.module.testexec.plan.entity.PlanCase;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.PlanCaseMapper;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.plan.service.TestPlanService;
import com.quality.guard.module.testexec.plan.vo.TestPlanVO;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl implements TestPlanService {

    private final TestPlanMapper testPlanMapper;
    private final PlanCaseMapper planCaseMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestRunMapper testRunMapper;

    @Override
    @Transactional
    public Long create(PlanCreateDTO dto) {
        validateCaseIds(dto.getCaseIds());
        TestPlan plan = new TestPlan();
        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setExecutionConfig(dto.getExecutionConfig());
        testPlanMapper.insert(plan);
        insertPlanCases(plan.getId(), dto.getCaseIds());
        return plan.getId();
    }

    @Override
    public PageResult<TestPlanVO> page(long page, long pageSize, String name) {
        Page<TestPlan> result = testPlanMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<TestPlan>()
                        .like(StringUtils.hasText(name), TestPlan::getName, name)
                        .orderByDesc(TestPlan::getId));
        // 计划数量有限，用例数逐条统计（N+1）当前可接受；数据量大时改 group by 统计
        List<TestPlanVO> records = result.getRecords().stream()
                .map(plan -> {
                    TestPlanVO vo = toVO(plan);
                    vo.setCaseCount(planCaseMapper.selectCaseIds(plan.getId()).size());
                    return vo;
                })
                .toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public TestPlanVO detail(Long id) {
        TestPlan plan = requireById(id);
        TestPlanVO vo = toVO(plan);
        List<Long> caseIds = planCaseMapper.selectCaseIds(id);
        vo.setCaseIds(caseIds);
        vo.setCaseCount(caseIds.size());
        if (!caseIds.isEmpty()) {
            vo.setCases(testCaseMapper.selectBatchIds(caseIds).stream()
                    .map(this::toBrief)
                    .toList());
        }
        return vo;
    }

    @Override
    @Transactional
    public void update(Long id, PlanUpdateDTO dto) {
        TestPlan plan = requireById(id);
        if (StringUtils.hasText(dto.getName())) {
            plan.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            plan.setDescription(dto.getDescription());
        }
        if (dto.getExecutionConfig() != null) {
            plan.setExecutionConfig(dto.getExecutionConfig());
        }
        testPlanMapper.updateById(plan);

        if (dto.getCaseIds() != null) {
            if (dto.getCaseIds().isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "用例列表不能为空");
            }
            validateCaseIds(dto.getCaseIds());
            planCaseMapper.delete(new LambdaQueryWrapper<PlanCase>()
                    .eq(PlanCase::getPlanId, id));
            insertPlanCases(id, dto.getCaseIds());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireById(id);
        Long activeRuns = testRunMapper.selectCount(new LambdaQueryWrapper<TestRun>()
                .eq(TestRun::getPlanId, id)
                .in(TestRun::getStatus, "PENDING", "RUNNING"));
        if (activeRuns != null && activeRuns > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "计划存在进行中的执行任务，无法删除");
        }
        planCaseMapper.delete(new LambdaQueryWrapper<PlanCase>()
                .eq(PlanCase::getPlanId, id));
        testPlanMapper.deleteById(id);
    }

    private void validateCaseIds(List<Long> caseIds) {
        List<Long> distinct = caseIds.stream().distinct().toList();
        if (testCaseMapper.selectBatchIds(distinct).size() != distinct.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "关联用例中包含不存在的用例 ID");
        }
    }

    private void insertPlanCases(Long planId, List<Long> caseIds) {
        for (Long caseId : caseIds.stream().distinct().toList()) {
            PlanCase planCase = new PlanCase();
            planCase.setPlanId(planId);
            planCase.setCaseId(caseId);
            planCaseMapper.insert(planCase);
        }
    }

    private TestPlan requireById(Long id) {
        TestPlan plan = testPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测试计划不存在: " + id);
        }
        return plan;
    }

    private TestPlanVO toVO(TestPlan plan) {
        TestPlanVO vo = new TestPlanVO();
        vo.setId(plan.getId());
        vo.setName(plan.getName());
        vo.setDescription(plan.getDescription());
        vo.setExecutionConfig(plan.getExecutionConfig());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        return vo;
    }

    private TestPlanVO.PlanCaseBrief toBrief(TestCase testCase) {
        TestPlanVO.PlanCaseBrief brief = new TestPlanVO.PlanCaseBrief();
        brief.setId(testCase.getId());
        brief.setName(testCase.getName());
        brief.setGenerationType(testCase.getGenerationType());
        return brief;
    }
}

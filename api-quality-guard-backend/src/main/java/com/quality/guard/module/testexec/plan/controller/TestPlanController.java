package com.quality.guard.module.testexec.plan.controller;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.Result;
import com.quality.guard.module.testexec.plan.dto.PlanCreateDTO;
import com.quality.guard.module.testexec.plan.dto.PlanUpdateDTO;
import com.quality.guard.module.testexec.plan.service.TestPlanService;
import com.quality.guard.module.testexec.plan.vo.TestPlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 测试计划管理。
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class TestPlanController {

    private final TestPlanService testPlanService;

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody PlanCreateDTO dto) {
        return Result.success(Map.of("id", testPlanService.create(dto)));
    }

    @GetMapping
    public Result<PageResult<TestPlanVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String name) {
        return Result.success(testPlanService.page(page, pageSize, name));
    }

    @GetMapping("/{id}")
    public Result<TestPlanVO> detail(@PathVariable Long id) {
        return Result.success(testPlanService.detail(id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlanUpdateDTO dto) {
        testPlanService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        testPlanService.delete(id);
        return Result.success();
    }
}

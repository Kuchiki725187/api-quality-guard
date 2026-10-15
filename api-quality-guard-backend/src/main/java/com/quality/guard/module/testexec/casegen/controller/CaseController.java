package com.quality.guard.module.testexec.casegen.controller;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.Result;
import com.quality.guard.module.testexec.casegen.dto.CaseCreateDTO;
import com.quality.guard.module.testexec.casegen.dto.CaseGenerateDTO;
import com.quality.guard.module.testexec.casegen.dto.CaseUpdateDTO;
import com.quality.guard.module.testexec.casegen.service.CaseService;
import com.quality.guard.module.testexec.casegen.vo.GenerateResultVO;
import com.quality.guard.module.testexec.casegen.vo.TestCaseVO;
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
 * 测试用例管理：手动 CRUD + 规则生成
 */
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CaseCreateDTO dto) {
        return Result.success(Map.of("id", caseService.create(dto)));
    }

    @GetMapping
    public Result<PageResult<TestCaseVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long apiId,
            @RequestParam(required = false) String generationType,
            @RequestParam(required = false) String name) {
        return Result.success(caseService.page(page, pageSize, apiId, generationType, name));
    }

    @GetMapping("/{id}")
    public Result<TestCaseVO> detail(@PathVariable Long id) {
        return Result.success(caseService.detail(id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CaseUpdateDTO dto) {
        caseService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        caseService.delete(id);
        return Result.success();
    }

    @PostMapping("/generate")
    public Result<GenerateResultVO> generate(@Valid @RequestBody CaseGenerateDTO dto) {
        return Result.success(caseService.generate(dto));
    }
}

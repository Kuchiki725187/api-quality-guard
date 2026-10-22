package com.quality.guard.module.ai.enhance.controller;

import com.quality.guard.common.result.Result;
import com.quality.guard.module.ai.enhance.dto.EnhanceRequestDTO;
import com.quality.guard.module.ai.enhance.service.AiCaseEnhanceService;
import com.quality.guard.module.testexec.casegen.vo.GenerateResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 用例增强接口：基于接口契约与业务背景生成复杂场景候选用例
 */
@RestController
@RequiredArgsConstructor
public class EnhanceController {

    private final AiCaseEnhanceService aiCaseEnhanceService;

    @PostMapping("/api/cases/ai-generate")
    public Result<GenerateResultVO> enhance(@Valid @RequestBody EnhanceRequestDTO dto) {
        return Result.success(aiCaseEnhanceService.enhance(dto));
    }
}

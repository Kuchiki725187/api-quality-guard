package com.quality.guard.module.ai.diagnosis.controller;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.Result;
import com.quality.guard.module.ai.diagnosis.dto.DiagnosisFeedbackDTO;
import com.quality.guard.module.ai.diagnosis.dto.DiagnosisRequestDTO;
import com.quality.guard.module.ai.diagnosis.service.DiagnosisService;
import com.quality.guard.module.ai.diagnosis.vo.AiDiagnosisVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 故障诊断接口
 */
@RestController
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    /** 对失败/异常结果发起诊断（同步等待 Agent 返回） */
    @PostMapping("/api/results/{id}/diagnose")
    public Result<AiDiagnosisVO> diagnose(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) DiagnosisRequestDTO dto) {
        String extraContext = dto == null ? null : dto.getExtraContext();
        return Result.success(diagnosisService.diagnose(id, extraContext));
    }

    @GetMapping("/api/diagnoses")
    public Result<PageResult<AiDiagnosisVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long testResultId,
            @RequestParam(required = false) String humanFeedback) {
        return Result.success(diagnosisService.page(page, pageSize, testResultId, humanFeedback));
    }

    @GetMapping("/api/diagnoses/{id}")
    public Result<AiDiagnosisVO> detail(@PathVariable Long id) {
        return Result.success(diagnosisService.detail(id));
    }

    /** 人工反馈（用于诊断准确率统计与 prompt 优化） */
    @PutMapping("/api/diagnoses/{id}/feedback")
    public Result<Void> feedback(@PathVariable Long id, @Valid @RequestBody DiagnosisFeedbackDTO dto) {
        diagnosisService.feedback(id, dto);
        return Result.success();
    }
}

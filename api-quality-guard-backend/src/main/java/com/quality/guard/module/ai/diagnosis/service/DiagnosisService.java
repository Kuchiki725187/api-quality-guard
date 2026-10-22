package com.quality.guard.module.ai.diagnosis.service;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.module.ai.diagnosis.dto.DiagnosisFeedbackDTO;
import com.quality.guard.module.ai.diagnosis.vo.AiDiagnosisVO;

public interface DiagnosisService {

    /** 发起诊断：仅 FAILED/ERROR 的结果可诊断 */
    AiDiagnosisVO diagnose(Long resultId, String extraContext);

    PageResult<AiDiagnosisVO> page(long page, long pageSize, Long testResultId, String humanFeedback);

    AiDiagnosisVO detail(Long id);

    /** 人工反馈：CONFIRMED / REJECTED / REVISED */
    void feedback(Long id, DiagnosisFeedbackDTO dto);
}

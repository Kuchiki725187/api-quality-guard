package com.quality.guard.module.ai.enhance.service;

import com.quality.guard.module.ai.enhance.dto.EnhanceRequestDTO;
import com.quality.guard.module.testexec.casegen.vo.GenerateResultVO;

public interface AiCaseEnhanceService {

    /** AI 增强生成：调用 Agent 生成复杂业务场景候选用例并落库（generationType=AI，按 apiId+用例名幂等） */
    GenerateResultVO enhance(EnhanceRequestDTO dto);
}

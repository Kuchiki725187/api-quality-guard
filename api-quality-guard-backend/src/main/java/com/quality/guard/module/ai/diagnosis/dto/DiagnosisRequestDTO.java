package com.quality.guard.module.ai.diagnosis.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起诊断的请求体（可选）
 */
@Data
public class DiagnosisRequestDTO {

    /** 用户补充的业务背景（会作为证据提供给模型） */
    @Size(max = 2000, message = "补充背景不能超过 2000 字符")
    private String extraContext;
}

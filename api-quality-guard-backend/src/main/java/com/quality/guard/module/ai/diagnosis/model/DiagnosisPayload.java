package com.quality.guard.module.ai.diagnosis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 结构化诊断结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisPayload {

    private String conclusion;

    private List<DiagnosisCause> causes;

    private List<String> suggestions;
}

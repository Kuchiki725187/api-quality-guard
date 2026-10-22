package com.quality.guard.module.ai.diagnosis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个候选原因
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisCause {

    private String cause;

    /** 置信度 0~1 */
    private Double confidence;

    private List<String> evidence;
}

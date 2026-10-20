package com.quality.guard.module.gate.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 门禁违规明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GateViolation {

    /** 违规规则名，如"质量总分未达标" */
    private String rule;

    /** 具体说明（含实际值与阈值） */
    private String detail;
}

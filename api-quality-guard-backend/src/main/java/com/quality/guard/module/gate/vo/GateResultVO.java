package com.quality.guard.module.gate.vo;

import com.quality.guard.module.gate.model.GateViolation;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 门禁判定结果
 */
@Data
public class GateResultVO {

    private Long recordId;

    private Long runId;

    /** true 放行 / false 阻断 */
    private boolean passed;

    private BigDecimal score;

    /** 本次判定使用的阈值快照 */
    private Map<String, Object> thresholds;

    /** 违规明细；通过时为空列表 */
    private List<GateViolation> violations;
}

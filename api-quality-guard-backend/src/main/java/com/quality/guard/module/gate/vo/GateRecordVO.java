package com.quality.guard.module.gate.vo;

import com.quality.guard.module.gate.model.GateViolation;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 门禁检查历史记录视图对象
 */
@Data
public class GateRecordVO {

    private Long id;

    private Long runId;

    private boolean passed;

    private BigDecimal score;

    private Map<String, Object> thresholds;

    private List<GateViolation> violations;

    private LocalDateTime createdAt;
}

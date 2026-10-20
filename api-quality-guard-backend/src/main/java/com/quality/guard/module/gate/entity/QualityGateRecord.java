package com.quality.guard.module.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.quality.guard.module.gate.model.GateViolation;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 质量门禁检查记录，对应 quality_gate_record 表（审计留档）
 */
@Data
@TableName(value = "quality_gate_record", autoResultMap = true)
public class QualityGateRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Boolean passed;

    private BigDecimal score;

    /** 判定时使用的阈值快照 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> thresholdSnapshot;

    /** 违规明细 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<GateViolation> violations;

    private LocalDateTime createdAt;
}

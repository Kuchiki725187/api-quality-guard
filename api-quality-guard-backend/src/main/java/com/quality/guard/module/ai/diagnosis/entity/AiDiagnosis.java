package com.quality.guard.module.ai.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.quality.guard.module.ai.diagnosis.model.DiagnosisPayload;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 诊断记录，对应 ai_diagnosis 表
 */
@Data
@TableName(value = "ai_diagnosis", autoResultMap = true)
public class AiDiagnosis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 test_result.id */
    private Long testResultId;

    /** 诊断输入摘要（JSON 文本，控制长度） */
    private String inputSummary;

    /** 结构化诊断输出（与模型原始输出分离，便于校验追溯） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private DiagnosisPayload candidateCauses;

    /** 模型原始输出（多轮拼接），便于排查解析/校验失败 */
    private String rawModelOutput;

    private String modelName;

    private Long durationMs;

    private Integer inputTokens;

    private Integer outputTokens;

    /** 人工反馈：CONFIRMED / REJECTED / REVISED */
    private String humanFeedback;

    private String humanComment;

    private LocalDateTime createdAt;
}

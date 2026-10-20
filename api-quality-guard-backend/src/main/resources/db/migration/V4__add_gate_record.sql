-- V4: 质量门禁检查记录（每次门禁调用留档，供审计与历史查询）
CREATE TABLE quality_gate_record
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    run_id             BIGINT      NOT NULL COMMENT '关联 test_run.id',
    passed             TINYINT     NOT NULL DEFAULT 0 COMMENT '门禁结论：1-通过 0-阻断',
    score              DECIMAL(5, 2) NULL COMMENT '检查时运行的综合评分',
    threshold_snapshot JSON        NULL COMMENT '判定时使用的阈值快照（scoreThreshold / maxFailedCount / maxErrorCount）',
    violations         JSON        NULL COMMENT '违规明细列表（规则名 + 说明）',
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检查时间',
    PRIMARY KEY (id),
    KEY idx_run_id (run_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='质量门禁检查记录';

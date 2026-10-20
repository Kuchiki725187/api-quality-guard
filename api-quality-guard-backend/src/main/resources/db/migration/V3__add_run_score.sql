-- V3: test_run 增加质量评分字段
-- 评分在 run 进入终态（SUCCESS/FAILED）时计算并落库，明细保证可解释、可复现
ALTER TABLE test_run
    ADD COLUMN score DECIMAL(5, 2) NULL COMMENT '综合质量评分（0-100），未评分时为 NULL' AFTER error_count,
    ADD COLUMN score_detail JSON NULL COMMENT '评分维度明细：各维度得分/实际权重/计算基数' AFTER score;

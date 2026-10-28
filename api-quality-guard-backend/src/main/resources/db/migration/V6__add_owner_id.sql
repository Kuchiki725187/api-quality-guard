ALTER TABLE api_definition
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON api_definition (owner_id);

ALTER TABLE test_case
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON test_case (owner_id);

ALTER TABLE test_plan
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON test_plan (owner_id);

ALTER TABLE test_plan_case
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON test_plan_case (owner_id);

ALTER TABLE test_run
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON test_run (owner_id);

ALTER TABLE test_result
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON test_result (owner_id);

ALTER TABLE ai_diagnosis
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON ai_diagnosis (owner_id);

ALTER TABLE quality_gate_record
    ADD COLUMN owner_id BIGINT NULL COMMENT '数据属主 sys_user.id' AFTER id;
CREATE INDEX idx_owner_id ON quality_gate_record (owner_id);

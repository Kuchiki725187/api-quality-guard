-- V2: api_definition 增加被测服务根地址
-- 来源：OpenAPI 文档 servers[0].url（Swagger 2.0 为 host + basePath），手动创建时由用户填写
ALTER TABLE api_definition
    ADD COLUMN base_url VARCHAR(500) NULL COMMENT '被测服务根地址，如 http://localhost:9090' AFTER description;

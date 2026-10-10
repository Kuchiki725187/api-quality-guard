
CREATE TABLE api_definition
(
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(255)  NOT NULL COMMENT '接口名称',
    http_method     VARCHAR(10)   NOT NULL COMMENT 'HTTP 方法：GET/POST/PUT/DELETE 等',
    path            VARCHAR(500)  NOT NULL COMMENT '接口路径，如 /api/users/{id}',
    description     VARCHAR(1000) NULL COMMENT '接口描述',
    request_schema  JSON          NULL COMMENT '请求定义：参数位置/名称/类型/是否必填/约束等结构化描述',
    response_schema JSON          NULL COMMENT '响应定义：状态码、响应字段结构等结构化描述',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_method_path (http_method, path)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='接口定义';

-- 2. 测试用例表：一条用例 = 一组具体请求参数 + 一组断言规则
CREATE TABLE test_case
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    api_id          BIGINT       NOT NULL COMMENT '所属接口，关联 api_definition.id',
    name            VARCHAR(255) NOT NULL COMMENT '用例名称',
    request_params  JSON         NULL COMMENT '请求参数值：按位置（path/query/header/body）组织的实际取值',
    assertion_rules JSON         NULL COMMENT '断言规则列表：状态码、JSON 字段、响应时间等',
    generation_type VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT '生成方式：MANUAL-手动 RULE-规则生成 AI-AI 增强',
    generation_note VARCHAR(500) NULL COMMENT '生成来源说明，如：BoundaryValueStrategy 生成的边界值场景',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_api_id (api_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='测试用例';

-- 3. 测试计划表：把多条用例组织成一次可批量执行的计划
CREATE TABLE test_plan
(
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(255)  NOT NULL COMMENT '计划名称',
    description      VARCHAR(1000) NULL COMMENT '计划描述',
    execution_config JSON          NULL COMMENT '执行配置：并发度、超时、失败策略等',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='测试计划';

-- 3.1 计划-用例关联表：计划与用例是多对多关系，用中间表而不是 JSON 数组存 id，
--     好处：支持唯一约束防重复、便于按用例反查计划、SQL 统计更直接
CREATE TABLE test_plan_case
(
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_id    BIGINT   NOT NULL COMMENT '关联 test_plan.id',
    case_id    BIGINT   NOT NULL COMMENT '关联 test_case.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_case (plan_id, case_id),
    KEY idx_case_id (case_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='测试计划-用例关联';

-- 4. 测试运行表：一次批量执行任务（一个 test_run 下含多条 test_result）
CREATE TABLE test_run
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_id       BIGINT      NULL COMMENT '关联 test_plan.id，为空表示非计划触发的执行',
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED 等',
    total_count   INT         NOT NULL DEFAULT 0 COMMENT '用例总数',
    success_count INT         NOT NULL DEFAULT 0 COMMENT '通过数（断言全部通过）',
    failed_count  INT         NOT NULL DEFAULT 0 COMMENT '失败数（断言未通过）',
    error_count   INT         NOT NULL DEFAULT 0 COMMENT '异常数（请求本身执行失败，未产生有效断言）',
    started_at    DATETIME    NULL COMMENT '开始执行时间',
    finished_at   DATETIME    NULL COMMENT '执行结束时间',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_plan_id (plan_id),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='测试运行（批量执行任务）';

-- 5. 单用例执行结果表：请求/响应快照 + 每条断言的明细结果
CREATE TABLE test_result
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    run_id             BIGINT      NOT NULL COMMENT '所属执行任务，关联 test_run.id',
    case_id            BIGINT      NOT NULL COMMENT '执行的用例，关联 test_case.id',
    status             VARCHAR(20) NOT NULL COMMENT '结果：PASSED-通过 FAILED-断言失败 ERROR-执行异常',
    request_snapshot   JSON        NULL COMMENT '请求快照：方法、URL、请求头、请求体',
    response_snapshot  JSON        NULL COMMENT '响应快照：状态码、响应头、响应体（超长时可截断并标记）',
    assertion_results  JSON        NULL COMMENT '每条断言的实际值/期望值/通过与否',
    error_message      TEXT        NULL COMMENT '执行异常或失败说明',
    duration_ms        BIGINT      NULL COMMENT '执行耗时（毫秒）',
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_run_id (run_id),
    KEY idx_case_id (case_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='单用例执行结果';

-- 6. AI 诊断表：针对失败的执行结果生成的结构化诊断
CREATE TABLE ai_diagnosis
(
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    test_result_id   BIGINT        NOT NULL COMMENT '关联 test_result.id（通常为失败或异常的结果）',
    input_summary    TEXT          NULL COMMENT '诊断输入摘要：发送给模型的失败上下文概述',
    candidate_causes JSON          NULL COMMENT '结构化诊断输出：候选原因列表（原因、证据、置信度）',
    raw_model_output TEXT          NULL COMMENT '模型原始输出，便于排查解析/校验失败',
    model_name       VARCHAR(100)  NULL COMMENT '模型名称',
    duration_ms      BIGINT        NULL COMMENT '模型调用耗时（毫秒）',
    input_tokens     INT           NULL COMMENT '输入 token 数（用于成本统计）',
    output_tokens    INT           NULL COMMENT '输出 token 数（用于成本统计）',
    human_feedback   VARCHAR(20)   NULL COMMENT '人工反馈：CONFIRMED-确认 REJECTED-否定 REVISED-修正',
    human_comment    VARCHAR(1000) NULL COMMENT '人工反馈说明',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_test_result_id (test_result_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 故障诊断';

# API Quality Guard 后端接口文档

> 版本：v0.1（阶段 0 设计初稿，随阶段推进持续修订）
> 基地址：`http://localhost:8080`，业务接口统一以 `/api` 为前缀
> 覆盖范围：平台规划的全部后端接口。已实现的接口标记 ✅，其余为设计契约，在对应阶段实现。

## 目录

- [1. 通用约定](#1-通用约定)
- [2. 健康检查](#2-健康检查)
- [3. 接口定义管理](#3-接口定义管理)
- [4. 测试用例管理](#4-测试用例管理)
- [5. 测试计划](#5-测试计划)
- [6. 执行与结果](#6-执行与结果)
- [7. AI 诊断与增强](#7-ai-诊断与增强)
- [8. 报告与实验评估](#8-报告与实验评估)
- [附录 A：核心 JSON 结构设计](#附录-a核心-json-结构设计)
- [附录 B：开放设计点](#附录-b开放设计点)

---

## 1. 通用约定

### 1.1 统一响应结构

所有业务接口返回统一包裹结构（对应后端 `common.result.Result`）：

```json
{
  "code": 200,
  "message": "成功",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 状态码，与 HTTP 状态码语义保持一致 |
| message | string | 提示信息；失败时为具体原因 |
| data | any | 业务数据；失败时为 null |

### 1.2 错误处理约定

HTTP 状态码与语义保持一致（非 200 时 `data` 为 null）：

| HTTP 状态 | 场景举例 |
| --- | --- |
| 400 | 参数校验失败、OpenAPI 文档格式非法、请求体不是合法 JSON |
| 404 | 目标资源不存在（接口定义 / 用例 / 计划 / 运行 / 诊断），具体对象见 message |
| 405 | 请求方法不支持 |
| 409 | 状态冲突，如取消一个已结束的任务、删除有关联数据的对象 |
| 500 | 未预期的服务端异常（message 只给笼统信息，细节在服务端日志） |
| 503 | 依赖的外部服务不可用，如 AI 模型调用失败（可稍后重试） |

> 扩展规划：若前端将来需要针对具体业务错误做分支处理（如"用例名重复"），
> 再引入四位业务码（1001+），届时 `BusinessException` 拆分为 httpStatus 与 bizCode 两个字段，本约定向后兼容。

### 1.3 分页约定

分页请求参数（query）：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| page | int | 否 | 1 | 页码，从 1 开始 |
| pageSize | int | 否 | 10 | 每页条数，最大 100 |

分页响应 `data` 结构：

```json
{
  "records": [],
  "total": 57,
  "current": 1,
  "size": 10
}
```

### 1.4 命名与格式

- URL 路径与 JSON 字段使用 camelCase（`httpMethod`、`requestSchema`），数据库列使用 snake_case，由 MyBatis-Plus 自动映射。
- 时间字段为 ISO-8601 字符串（如 `2026-10-10T19:12:11`），前端展示时自行格式化。
- ID 为 Long 类型，自增生成。
- 请求/响应体均为 `application/json;charset=UTF-8`（文件上传除外）。

---

## 2. 健康检查 ✅

### 2.1 应用健康检查

`GET /api/health`

检查应用与数据库连通性，供前端状态页和部署探针使用。

**响应示例**

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "application": "api-quality-guard",
    "status": "UP",
    "database": "UP",
    "timestamp": "2026-10-10T19:12:11"
  }
}
```

`status` 取值：`UP`（正常）/ `DEGRADED`（应用存活但数据库不可用）。

### 2.2 Actuator 运维端点 ✅

`GET /actuator/health` —— Spring Boot Actuator 标准健康端点，展示各组件的详细健康状态，不属于业务接口。

---

## 3. 接口定义管理

对应阶段 1。接口定义来自手动录入或 OpenAPI/Swagger 文档导入。

### 3.1 接口定义对象

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键 |
| name | string | 接口名称 |
| httpMethod | string | GET / POST / PUT / DELETE / PATCH |
| path | string | 接口路径，如 `/users/{id}` |
| description | string | 描述，可为空 |
| requestSchema | object | 请求定义，结构见 [附录 A.1](#a1-requestschema请求定义) |
| responseSchema | object | 响应定义，结构见 [附录 A.2](#a2-responseschema响应定义) |
| baseUrl | string | 被测服务根地址，导入时取自文档 `servers`，手动创建时填写；见 [附录 B.1](#b1-base_url-的归属待确认) |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 3.2 创建接口定义

`POST /api/apis`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 接口名称，≤255 字符 |
| httpMethod | string | 是 | 取值见上（自动转大写） |
| path | string | 是 | 以 `/` 开头 |
| description | string | 否 | ≤1000 字符 |
| requestSchema | object | 否 | 结构见附录 A.1 |
| responseSchema | object | 否 | 结构见附录 A.2 |
| baseUrl | string | 否 | 如 `http://localhost:9090` |

**请求示例**

```json
{
  "name": "查询用户详情",
  "httpMethod": "GET",
  "path": "/users/{id}",
  "description": "根据用户 ID 查询用户信息",
  "baseUrl": "http://localhost:9090",
  "requestSchema": {
    "contentType": "application/json",
    "params": [
      { "name": "id", "in": "path", "type": "integer", "required": true, "constraints": { "minimum": 1 } }
    ]
  },
  "responseSchema": {
    "successStatusCode": 200,
    "schema": {
      "type": "object",
      "properties": {
        "code": { "type": "integer" },
        "data": {
          "type": "object",
          "properties": {
            "id": { "type": "integer" },
            "name": { "type": "string" }
          }
        }
      }
    }
  }
}
```

**响应示例**

```json
{ "code": 200, "message": "成功", "data": { "id": 1 } }
```

### 3.3 分页查询接口定义

`GET /api/apis`

**查询参数**：`page`、`pageSize`（见 1.3），另支持：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 名称模糊匹配 |
| httpMethod | string | 精确匹配 |

**响应 data**：分页结构，`records` 为接口定义对象数组（含 requestSchema / responseSchema）。

### 3.4 查询接口定义详情

`GET /api/apis/{id}`

**响应 data**：接口定义对象。不存在时返回 404。

### 3.5 更新接口定义

`PUT /api/apis/{id}`

请求体同 3.2（字段均可选，只更新传入的字段）。不存在时返回 404。

### 3.6 删除接口定义

`DELETE /api/apis/{id}`

逻辑删除。若其下存在关联测试用例，返回 409 并提示先处理关联用例。

**响应示例**

```json
{ "code": 200, "message": "成功", "data": null }
```

### 3.7 导入 OpenAPI / Swagger 文档

`POST /api/apis/import`

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | 文档文件，支持 `.json` / `.yaml` / `.yml`，建议 ≤2MB |

**响应 data**

```json
{
  "documentTitle": "Pet Store API",
  "documentVersion": "1.0.0",
  "totalParsed": 13,
  "imported": 12,
  "updated": 0,
  "skipped": 1,
  "failures": [
    { "path": "/pets/{petId}/uploadImage", "reason": "不支持的请求体类型" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| totalParsed | 文档中解析出的接口总数 |
| imported | 新增入库数（方法 + 路径在库中不存在） |
| updated | 更新数（方法 + 路径已存在，更新其定义并保留原 ID） |
| skipped | 主动跳过的接口数（如不支持的 body 类型） |
| failures | 解析异常明细，含路径与原因 |

> 去重规则：同一接口以「HTTP 方法 + 路径」判重。已存在时更新定义（名称 / 描述 / baseUrl / 请求与响应结构）并**保留原 ID**，
> 保证已有关联数据（如测试用例）不断裂；因此重复导入同一文档是幂等的。

**错误**：400 + message（文档格式非法 / 不是有效的 OpenAPI 文档 / 版本不支持）。

> 解析端支持 OpenAPI 3.x 与 Swagger 2.0；`$ref` 引用需先解析展开，循环引用需防护。

---

## 4. 测试用例管理

对应阶段 2。用例 = 一组具体请求参数值 + 一组断言规则。

### 4.1 测试用例对象

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键 |
| apiId | long | 所属接口定义 ID |
| apiName | string | 所属接口名称（查询时联表带出） |
| name | string | 用例名称 |
| requestParams | object | 具体请求参数值，结构见 [附录 A.3](#a3-request_params用例请求参数值) |
| assertionRules | array | 断言规则列表，结构见 [附录 A.4](#a4-assertion_rules断言规则) |
| generationType | string | `MANUAL` 手动 / `RULE` 规则生成 / `AI` AI 增强 |
| generationNote | string | 生成来源说明，如 `BoundaryValueStrategy: 取 maximum+1` |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 4.2 手动创建用例

`POST /api/cases`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| apiId | long | 是 | 所属接口定义 |
| name | string | 是 | ≤255 字符 |
| requestParams | object | 是 | 结构见附录 A.3 |
| assertionRules | array | 是 | 至少一条，结构见附录 A.4 |

`generationType` 由服务端置为 `MANUAL`，不接受客户端传入。

**请求示例**

```json
{
  "apiId": 1,
  "name": "查询用户-正常值",
  "requestParams": {
    "path": { "id": 1 }
  },
  "assertionRules": [
    { "type": "STATUS_CODE", "expected": 200 },
    { "type": "JSON_FIELD", "path": "$.code", "operator": "EQUALS", "expected": 0 }
  ]
}
```

**响应示例**

```json
{ "code": 200, "message": "成功", "data": { "id": 10 } }
```

### 4.3 分页查询用例

`GET /api/cases`

**查询参数**：`page`、`pageSize`，另支持 `apiId`（精确）、`generationType`（精确）、`name`（模糊）。

**响应 data**：分页结构，`records` 为用例对象数组。

### 4.4 查询用例详情

`GET /api/cases/{id}`

### 4.5 更新用例

`PUT /api/cases/{id}`

请求体字段同 4.2，均可选。手动编辑 AI 生成的用例时允许更新 requestParams / assertionRules，`generationType` 保持不变。

### 4.6 删除用例

`DELETE /api/cases/{id}`

逻辑删除。若用例已被测试计划引用，返回 409。

### 4.7 基于约束的规则生成

`POST /api/cases/generate`

根据接口定义的参数约束批量生成基础用例。生成规则由 `module.testcase.generator` 下的策略实现，本接口为统一入口。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| apiId | long | 是 | 目标接口定义 |
| strategies | array | 否 | 启用的生成策略，缺省为全部：`NORMAL` 正常值 / `REQUIRED_MISSING` 必填缺失 / `TYPE_MISMATCH` 类型异常 / `BOUNDARY` 边界值 |

**请求示例**

```json
{
  "apiId": 1,
  "strategies": ["NORMAL", "REQUIRED_MISSING", "BOUNDARY"]
}
```

**响应 data**

```json
{
  "generatedCount": 7,
  "cases": [
    { "id": 11, "name": "查询用户-正常值", "generationType": "RULE", "generationNote": "NormalValueStrategy + 默认断言" },
    { "id": 12, "name": "查询用户-必填缺失(id)", "generationType": "RULE", "generationNote": "RequiredFieldStrategy: 移除 path 必填参数 id" },
    { "id": 13, "name": "查询用户-边界值(id=2)", "generationType": "RULE", "generationNote": "BoundaryValueStrategy: integer minimum=1 的边界外值 1+1" }
  ]
}
```

**错误**：400（接口定义缺少 requestSchema 约束、策略名非法）；404（接口定义不存在）。

> 生成结果必须可解释、可复现：同一接口定义 + 同一策略集，生成结果一致；每条用例的 `generationNote` 说明其来源策略与取值依据。

---

## 5. 测试计划

对应阶段 4。计划把多条用例组织为一次可批量执行的任务。

### 5.1 测试计划对象

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键 |
| name | string | 计划名称 |
| description | string | 描述 |
| executionConfig | object | 执行配置，结构见 [附录 A.5](#a5-execution_config执行配置) |
| caseCount | int | 关联用例数 |
| createdAt / updatedAt | string | 时间 |

### 5.2 创建计划

`POST /api/plans`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | ≤255 字符 |
| description | string | 否 | |
| executionConfig | object | 否 | 缺省用全局默认（并发度 5、超时 5000ms） |
| caseIds | array | 是 | 关联用例 ID 列表，至少一个；建立到 `test_plan_case` 的关联 |

**响应示例**

```json
{ "code": 200, "message": "成功", "data": { "id": 1 } }
```

### 5.3 分页查询计划

`GET /api/plans` —— 查询参数 `page`、`pageSize`、`name`（模糊）。

### 5.4 查询计划详情

`GET /api/plans/{id}`

响应 data 含 `cases` 数组（计划关联的用例摘要列表：id、name、apiName、generationType）。

### 5.5 更新计划

`PUT /api/plans/{id}`

请求体字段同 5.2，均可选；`caseIds` 传入时整体替换用例关联（先删后插，同一事务内完成）。

### 5.6 删除计划

`DELETE /api/plans/{id}`

逻辑删除，同时清理 `test_plan_case` 关联。若存在 PENDING / RUNNING 状态的运行任务，返回 409。

---

## 6. 执行与结果

对应阶段 3（单用例执行与断言）与阶段 4（批量任务调度）。

### 6.1 状态定义

**运行任务（test_run）状态机**

```
PENDING ──> RUNNING ──> SUCCESS    执行完成，且全部用例通过
                    └─> FAILED     执行完成，但存在未通过用例
                    └─> ERROR      任务级异常中断（如线程池拒绝、汇总失败）
PENDING ──> CANCELLED               排队中被取消
RUNNING ──> CANCELLED               执行中被取消（剩余用例不再执行）
```

- 终态：SUCCESS / FAILED / ERROR / CANCELLED，终态不可再流转。
- 非法转换（如 SUCCESS → RUNNING）由服务端拒绝，返回 409。

**单条结果（test_result）状态**

| 状态 | 含义 |
| --- | --- |
| PASSED | 请求执行成功，且全部断言通过 |
| FAILED | 请求执行成功，但存在至少一条断言未通过 |
| ERROR | 请求本身未成功执行（连接失败、超时、响应无法解析），断言无法判定 |

> 关键原则：AI 不参与通过与否的判定，结果只由执行引擎与断言引擎决定。

### 6.2 触发计划批量执行

`POST /api/plans/{id}/runs`

异步提交：校验计划与用例状态后创建 `test_run`（PENDING），提交到线程池执行，立即返回。

**请求体**（可选，用于本次执行的临时覆盖）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| baseUrl | string | 覆盖用例所属接口的默认地址（如切换到测试环境） |
| concurrency | int | 覆盖计划的并发度，1~20 |

**响应示例**

```json
{ "code": 200, "message": "成功", "data": { "runId": 100, "status": "PENDING" } }
```

**错误**：404（计划不存在）；409（计划无用例）；400（并发度超出范围）。

### 6.3 执行单个用例

`POST /api/cases/{id}/run`

同步执行：构建请求、发送、断言、保存结果，一次性返回完整结果对象。用于用例调试。

**请求体**（可选）：`{ "baseUrl": "http://localhost:9090" }`

**响应 data**：单条结果对象（见 6.7），无需再查询。

### 6.4 分页查询运行记录

`GET /api/runs`

**查询参数**：`page`、`pageSize`、`planId`、`status`。

**响应 records 字段**：`id`、`planId`、`planName`、`status`、`totalCount`、`successCount`、`failedCount`、`errorCount`、`startedAt`、`finishedAt`、`createdAt`。

### 6.5 查询运行详情（进度轮询）

`GET /api/runs/{id}`

执行期间前端轮询此接口获取进度。

**响应 data 示例**

```json
{
  "id": 100,
  "planId": 1,
  "planName": "用户模块回归",
  "status": "RUNNING",
  "totalCount": 20,
  "successCount": 12,
  "failedCount": 3,
  "errorCount": 0,
  "startedAt": "2026-10-10T20:00:01",
  "finishedAt": null
}
```

### 6.6 查询运行的结果列表

`GET /api/runs/{id}/results`

**查询参数**：`page`、`pageSize`、`status`（过滤 PASSED / FAILED / ERROR）。

**响应 records 字段**：`id`、`caseId`、`caseName`、`status`、`durationMs`、`errorMessage`（摘要）、`createdAt`。

### 6.7 查询单条结果详情

`GET /api/results/{id}`

**响应 data**

```json
{
  "id": 3001,
  "runId": 100,
  "caseId": 10,
  "caseName": "查询用户-正常值",
  "status": "FAILED",
  "durationMs": 87,
  "requestSnapshot": {
    "method": "GET",
    "url": "http://localhost:9090/users/999",
    "headers": { "Accept": "application/json" },
    "body": null
  },
  "responseSnapshot": {
    "statusCode": 404,
    "headers": { "Content-Type": "application/json" },
    "body": { "code": 40401, "message": "user not found" },
    "bodyTruncated": false
  },
  "assertionResults": [
    { "type": "STATUS_CODE", "expected": 200, "actual": 404, "passed": false },
    { "type": "JSON_FIELD", "path": "$.code", "operator": "EQUALS", "expected": 0, "actual": 40401, "passed": false }
  ],
  "errorMessage": null,
  "createdAt": "2026-10-10T20:00:03"
}
```

> 快照结构见 [附录 A.6](#a6-执行快照结构)。响应体超过阈值（如 64KB）时截断存储并置 `bodyTruncated=true`。

### 6.8 取消运行任务

`POST /api/runs/{id}/cancel`

仅 PENDING / RUNNING 状态可取消；对终态调用返回 409。运行中的任务取消后，未开始的用例不再执行，已产生的结果保留。

**响应 data**：`{ "runId": 100, "status": "CANCELLED" }`

---

## 7. AI 诊断与增强

对应阶段 5。AI 只提供建议与诊断，不决定测试通过与否。

### 7.1 发起失败诊断

`POST /api/results/{id}/diagnose`

针对 FAILED / ERROR 的结果生成结构化诊断。服务端构建诊断上下文（接口定义摘要、请求/响应快照、断言差异），调用大模型并校验输出后入库。

**请求体**（可选）：`{ "extraContext": "该接口本周刚上线新版本" }`

**响应 data**

```json
{
  "id": 501,
  "testResultId": 3001,
  "modelName": "qwen-plus",
  "durationMs": 4200,
  "candidateCauses": {
    "conclusion": "响应 404 且业务码 40401，最可能是请求数据不存在",
    "causes": [
      {
        "cause": "测试使用的用户 ID 999 在目标环境中不存在",
        "confidence": 0.85,
        "evidence": ["HTTP 404", "响应 message: user not found"]
      },
      {
        "cause": "断言期望值与接口实际契约不符（接口文档定义的业务码非 0）",
        "confidence": 0.3,
        "evidence": ["断言期望 $.code = 0，实际接口返回 40401 自增码"]
      }
    ],
    "suggestions": ["确认测试数据准备逻辑", "核对接口文档的业务码约定"]
  }
}
```

**错误**：404（结果不存在）；400（对 PASSED 结果发起诊断）；503（模型调用失败，可重试）。

> 输出结构见 [附录 A.7](#a7-ai-诊断输出结构)。模型输出必须经 DTO 反序列化 + 业务校验（causes 非空、confidence ∈ [0,1]），校验失败时保存原始输出并返回明确错误。

### 7.2 分页查询诊断记录

`GET /api/diagnoses`

**查询参数**：`page`、`pageSize`、`testResultId`、`humanFeedback`。

**records 字段**：`id`、`testResultId`、`caseName`、`modelName`、`durationMs`、`humanFeedback`、`createdAt`。

### 7.3 查询诊断详情

`GET /api/diagnoses/{id}`

响应 data 完整诊断对象：`inputSummary`、`candidateCauses`、`rawModelOutput`、`modelName`、`durationMs`、`inputTokens`、`outputTokens`、`humanFeedback`、`humanComment`、`createdAt`。

### 7.4 提交人工反馈

`PUT /api/diagnoses/{id}/feedback`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| feedback | string | 是 | `CONFIRMED` 确认 / `REJECTED` 否定 / `REVISED` 修正 |
| comment | string | 否 | 说明，≤1000 字符 |

反馈数据用于阶段 6 的诊断准确率统计与 prompt 优化参考。

### 7.5 AI 增强用例生成

`POST /api/cases/ai-generate`

让模型基于接口约束与业务上下文补充复杂业务场景的候选用例，入库时 `generationType = AI`。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| apiId | long | 是 | 目标接口定义 |
| extraContext | string | 否 | 业务背景，如"该接口用于订单取消，同一订单仅可取消一次" |
| count | int | 否 | 期望生成条数，1~10，默认 3 |

**响应 data**

```json
{
  "generatedCount": 3,
  "cases": [
    { "id": 20, "name": "取消已取消的订单", "generationType": "AI", "generationNote": "AI 建议：重复取消场景", "reviewed": false }
  ]
}
```

> AI 生成的用例同样经过结构校验后才入库；建议前端在列表中对 `generationType=AI` 的用例做来源标记，由用户编辑确认后再纳入计划。

---

## 8. 报告与实验评估

对应阶段 6。所有统计必须来自真实执行数据，指标定义见 8.3。

### 8.1 运行报告

`GET /api/reports/runs/{id}`

**响应 data**

```json
{
  "runId": 100,
  "planName": "用户模块回归",
  "status": "FAILED",
  "totalCount": 20,
  "successCount": 15,
  "failedCount": 4,
  "errorCount": 1,
  "passRate": 0.75,
  "durationMs": 12400,
  "responseTime": { "minMs": 41, "avgMs": 210, "maxMs": 5012 },
  "durationDistribution": [
    { "range": "0-100ms", "count": 8 },
    { "range": "100-500ms", "count": 7 },
    { "range": "500-1000ms", "count": 3 },
    { "range": ">1000ms", "count": 2 }
  ],
  "generationTypeBreakdown": [
    { "generationType": "RULE", "total": 14, "passed": 11 },
    { "generationType": "MANUAL", "total": 4, "passed": 3 },
    { "generationType": "AI", "total": 2, "passed": 1 }
  ]
}
```

- `passRate` = successCount / totalCount（ERROR 计入分母，不计入通过）。
- `durationDistribution` 分桶区间固定，便于跨运行对比。

### 8.2 平台概览

`GET /api/reports/overview`

仪表盘数据：`{ "apiCount": 15, "caseCount": 88, "planCount": 4, "runCount": 26, "latestRun": { ...运行摘要 } }`

### 8.3 对比实验

用于"纯规则 vs AI 增强"的固定测试集对比（阶段 6 验收）。

**创建实验** `POST /api/experiments`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 实验名称 |
| apiIds | array | 是 | 固定测试集：参与实验的接口定义 ID 列表 |

行为：对测试集分别执行"纯规则生成"与"AI 增强生成"两条流水线，各自生成用例、执行并记录指标。异步执行，返回 `experimentId`。

**查询实验报告** `GET /api/experiments/{id}`

```json
{
  "id": 1,
  "name": "订单模块规则 vs AI",
  "status": "SUCCESS",
  "testSetSize": 10,
  "rule": {
    "caseCount": 42, "validCaseCount": 40, "scenarioCoverage": 0.62,
    "durationMs": 18200, "aiCost": 0
  },
  "ai": {
    "caseCount": 55, "validCaseCount": 46, "scenarioCoverage": 0.81,
    "durationMs": 95400, "aiCost": 0.38
  }
}
```

**指标定义（分母与统计方法随阶段 6 落地时固化，此处为初稿）**

| 指标 | 定义 |
| --- | --- |
| 用例有效率 | 有效用例数 / 生成用例总数；有效 = 用例参数合法且断言可判定 |
| 场景覆盖率 | 生成的场景类型数 / 预定义场景类型总数（按接口参数维度统计） |
| 诊断准确率 | 人工反馈 CONFIRMED 数 / 有反馈的诊断总数 |
| 模型调用成本 | Σ(输入 tokens × 单价 + 输出 tokens × 单价) |
| 执行耗时 | 从任务开始到结束的墙钟时间 |

**分页查询实验** `GET /api/experiments` —— `page`、`pageSize`。

---

## 附录 A：核心 JSON 结构设计

> 以下结构是接口定义、用例与结果的核心承载，直接决定阶段 2 生成引擎与阶段 3 断言引擎的输入形态。
> 初稿在此定义，实现阶段可根据实际需要修订，修订需同步更新本附录。

### A.1 requestSchema（请求定义）

`api_definition.request_schema` 的 JSON 结构：

```json
{
  "contentType": "application/json",
  "params": [
    {
      "name": "id",
      "in": "path",
      "type": "integer",
      "required": true,
      "description": "用户 ID",
      "constraints": { "minimum": 1, "maximum": 99999 }
    },
    {
      "name": "keyword",
      "in": "query",
      "type": "string",
      "required": false,
      "constraints": { "minLength": 1, "maxLength": 50, "pattern": "^[a-zA-Z0-9]+$" }
    },
    {
      "name": "X-Token",
      "in": "header",
      "type": "string",
      "required": true
    },
    {
      "name": "body",
      "in": "body",
      "type": "object",
      "required": true,
      "schema": { "type": "object", "properties": { "name": { "type": "string", "constraints": { "maxLength": 20 } }, "age": { "type": "integer", "constraints": { "minimum": 0, "maximum": 150 } } }, "required": ["name"] }
    }
  ]
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| in | 参数位置：`path` / `query` / `header` / `body` |
| type | `integer` / `number` / `string` / `boolean` / `array` / `object`（对齐 OpenAPI 类型） |
| required | 是否必填，驱动 RequiredFieldStrategy |
| constraints | 约束集合：`minimum` / `maximum` / `minLength` / `maxLength` / `pattern` / `enum`，驱动 BoundaryValueStrategy |
| schema | 仅 body 参数使用，嵌套的简化 JSON Schema（`$ref` 已展开） |

### A.2 responseSchema（响应定义）

```json
{
  "successStatusCode": 200,
  "schema": {
    "type": "object",
    "properties": {
      "code": { "type": "integer" },
      "data": { "type": "object", "properties": { "id": { "type": "integer" }, "name": { "type": "string" } } }
    }
  }
}
```

解析时保留 OpenAPI 的响应 schema 树（展开 `$ref`、去掉循环引用）；`successStatusCode` 用于生成默认状态码断言。

### A.3 requestParams（用例请求参数值）

`test_case.request_params` 的 JSON 结构，按参数位置分组存具体值：

```json
{
  "path": { "id": 1 },
  "query": { "keyword": "abc" },
  "headers": { "X-Token": "test-token" },
  "body": { "name": "张三", "age": 20 }
}
```

规则：某位置无参数时对应键可省略；必填缺失类用例通过"不携带该参数"表达。

### A.4 assertionRules（断言规则）

`test_case.assertion_rules` 的 JSON 结构，数组，逐条独立判定：

```json
[
  { "type": "STATUS_CODE", "expected": 200 },
  { "type": "JSON_FIELD", "path": "$.code", "operator": "EQUALS", "expected": 0 },
  { "type": "RESPONSE_TIME", "operator": "LESS_THAN", "expectedMs": 1000 }
]
```

| type | 字段 | operator 可选值 |
| --- | --- | --- |
| STATUS_CODE | expected(int) | —— |
| JSON_FIELD | path(JSONPath)、expected、operator | `EQUALS` / `NOT_EQUALS` / `EXISTS` / `NOT_EXISTS` / `CONTAINS` / `GREATER_THAN` / `LESS_THAN` |
| RESPONSE_TIME | expectedMs(long) | `LESS_THAN` |

**汇总规则**：全部规则通过 → PASSED；任一不通过 → FAILED；请求未成功执行（无法取得响应）→ ERROR，断言均不判定。

### A.5 executionConfig（执行配置）

`test_plan.execution_config` 的 JSON 结构：

```json
{
  "concurrency": 5,
  "timeoutMs": 5000,
  "baseUrl": "http://localhost:9090",
  "stopOnFirstFailure": false
}
```

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| concurrency | 5 | 并发用例数，1~20，受全局有界线程池上限约束 |
| timeoutMs | 5000 | 单请求超时 |
| baseUrl | 空 | 覆盖接口定义中的 baseUrl（环境切换） |
| stopOnFirstFailure | false | 首条失败后是否停止后续调度 |

### A.6 执行快照结构

`test_result.request_snapshot`：

```json
{ "method": "GET", "url": "http://localhost:9090/users/1", "headers": { "Accept": "application/json" }, "body": null, "timeoutMs": 5000 }
```

`test_result.response_snapshot`：

```json
{ "statusCode": 200, "headers": { "Content-Type": "application/json" }, "body": { }, "bodyTruncated": false, "elapsedMs": 87 }
```

`test_result.assertion_results`：断言规则 + 实际取值的逐条回填：

```json
[ { "type": "STATUS_CODE", "expected": 200, "actual": 200, "passed": true } ]
```

### A.7 AI 诊断输出结构

模型被要求输出的结构化 JSON（`ai_diagnosis.candidate_causes`），入库前必须通过校验：

```json
{
  "conclusion": "一句话结论",
  "causes": [
    { "cause": "候选原因描述", "confidence": 0.85, "evidence": ["证据1", "证据2"] }
  ],
  "suggestions": ["可执行的排查/修复建议"]
}
```

校验规则：`conclusion` 非空；`causes` 为 1~5 条；每条 `confidence ∈ [0,1]`；`evidence` 至少一条。校验失败保存 `raw_model_output` 并返回错误，不落 `candidate_causes`。

---

## 附录 B：开放设计点

> 编写接口文档过程中发现的、需要确认后再实现的设计点。

### B.1 base_url 的归属（待确认）

**问题**：执行引擎需要知道被测服务的根地址，但 V1 基线表（`api_definition`、`test_case`）中没有该字段。

**建议方案**（两级覆盖）：
1. `api_definition` 新增 `base_url` 列——导入 OpenAPI 文档时取自 `servers[0].url`，手动创建时填写；
2. `test_plan.execution_config.baseUrl` 可覆盖（如临时切到联调环境）；单用例执行接口也支持请求级覆盖。

**落地方式**：V1 迁移已执行，按 Flyway 规则不能修改 V1 文件，需新增 `V2__add_base_url.sql` 迁移。在阶段 1 实现接口定义管理时一并完成。

### B.2 运行状态机与指标口径

6.1 的状态机与 8.3 的指标定义均为初稿，阶段 4 / 阶段 6 实现时由设计者（你）确认后固化，本附录届时移除。

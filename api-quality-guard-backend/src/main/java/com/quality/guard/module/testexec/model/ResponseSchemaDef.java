package com.quality.guard.module.testexec.model;

import lombok.Data;

import java.util.Map;

/**
 * 接口的响应定义，对应 api_definition.response_schema 列（JSON）
 */
@Data
public class ResponseSchemaDef {

    /** 成功状态码：取自文档中第一个 2xx 响应 */
    private Integer successStatusCode;

    /** 响应结构：简化后的 JSON Schema 树（type/properties/items/required 等） */
    private Map<String, Object> schema;
}

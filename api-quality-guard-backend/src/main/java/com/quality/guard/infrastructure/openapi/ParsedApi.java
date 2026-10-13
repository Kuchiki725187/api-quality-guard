package com.quality.guard.infrastructure.openapi;

import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import lombok.Data;

/**
 * 从 OpenAPI / Swagger 文档中解析出的单个接口（解析器输出，未落库）
 *
 */
@Data
public class ParsedApi {

    private String name;

    private String httpMethod;

    private String path;

    private String description;

    private String baseUrl;

    private RequestSchemaDef requestSchema;

    private ResponseSchemaDef responseSchema;
}

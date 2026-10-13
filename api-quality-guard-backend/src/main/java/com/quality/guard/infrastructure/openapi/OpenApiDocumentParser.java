package com.quality.guard.infrastructure.openapi;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.model.ParamDef;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI / Swagger 文档解析器（infrastructure 层，外部技术适配）
 * 支持 OpenAPI 3.x 与 Swagger 2.0（2.0 先转换为 3.0 模型后统一处理）
 * 输出为本系统的领域模型（RequestSchemaDef / ResponseSchemaDef），供业务层落库
 */
@Slf4j
@Component
public class OpenApiDocumentParser {

    /** schema 递归展开的最大深度，防止异常文档导致无限递归 */
    private static final int MAX_SCHEMA_DEPTH = 8;

    public ParsedApiDocument parse(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        //读取文档中的接口
        OpenAPI openAPI = readOpenApi(text);
       //为文档解析结果设置标题和版本
        ParsedApiDocument document = new ParsedApiDocument();
        document.setDocumentTitle(openAPI.getInfo() != null ? openAPI.getInfo().getTitle() : null);
        document.setDocumentVersion(openAPI.getInfo() != null ? openAPI.getInfo().getVersion() : null);
          //从接口中解析url
        String baseUrl = resolveBaseUrl(openAPI);
         //循环遍历文档中的接口并加入到文档解析结果中
        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();
                if (pathItem == null || pathItem.readOperationsMap() == null) {
                    continue;
                }
                for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                    document.getApis().add(convertOperation(path, opEntry.getKey().name(), opEntry.getValue(), baseUrl));
                }
            }
        }

        if (document.getApis().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文档中未解析到任何接口（paths 为空）");
        }
        return document;
    }
    //读取文档中的接口
    private OpenAPI readOpenApi(String text) {
        ParseOptions options = new ParseOptions();
        // 解析并展开 $ref 引用，简化后续处理；循环引用由解析器保留 $ref 处理
        options.setResolve(true);
        options.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(text, null, options);
        if (result.getOpenAPI() != null) {
            return result.getOpenAPI();
        }

        // 尝试按 Swagger 2.0 解析并转换为 OpenAPI 3.0 模型
        try {
            result = new SwaggerConverter().readContents(text, null, options);
            if (result.getOpenAPI() != null) {
                return result.getOpenAPI();
            }
        } catch (Exception e) {
            log.debug("Swagger 2.0 转换失败: {}", e.getMessage());
        }

        String detail = result.getMessages() == null || result.getMessages().isEmpty()
                ? "无法识别的文档格式（仅支持 OpenAPI 3.x / Swagger 2.0 的 JSON 或 YAML）"
                : String.join("; ", result.getMessages());
        throw new BusinessException(ResultCode.BAD_REQUEST, "文档解析失败：" + detail);
    }

    private String resolveBaseUrl(OpenAPI openAPI) {
        if (openAPI.getServers() == null || openAPI.getServers().isEmpty()) {
            return null;
        }
        return openAPI.getServers().get(0).getUrl();
    }
     //为文档中解析出的接口设置各项信息
    private ParsedApi convertOperation(String path, String httpMethod, Operation operation, String baseUrl) {
        ParsedApi api = new ParsedApi();
        api.setPath(path);
        api.setHttpMethod(httpMethod);
        api.setBaseUrl(baseUrl);
        api.setName(firstNonBlank(operation.getOperationId(), operation.getSummary(), httpMethod + " " + path));
        api.setDescription(firstNonBlank(operation.getDescription(), operation.getSummary()));
        api.setRequestSchema(convertRequest(operation));
        api.setResponseSchema(convertResponse(operation));
        return api;
    }
     //接口中的请求处理
    private RequestSchemaDef convertRequest(Operation operation) {
        RequestSchemaDef requestSchema = new RequestSchemaDef();
        List<ParamDef> params = new ArrayList<>();

        if (operation.getParameters() != null) {
            //遍历接口中的每个请求参数并加入参数表
            for (Parameter parameter : operation.getParameters()) {
                // body 参数统一由 requestBody 处理
                if ("body".equalsIgnoreCase(parameter.getIn())) {
                    continue;
                }
                ParamDef def = new ParamDef();
                def.setName(parameter.getName());
                def.setIn(parameter.getIn());
                def.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
                def.setDescription(parameter.getDescription());
                Schema<?> schema = parameter.getSchema();
                if (schema != null) {
                    def.setType(schema.getType());
                    def.setConstraints(extractConstraints(schema));
                }
                params.add(def);
            }
        }

        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null
                && requestBody.getContent().containsKey("application/json")) {
            MediaType mediaType = requestBody.getContent().get("application/json");
            ParamDef bodyDef = new ParamDef();
            bodyDef.setName("body");
            bodyDef.setIn("body");
            bodyDef.setType("object");
            bodyDef.setRequired(Boolean.TRUE.equals(requestBody.getRequired()));
            bodyDef.setDescription(requestBody.getDescription());
            bodyDef.setSchema(schemaToMap(mediaType.getSchema(), 0));
            params.add(bodyDef);
            requestSchema.setContentType("application/json");
        }

        requestSchema.setParams(params);
        return requestSchema;
    }
    //对接口响应处理
    private ResponseSchemaDef convertResponse(Operation operation) {
        ResponseSchemaDef responseSchema = new ResponseSchemaDef();
        responseSchema.setSuccessStatusCode(200);
        if (operation.getResponses() == null) {
            return responseSchema;
        }
        for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
            String code = entry.getKey();
            if (code.startsWith("2")) {
                try {
                    responseSchema.setSuccessStatusCode(Integer.parseInt(code));
                } catch (NumberFormatException ignored) {
                    // "2XX" 之类的通配响应码，保持默认 200
                }
                ApiResponse response = entry.getValue();
                if (response != null && response.getContent() != null
                        && response.getContent().containsKey("application/json")) {
                    responseSchema.setSchema(
                            schemaToMap(response.getContent().get("application/json").getSchema(), 0));
                }
                break;
            }
        }
        return responseSchema;
    }

    /** 提取参数约束：阶段 2 BoundaryValueStrategy 的输入 */
    private Map<String, Object> extractConstraints(Schema<?> schema) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        if (schema.getMinimum() != null) {
            constraints.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            constraints.put("maximum", schema.getMaximum());
        }
        if (schema.getMinLength() != null) {
            constraints.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            constraints.put("maxLength", schema.getMaxLength());
        }
        if (schema.getPattern() != null) {
            constraints.put("pattern", schema.getPattern());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            constraints.put("enum", schema.getEnum());
        }
        return constraints.isEmpty() ? null : constraints;
    }

    /** 将 swagger Schema 树递归转换为简化 Map（保留生成引擎需要的核心字段） */
    private Map<String, Object> schemaToMap(Schema<?> schema, int depth) {
        if (schema == null || depth > MAX_SCHEMA_DEPTH) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (schema.get$ref() != null) {
            // resolveFully 一般已展开；此处兜底处理循环引用保留的 $ref
            map.put("$ref", schema.get$ref());
            return map;
        }
        if (schema.getType() != null) {
            map.put("type", schema.getType());
        }
        if (schema.getFormat() != null) {
            map.put("format", schema.getFormat());
        }
        if (schema.getDescription() != null) {
            map.put("description", schema.getDescription());
        }
        if (schema.getExample() != null) {
            map.put("example", schema.getExample());
        }
        Map<String, Object> constraints = extractConstraints(schema);
        if (constraints != null) {
            map.put("constraints", constraints);
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            schema.getProperties().forEach((key, value) -> properties.put(key, schemaToMap(value, depth + 1)));
            map.put("properties", properties);
        }
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            map.put("required", schema.getRequired());
        }
        if (schema.getItems() != null) {
            map.put("items", schemaToMap(schema.getItems(), depth + 1));
        }
        return map;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}

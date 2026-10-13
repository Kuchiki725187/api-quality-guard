package com.quality.guard.module.testexec.definition.dto;

import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiDefinitionCreateDTO {

    @NotBlank(message = "接口名称不能为空")
    @Size(max = 255, message = "接口名称不能超过 255 字符")
    private String name;

    @NotBlank(message = "HTTP 方法不能为空")
    @Pattern(regexp = "(?i)GET|POST|PUT|DELETE|PATCH", message = "HTTP 方法仅支持 GET/POST/PUT/DELETE/PATCH")
    private String httpMethod;

    @NotBlank(message = "接口路径不能为空")
    @Size(max = 500, message = "接口路径不能超过 500 字符")
    @Pattern(regexp = "^/.*", message = "接口路径需以 / 开头")
    private String path;

    @Size(max = 1000, message = "描述不能超过 1000 字符")
    private String description;

    @Size(max = 500, message = "baseUrl 不能超过 500 字符")
    @Pattern(regexp = "^https?://.*", message = "baseUrl 需以 http:// 或 https:// 开头")
    private String baseUrl;

    private RequestSchemaDef requestSchema;

    private ResponseSchemaDef responseSchema;
}

package com.quality.guard.module.testexec.definition.vo;

import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiDefinitionVO {

    private Long id;

    private String name;

    private String httpMethod;

    private String path;

    private String description;

    private String baseUrl;

    private RequestSchemaDef requestSchema;

    private ResponseSchemaDef responseSchema;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

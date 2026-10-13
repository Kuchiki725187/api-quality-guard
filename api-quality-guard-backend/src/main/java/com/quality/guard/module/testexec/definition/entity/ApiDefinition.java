package com.quality.guard.module.testexec.definition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "api_definition", autoResultMap = true)
public class ApiDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String httpMethod;

    private String path;

    private String description;

    private String baseUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private RequestSchemaDef requestSchema;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private ResponseSchemaDef responseSchema;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

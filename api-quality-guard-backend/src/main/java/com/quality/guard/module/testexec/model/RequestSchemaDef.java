package com.quality.guard.module.testexec.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口的请求定义，对应 api_definition.request_schema 列（JSON）
 */
@Data
public class RequestSchemaDef {

    /** body 请求的内容类型，如 application/json；无 body 时为 null */
    private String contentType;

    /** 全部请求参数（path / query / header / body） */
    private List<ParamDef> params = new ArrayList<>();
}

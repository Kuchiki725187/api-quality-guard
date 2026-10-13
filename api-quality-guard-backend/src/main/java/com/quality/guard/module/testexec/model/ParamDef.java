package com.quality.guard.module.testexec.model;

import lombok.Data;
import java.util.Map;

/**
 * 单个请求参数的定义（requestSchema.params 的元素）
 * constraints 是阶段 2 生成引擎的核心输入：边界值策略读取 minimum/maxLength 等约束
 */
@Data
public class ParamDef {

    /** 参数名 */
    private String name;

    /** 参数位置：path / query / header / body */
    private String in;

    /** 数据类型：integer / number / string / boolean / array / object */
    private String type;

    /** 是否必填，驱动 RequiredFieldStrategy */
    private boolean required;

    /** 描述 */
    private String description;

    /** 约束集合：minimum / maximum / minLength / maxLength / pattern / enum */
    private Map<String, Object> constraints;

    /** 仅 body 参数使用：嵌套的简化 JSON Schema 结构（$ref 已展开） */
    private Map<String, Object> schema;
}

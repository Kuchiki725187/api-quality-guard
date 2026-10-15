package com.quality.guard.module.testexec.model;

import lombok.Data;

import java.util.Map;

/**
 * 测试用例的具体请求参数值，对应 test_case.request_params 列（JSON）
 * 按参数位置分组
 *
 */
@Data
public class RequestParams {

    /** path 参数值 */
    private Map<String, Object> path;

    /** query 参数值 */
    private Map<String, Object> query;

    /** header 参数值 */
    private Map<String, Object> headers;

    /** body 参数值（JSON 对象；无 body 时为 null） */
    private Object body;
}
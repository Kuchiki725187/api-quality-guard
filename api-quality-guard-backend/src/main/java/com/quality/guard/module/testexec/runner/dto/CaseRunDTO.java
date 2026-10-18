package com.quality.guard.module.testexec.runner.dto;

import lombok.Data;

/**
 * 单用例执行请求：可临时覆盖被测服务地址（切换到联调环境等），为空则用接口定义中的 baseUrl
 */
@Data
public class CaseRunDTO {

    private String baseUrl;
}

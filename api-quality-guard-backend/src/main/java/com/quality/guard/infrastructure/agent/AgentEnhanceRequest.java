package com.quality.guard.infrastructure.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送给 Agent 的用例增强请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEnhanceRequest {

    private Long apiId;

    private String apiName;

    private String apiMethod;

    private String apiPath;

    private String apiDescription;

    private Object apiRequestSchema;

    private Object apiResponseSchema;

    /** 业务背景（如"同一订单仅可取消一次"） */
    private String extraContext;

    /** 期望生成条数 1~10 */
    private int count;
}

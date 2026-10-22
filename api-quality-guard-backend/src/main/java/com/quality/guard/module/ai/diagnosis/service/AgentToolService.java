package com.quality.guard.module.ai.diagnosis.service;

import java.util.List;
import java.util.Map;

/**
 * Agent 工具接口的数据访问：只读、返回瘦身后的摘要（供 AI 诊断 Agent 工具循环使用）。
 */
public interface AgentToolService {

    /** 接口定义摘要 */
    Map<String, Object> apiDetail(Long apiId);

    /** 某用例最近的执行结果摘要（按状态/耗时/失败断言） */
    List<Map<String, Object>> caseHistory(Long caseId, int limit);
}

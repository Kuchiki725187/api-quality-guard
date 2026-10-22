package com.quality.guard.module.ai.diagnosis.controller;

import com.quality.guard.module.ai.diagnosis.service.AgentToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 诊断 Agent 的工具回调接口（内部只读接口，供 Agent 工具循环获取更多证据）
 * 直接返回业务 JSON（不包统一 Result），便于 Agent 原样作为工具结果使用
 */
@RestController
@RequestMapping("/api/agent-tools")
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolService agentToolService;

    @GetMapping("/api-detail/{apiId}")
    public Map<String, Object> apiDetail(@PathVariable Long apiId) {
        return agentToolService.apiDetail(apiId);
    }

    @GetMapping("/case-history/{caseId}")
    public List<Map<String, Object>> caseHistory(@PathVariable Long caseId,
                                                 @RequestParam(defaultValue = "5") int limit) {
        return agentToolService.caseHistory(caseId, limit);
    }
}

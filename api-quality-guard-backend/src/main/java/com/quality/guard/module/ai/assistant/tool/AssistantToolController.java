package com.quality.guard.module.ai.assistant.tool;

import com.quality.guard.module.ai.report.vo.RunReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 助手只读工具回调，供 Python Agent 查询平台数据
 * 路径位于 /api/agent-tools/**，由 InternalTokenInterceptor 校验 X-Internal-Token
 */
@RestController
@RequestMapping("/api/agent-tools")
@RequiredArgsConstructor
public class AssistantToolController {

    private final AssistantToolService assistantToolService;

    @GetMapping("/platform-report")
    public Map<String, Object> platformReport() {
        return assistantToolService.platformReport();
    }

    @GetMapping("/apis")
    public List<Map<String, Object>> apis(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        return assistantToolService.searchApis(keyword, limit);
    }

    @GetMapping("/runs/{id}")
    public RunReportVO runReport(@PathVariable Long id) {
        return assistantToolService.runReport(id);
    }

    @GetMapping("/failures")
    public List<Map<String, Object>> failures(@RequestParam(defaultValue = "10") int limit) {
        return assistantToolService.recentFailures(limit);
    }
}

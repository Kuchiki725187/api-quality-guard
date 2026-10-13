package com.quality.guard.module.testexec.definition.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档导入结果统计
 */
@Data
public class ApiImportResultVO {

    private String documentTitle;

    private String documentVersion;

    private int totalParsed;

    /** 新增入库的接口数（方法 + 路径在库中不存在） */
    private int imported;

    /** 更新已有定义的接口数（按方法 + 路径去重命中，保留原 ID） */
    private int updated;

    /** 跳过导入的接口数量 */
    private int skipped;

    /** 按 HTTP 的方法统计解析出的接口数，如 {"GET": 18, "POST": 15} */
    private Map<String, Integer> methodCounts = new LinkedHashMap<>();

    private List<Failure> failures = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class Failure {

        private String path;

        private String reason;
    }
}

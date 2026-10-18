package com.quality.guard.module.testexec.model;

import lombok.Builder;
import lombok.Data;

/**
 * 单条断言的判定明细（test_result.assertion_results JSON 数组的元素）
 */
@Data
@Builder
public class AssertionOutcome {

    /** 断言类型：STATUS_CODE / JSON_FIELD / RESPONSE_TIME */
    private String type;

    /** JSON_FIELD 断言路径（如 $.code）；其他类型为 null */
    private String path;

    /** JSON_FIELD 断言操作符（如 EQUALS）；其他类型为 null */
    private String operator;

    /** 期望值（RESPONSE_TIME 时存毫秒数） */
    private Object expected;

    /** 实际取值；无法取得时存说明文本 */
    private Object actual;

    private boolean passed;

    /** 补充说明（如"响应体非 JSON，无法求值"），可为空 */
    private String message;
}

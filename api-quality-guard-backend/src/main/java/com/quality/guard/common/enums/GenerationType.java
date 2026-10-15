package com.quality.guard.common.enums;

/**
 * 用例生成来源，对应 test_case.generation_type 列。
 */
public enum GenerationType {

    /** 手动创建/编辑 */
    MANUAL,

    /** 规则引擎生成 */
    RULE,

    /** AI 增强生成（阶段 M4） */
    AI
}

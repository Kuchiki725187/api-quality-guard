package com.quality.guard.module.testexec.casegen.model;

import lombok.Getter;

/**
 * 用例生成策略类型，对应生成接口请求参数 strategies 的取值
 */
@Getter
public enum StrategyType {

    NORMAL("正常值"),
    REQUIRED_MISSING("必填参数缺失"),
    TYPE_MISMATCH("非法类型"),
    BOUNDARY("边界值");

    private final String label;

    StrategyType(String label) {
        this.label = label;
    }
}

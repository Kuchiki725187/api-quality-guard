package com.quality.guard.module.testexec.casegen.engine;

import com.quality.guard.module.testexec.casegen.model.GeneratedCase;
import com.quality.guard.module.testexec.casegen.model.StrategyType;
import com.quality.guard.module.testexec.casegen.strategy.GenerationStrategy;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用例生成引擎（编排器）
 * 根据启用的策略对接口定义批量生成候选用例
 * 扩展机制：strategies 通过构造器注入 Spring 容器中所有 GenerationStrategy 实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestCaseGenerator {

    private final List<GenerationStrategy> strategies;

    /**
     * 对接口定义执行生成
     *
     * @param apiDefinition 目标接口定义（需包含 requestSchema）
     * @param types         启用的策略集合；为空或 null 表示执行全部策略
     * @return 全部候选用例（按 StrategyType 枚举声明顺序执行，输出稳定可复现）
     */
    public List<GeneratedCase> generate(ApiDefinition apiDefinition, Set<StrategyType> types) {
        List<GeneratedCase> results = new ArrayList<>();
        for (StrategyType type : StrategyType.values()) {
            if (types != null && !types.isEmpty() && !types.contains(type)) {
                continue;
            }
            Optional<GenerationStrategy> strategy = findStrategy(type);
            if (strategy.isEmpty()) {
                log.warn("策略未注册，跳过: {}", type);
                continue;
            }
            collect(strategy.get(), apiDefinition, results);
        }
        return results;
    }

    private Optional<GenerationStrategy> findStrategy(StrategyType type) {
        return strategies.stream().filter(s -> s.getType() == type).findFirst();
    }

    /** 单个策略失败不影响其余策略（开发期未实现的策略会被跳过并告警） */
    private void collect(GenerationStrategy strategy, ApiDefinition apiDefinition, List<GeneratedCase> results) {
        try {
            List<GeneratedCase> generated = strategy.generate(apiDefinition);
            if (generated != null) {
                results.addAll(generated);
            }
        } catch (Exception e) {
            log.warn("策略 {} 生成失败，已跳过: {}", strategy.getType(), e.getMessage());
        }
    }
}

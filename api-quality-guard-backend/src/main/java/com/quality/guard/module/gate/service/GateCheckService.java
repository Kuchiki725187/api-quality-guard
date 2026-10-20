package com.quality.guard.module.gate.service;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.gate.dto.GateCheckDTO;
import com.quality.guard.module.gate.entity.QualityGateRecord;
import com.quality.guard.module.gate.mapper.QualityGateRecordMapper;
import com.quality.guard.module.gate.model.GateViolation;
import com.quality.guard.module.gate.vo.GateResultVO;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量门禁判定
 * 对一次已正常结束（SUCCESS/FAILED）的运行按阈值判定"放行/阻断"，并把判定上下文留档
 * 默认规则：质量总分 ≥ 80、执行异常数 = 0、失败用例数 ≤ 0
 * 阈值优先级：请求参数覆盖 > 计划 executionConfig.gate > 默认值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GateCheckService {

    private static final int DEFAULT_SCORE_THRESHOLD = 80;
    private static final int DEFAULT_MAX_FAILED = 0;
    private static final int DEFAULT_MAX_ERROR = 0;

    private final TestRunMapper testRunMapper;
    private final TestPlanMapper testPlanMapper;
    private final QualityGateRecordMapper gateRecordMapper;

    public GateResultVO check(GateCheckDTO dto) {
        //先查询执行记录并判断任务执行的状态
        TestRun run = testRunMapper.selectById(dto.getRunId());
        if (run == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行任务不存在: " + dto.getRunId());
        }
        if (!"SUCCESS".equals(run.getStatus()) && !"FAILED".equals(run.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "任务未正常结束（当前状态: " + run.getStatus() + "），无法执行门禁检查");
        }
         //拿到解析后的配置
        Map<String, Object> thresholds = resolveThresholds(run, dto);
        int scoreThreshold = asInt(thresholds.get("scoreThreshold"), DEFAULT_SCORE_THRESHOLD);
        int maxFailed = asInt(thresholds.get("maxFailedCount"), DEFAULT_MAX_FAILED);
        int maxError = asInt(thresholds.get("maxErrorCount"), DEFAULT_MAX_ERROR);
        //对门禁违规进行记录
        List<GateViolation> violations = new ArrayList<>();
        //获取执行结果的评分
        BigDecimal score = run.getScore();
        if (score == null) {
            violations.add(new GateViolation("缺少评分", "该运行没有评分数据，无法判定质量总分"));
        } else if (score.compareTo(BigDecimal.valueOf(scoreThreshold)) < 0) {
            violations.add(new GateViolation("质量总分未达标",
                    "当前 " + score + "，要求 ≥ " + scoreThreshold));
        }
        int failed = run.getFailedCount() == null ? 0 : run.getFailedCount();
        if (failed > maxFailed) {
            violations.add(new GateViolation("失败用例数超限",
                    "当前 " + failed + "，要求 ≤ " + maxFailed));
        }
        int error = run.getErrorCount() == null ? 0 : run.getErrorCount();
        if (error > maxError) {
            violations.add(new GateViolation("执行异常数超限",
                    "当前 " + error + "，要求 ≤ " + maxError));
        }

        boolean passed = violations.isEmpty();
        //质量门禁记录入库
        QualityGateRecord record = new QualityGateRecord();
        record.setRunId(run.getId());
        record.setPassed(passed);
        record.setScore(score);
        record.setThresholdSnapshot(thresholds);
        record.setViolations(violations);
        gateRecordMapper.insert(record);

        log.info("门禁检查 runId={} passed={} score={} violations={}",
                run.getId(), passed, score, violations.size());
        //组装结果返回
        GateResultVO vo = new GateResultVO();
        vo.setRecordId(record.getId());
        vo.setRunId(run.getId());
        vo.setPassed(passed);
        vo.setScore(score);
        vo.setThresholds(thresholds);
        vo.setViolations(violations);
        return vo;
    }

    /** 阈值解析：本次请求配置>测试计划配置>系统默认配置*/
    private Map<String, Object> resolveThresholds(TestRun run, GateCheckDTO dto) {
        //初始化默认阈值
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("scoreThreshold", DEFAULT_SCORE_THRESHOLD);
        thresholds.put("maxFailedCount", DEFAULT_MAX_FAILED);
        thresholds.put("maxErrorCount", DEFAULT_MAX_ERROR);
        //读取测试计划中的门禁配置
        if (run.getPlanId() != null) {
            TestPlan plan = testPlanMapper.selectById(run.getPlanId());
            if (plan != null && plan.getExecutionConfig() != null
                    && plan.getExecutionConfig().get("gate") instanceof Map<?, ?> gateConfig) {
                //读取计划中的三个配置项，只覆盖存在且取值符合要求的值
                copyIfPresent(gateConfig, "scoreThreshold", thresholds);
                copyIfPresent(gateConfig, "maxFailedCount", thresholds);
                copyIfPresent(gateConfig, "maxErrorCount", thresholds);
            }
        }
        //如果本次请求有配置就覆盖计划配置
        if (dto.getScoreThreshold() != null) {
            thresholds.put("scoreThreshold", dto.getScoreThreshold());
        }
        if (dto.getMaxFailedCount() != null) {
            thresholds.put("maxFailedCount", dto.getMaxFailedCount());
        }
        if (dto.getMaxErrorCount() != null) {
            thresholds.put("maxErrorCount", dto.getMaxErrorCount());
        }
        return thresholds;
    }
    //从来源map中取出指定配置并将其复制到目标map中
    private void copyIfPresent(Map<?, ?> source, String key, Map<String, Object> target) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            target.put(key, number.intValue());
        }
    }

    private int asInt(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }
}

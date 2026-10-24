package com.quality.guard.module.ai.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quality.guard.module.ai.report.mapper.ReportRiskMapper;
import com.quality.guard.module.ai.report.model.ApiRiskRow;
import com.quality.guard.module.ai.report.vo.PlatformOverviewVO;
import com.quality.guard.module.ai.report.vo.PlatformQualityVO;
import com.quality.guard.module.scoring.calculator.ScoreCalculator;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import com.quality.guard.module.testexec.plan.entity.TestPlan;
import com.quality.guard.module.testexec.plan.mapper.TestPlanMapper;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 平台级报告
 * 概览与四维质量评分都直接读现有执行数据，不落新表
 * - 规范性/覆盖率是接口定义与用例的快照指标
 * - 性能/稳定性取近 14 天执行结果，口径与 run 评分一致
 * - 风险接口取每个接口最近一次 FAILED/ERROR 结果
 */
@Service
@RequiredArgsConstructor
public class PlatformReportService {

    private static final int TREND_DAYS = 14;
    private static final int RECENT_RUN_LIMIT = 5;
    private static final int RISK_LIMIT = 5;
    private static final double DIMENSION_WEIGHT = 0.25;
    private static final String STATUS_ERROR = "ERROR";

    private final ApiDefinitionMapper apiDefinitionMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestPlanMapper testPlanMapper;
    private final TestRunMapper testRunMapper;
    private final TestResultMapper testResultMapper;
    private final ReportRiskMapper reportRiskMapper;

    public PlatformOverviewVO overview() {
        //查询测试用例
        List<TestCase> cases = testCaseMapper.selectList(null);
        //从测试用例中得到接口集合
        Set<Long> coveredApiIds = cases.stream()
                .map(TestCase::getApiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        //统计接口数量
        int apiCount = count(apiDefinitionMapper);
        Long todayCount = testRunMapper.selectCount(
                new LambdaQueryWrapper<TestRun>().ge(TestRun::getCreatedAt, LocalDate.now().atStartOfDay()));
        int todayRunCount = todayCount == null ? 0 : todayCount.intValue();
        Long weekAdded = apiDefinitionMapper.selectCount(
                new LambdaQueryWrapper<ApiDefinition>()
                        .ge(ApiDefinition::getCreatedAt, LocalDateTime.now().minusDays(7)));
        int apiWeekAdded = weekAdded == null ? 0 : weekAdded.intValue();
        List<TestRun> scoredRuns = scoredRunsSince(trendStart());

        PlatformOverviewVO vo = new PlatformOverviewVO();
        vo.setApiCount(apiCount);
        vo.setCaseCount(cases.size());
        vo.setPlanCount(count(testPlanMapper));
        vo.setRunCount(count(testRunMapper));
        vo.setTodayRunCount(todayRunCount);
        vo.setCoveredApiCount(coveredApiIds.size());
        vo.setCoverage(apiCount == 0 ? 0.0 : percent(coveredApiIds.size(), apiCount));
        vo.setApiWeekAdded(apiWeekAdded);
        vo.setAvgScore(averageScore(scoredRuns));
        vo.setWeekDelta(weekDelta(scoredRuns));
        vo.setScoreTrend(scoreTrend(scoredRuns).stream()
                .map(point -> new PlatformOverviewVO.ScorePoint(point.date(), point.score()))
                .toList());

        List<TestRun> recentRuns = testRunMapper.selectList(new LambdaQueryWrapper<TestRun>()
                .orderByDesc(TestRun::getCreatedAt)
                .orderByDesc(TestRun::getId)
                .last("LIMIT " + RECENT_RUN_LIMIT));
        Map<Long, String> planNames = loadPlanNames(recentRuns);
        List<PlatformOverviewVO.RunSummary> summaries = recentRuns.stream()
                .map(run -> toSummary(run, run.getPlanId() == null ? null : planNames.get(run.getPlanId())))
                .toList();
        vo.setRecentRuns(summaries);
        vo.setLatestRun(summaries.isEmpty() ? null : summaries.get(0));
        vo.setRiskApis(riskApis());
        return vo;
    }

    public PlatformQualityVO quality() {
        List<ApiDefinition> apis = apiDefinitionMapper.selectList(null);
        List<TestCase> cases = testCaseMapper.selectList(null);
        List<TestResult> results = recentResults();

        int compliant = (int) apis.stream().filter(this::isSpecificationCompliant).count();
        Double specification = apis.isEmpty() ? null : percent(compliant, apis.size());
        String specificationDetail = apis.isEmpty()
                ? "暂无接口定义"
                : "合规 %d/%d 个（描述 + 参数类型 + 响应定义）".formatted(compliant, apis.size());

        Set<Long> coveredApiIds = cases.stream()
                .map(TestCase::getApiId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Double coverage = apis.isEmpty() ? null : percent(coveredApiIds.size(), apis.size());
        String coverageDetail = apis.isEmpty()
                ? "暂无接口定义"
                : "已覆盖 %d/%d 个接口".formatted(coveredApiIds.size(), apis.size());

        int perfTotal = 0;
        int perfPassed = 0;
        int errorCount = 0;
        for (TestResult result : results) {
            if (STATUS_ERROR.equals(result.getStatus())) {
                errorCount++;
            }
            List<AssertionOutcome> outcomes = result.getAssertionResults();
            if (outcomes == null || outcomes.isEmpty()) {
                continue;
            }
            List<AssertionOutcome> timeOutcomes = outcomes.stream()
                    .filter(outcome -> "RESPONSE_TIME".equals(outcome.getType()))
                    .toList();
            if (timeOutcomes.isEmpty()) {
                continue;
            }
            perfTotal++;
            if (timeOutcomes.stream().allMatch(AssertionOutcome::isPassed)) {
                perfPassed++;
            }
        }
        Double performance = perfTotal == 0 ? null : percent(perfPassed, perfTotal);
        String performanceDetail = perfTotal == 0
                ? "近 14 天无响应时间断言"
                : "含响应时间断言 %d 条，达标 %d 条".formatted(perfTotal, perfPassed);

        Double stability = results.isEmpty()
                ? null
                : round1((1.0 - (double) errorCount / results.size()) * 100.0);
        String stabilityDetail = results.isEmpty()
                ? "近 14 天无执行结果"
                : "近 14 天 %d 条结果，异常 %d 条".formatted(results.size(), errorCount);

        List<PlatformQualityVO.Dimension> dimensions = List.of(
                new PlatformQualityVO.Dimension(
                        "specification", "接口规范性", specification, DIMENSION_WEIGHT, specificationDetail),
                new PlatformQualityVO.Dimension(
                        "coverage", "测试覆盖率", coverage, DIMENSION_WEIGHT, coverageDetail),
                new PlatformQualityVO.Dimension(
                        "performance", "性能表现", performance, DIMENSION_WEIGHT, performanceDetail),
                new PlatformQualityVO.Dimension(
                        "stability", "稳定性", stability, DIMENSION_WEIGHT, stabilityDetail));

        double weighted = 0;
        double weightSum = 0;
        for (PlatformQualityVO.Dimension dimension : dimensions) {
            if (dimension.getScore() != null) {
                weighted += dimension.getScore() * dimension.getWeight();
                weightSum += dimension.getWeight();
            }
        }
        double total = weightSum == 0 ? 0.0 : round1(weighted / weightSum);

        List<TestRun> scoredRuns = scoredRunsSince(trendStart());
        PlatformQualityVO vo = new PlatformQualityVO();
        vo.setTotal(total);
        vo.setGrade(weightSum == 0 ? "暂无数据" : ScoreCalculator.gradeOf(total));
        vo.setWeekDelta(weekDelta(scoredRuns));
        vo.setDimensions(dimensions);
        vo.setScoreTrend(scoreTrend(scoredRuns).stream()
                .map(point -> new PlatformQualityVO.ScorePoint(point.date(), point.score()))
                .toList());
        return vo;
    }

    private boolean isSpecificationCompliant(ApiDefinition api) {
        if (!StringUtils.hasText(api.getDescription())) {
            return false;
        }
        ResponseSchemaDef responseSchema = api.getResponseSchema();
        if (responseSchema == null || responseSchema.getSuccessStatusCode() == null) {
            return false;
        }
        RequestSchemaDef requestSchema = api.getRequestSchema();
        return requestSchema == null
                || requestSchema.getParams() == null
                || requestSchema.getParams().stream().allMatch(param -> StringUtils.hasText(param.getType()));
    }

    private List<TestResult> recentResults() {
        return testResultMapper.selectList(new LambdaQueryWrapper<TestResult>()
                .ge(TestResult::getCreatedAt, trendStart()));
    }

    private List<TestRun> scoredRunsSince(LocalDateTime start) {
        return testRunMapper.selectList(new LambdaQueryWrapper<TestRun>()
                .in(TestRun::getStatus, "SUCCESS", "FAILED")
                .isNotNull(TestRun::getScore)
                .ge(TestRun::getFinishedAt, start));
    }

    private List<PlatformOverviewVO.RiskApi> riskApis() {
        List<ApiRiskRow> rows = reportRiskMapper.selectRiskApis(RISK_LIMIT);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> resultIds = rows.stream().map(ApiRiskRow::getResultId).toList();
        Map<Long, TestResult> results = testResultMapper.selectBatchIds(resultIds).stream()
                .collect(Collectors.toMap(TestResult::getId, Function.identity()));
        return rows.stream()
                .map(row -> {
                    TestResult result = results.get(row.getResultId());
                    return new PlatformOverviewVO.RiskApi(
                            row.getApiId(),
                            row.getName(),
                            row.getMethod(),
                            row.getPath(),
                            STATUS_ERROR.equals(row.getStatus()) ? "高" : "中",
                            result == null ? "最近一次执行失败" : describeFailure(result));
                })
                .toList();
    }

    private String describeFailure(TestResult result) {
        if (STATUS_ERROR.equals(result.getStatus())) {
            return truncate(StringUtils.hasText(result.getErrorMessage())
                    ? result.getErrorMessage() : "执行异常", 100);
        }
        if (result.getAssertionResults() != null) {
            for (AssertionOutcome outcome : result.getAssertionResults()) {
                if (outcome.isPassed()) {
                    continue;
                }
                if (StringUtils.hasText(outcome.getMessage())) {
                    return truncate(outcome.getMessage(), 100);
                }
                String detail = switch (outcome.getType()) {
                    case "STATUS_CODE" -> "HTTP 状态码 期望 " + outcome.getExpected()
                            + "，实际 " + outcome.getActual();
                    case "RESPONSE_TIME" -> "响应时间 期望 < " + outcome.getExpected()
                            + "ms，实际 " + outcome.getActual() + "ms";
                    case "JSON_FIELD" -> outcome.getPath() + " 期望 " + outcome.getOperator()
                            + " " + outcome.getExpected() + "，实际 " + outcome.getActual();
                    default -> outcome.getType() + " 断言失败";
                };
                return truncate(detail, 100);
            }
        }
        return "最近一次执行失败";
    }

    private String truncate(String text, int maxLength) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "…";
    }

    private PlatformOverviewVO.RunSummary toSummary(TestRun run, String planName) {
        return new PlatformOverviewVO.RunSummary(
                run.getId(),
                planName,
                run.getStatus(),
                run.getScore(),
                run.getCreatedAt(),
                run.getFinishedAt());
    }

    private Map<Long, String> loadPlanNames(List<TestRun> runs) {
        List<Long> planIds = runs.stream()
                .map(TestRun::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return testPlanMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(TestPlan::getId, TestPlan::getName));
    }

    private Double averageScore(List<TestRun> runs) {
        if (runs.isEmpty()) {
            return null;
        }
        return round1(runs.stream().mapToDouble(run -> run.getScore().doubleValue()).average().orElse(0));
    }
    //计算周之间的评分变化
    private Double weekDelta(List<TestRun> scoredRuns) {
        LocalDateTime thisWeekStart = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime lastWeekStart = LocalDate.now().minusDays(TREND_DAYS - 1).atStartOfDay();
        Double current = averageScore(scoredRuns.stream()
                .filter(run -> run.getFinishedAt() != null && !run.getFinishedAt().isBefore(thisWeekStart))
                .toList());
        Double previous = averageScore(scoredRuns.stream()
                .filter(run -> run.getFinishedAt() != null
                        && !run.getFinishedAt().isBefore(lastWeekStart)
                        && run.getFinishedAt().isBefore(thisWeekStart))
                .toList());
        return current == null || previous == null ? null : round1(current - previous);
    }
    //统计最近天数内每天的平均质量评分，并生成一组趋势数据
    private List<DayScore> scoreTrend(List<TestRun> scoredRuns) {
        //key为日期，value该日期下所有测试运行的评分
        Map<LocalDate, List<Double>> scoresByDay = scoredRuns.stream()
                .filter(run -> run.getFinishedAt() != null)//过滤掉没有完成时间的记录
                .collect(Collectors.groupingBy(
                        run -> run.getFinishedAt().toLocalDate(),//去掉时分秒按日期分组
                        //分组后提取每条记录的评分并保存到list
                        Collectors.mapping(run -> run.getScore().doubleValue(), Collectors.toList())));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate start = LocalDate.now().minusDays(TREND_DAYS - 1);
        //该list用于保存每个趋势图上的点相关信息
        List<DayScore> points = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            //每次都向后退一天
            LocalDate day = start.plusDays(i);
            //获取当天评分列表
            List<Double> sameDay = scoresByDay.get(day);
            //计算当天评分平均值
            Double average = sameDay == null || sameDay.isEmpty()
                    ? null
                    : round1(sameDay.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            points.add(new DayScore(day.format(formatter), average));
        }
        return points;
    }

    private LocalDateTime trendStart() {
        return LocalDate.now().minusDays(TREND_DAYS - 1).atStartOfDay();
    }
    //查询接口数量
    private int count(BaseMapper<?> mapper) {
        Long count = mapper.selectCount(null);
        return count == null ? 0 : count.intValue();
    }
    //计算最后的百分比
    private Double percent(int part, int total) {
        return round1(part * 100.0 / total);
    }
    //将double数值四舍五入保留一位小数
    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private record DayScore(String date, Double score) {
    }
}

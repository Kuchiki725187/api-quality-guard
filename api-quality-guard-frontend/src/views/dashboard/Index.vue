<script setup>
import { computed } from 'vue'
import ScoreCard from '@/components/ScoreCard.vue'
import QualityChart from '@/components/QualityChart.vue'
import RiskTable from '@/components/RiskTable.vue'
import { dashboardStats, recentRuns, riskApis, scoreTrend } from '@/mock/data'

const stats = dashboardStats

const trendOption = computed(() => ({
  grid: { left: 44, right: 20, top: 30, bottom: 30 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: scoreTrend.dates,
    axisLine: { lineStyle: { color: '#334155' } },
    axisLabel: { color: '#94a3b8' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    min: 70,
    max: 100,
    splitLine: { lineStyle: { color: '#1e293b' } },
    axisLabel: { color: '#94a3b8' },
  },
  series: [
    {
      name: '平均质量评分',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: scoreTrend.values,
      lineStyle: { color: '#3b82f6', width: 2.5 },
      itemStyle: { color: '#3b82f6' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(59, 130, 246, 0.35)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0.02)' },
          ],
        },
      },
    },
  ],
}))

const runStatusType = (s) =>
  s === '成功' ? 'success' : s === '失败' ? 'danger' : s === '运行中' ? 'primary' : 'warning'
</script>

<template>
  <div class="page">
    <div class="cards">
      <ScoreCard title="API 接口总数" :value="stats.apiTotal" suffix="个" icon="Collection" color="#3b82f6" trend="+12 本周" />
      <ScoreCard title="测试覆盖率" :value="stats.coverage" suffix="%" icon="PieChart" color="#f59e0b" trend="-5% 本周" :trend-up="false" />
      <ScoreCard title="平均质量评分" :value="stats.avgScore" suffix="分" icon="TrendCharts" color="#10b981" trend="+2.6 本周" />
      <ScoreCard title="今日测试次数" :value="stats.todayRuns" suffix="次" icon="Timer" color="#8b5cf6" trend="+18%" />
    </div>

    <div class="panel">
      <h3 class="panel-title">
        <span>质量趋势</span>
        <span class="sub">最近 14 天平均质量评分</span>
      </h3>
      <QualityChart :option="trendOption" height="300px" />
    </div>

    <div class="grid-2">
      <div class="panel">
        <h3 class="panel-title">
          <span>风险接口 TOP5</span>
          <router-link class="more" to="/quality/score">质量中心 →</router-link>
        </h3>
        <RiskTable :data="riskApis" />
      </div>

      <div class="panel">
        <h3 class="panel-title">
          <span>最近测试任务</span>
          <router-link class="more" to="/test/records">全部记录 →</router-link>
        </h3>
        <div class="run-list">
          <div v-for="r in recentRuns" :key="r.time" class="run-row">
            <div class="run-main">
              <span class="run-name">{{ r.name }}</span>
              <span class="run-time">{{ r.time }}</span>
            </div>
            <div class="run-side">
              <el-tag :type="runStatusType(r.status)" size="small" effect="dark">
                {{ r.status }}
              </el-tag>
              <span class="run-score" :class="{ muted: r.score == null }">
                {{ r.score ?? '—' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 1200px) {
  .cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

.sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.more {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-blue);
  text-decoration: none;
}

.run-list {
  display: flex;
  flex-direction: column;
}

.run-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 4px;
  border-bottom: 1px solid var(--aqg-border);
}

.run-row:last-child {
  border-bottom: none;
}

.run-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.run-name {
  font-size: 14px;
  font-weight: 600;
}

.run-time {
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.run-side {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.run-score {
  min-width: 36px;
  text-align: right;
  font-size: 15px;
  font-weight: 700;
  color: var(--aqg-green);
}

.run-score.muted {
  color: var(--aqg-text-muted);
}
</style>

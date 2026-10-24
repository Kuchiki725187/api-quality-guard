<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import ScoreCard from '@/components/ScoreCard.vue'
import QualityChart from '@/components/QualityChart.vue'
import RiskTable from '@/components/RiskTable.vue'

const router = useRouter()
const loading = ref(true)
const overview = ref(null)

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await http.get('/reports/overview')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)

const stats = computed(() => overview.value ?? {
  apiCount: 0,
  caseCount: 0,
  planCount: 0,
  runCount: 0,
  todayRunCount: 0,
  coveredApiCount: 0,
  coverage: 0,
  apiWeekAdded: 0,
  avgScore: null,
  weekDelta: null,
})

const scoreTrend = computed(() => overview.value?.scoreTrend ?? [])
const recentRuns = computed(() => overview.value?.recentRuns ?? [])
const riskApis = computed(() => overview.value?.riskApis ?? [])

const apiTrend = computed(() => (stats.value.apiWeekAdded ? `+${stats.value.apiWeekAdded} 本周` : ''))

const coverageTrend = computed(() =>
  stats.value.apiCount ? `${stats.value.coveredApiCount}/${stats.value.apiCount} 已覆盖` : '',
)

const weekDeltaText = computed(() => {
  const delta = stats.value.weekDelta
  if (delta === null || delta === undefined) return ''
  return `${delta >= 0 ? '+' : ''}${delta} 较上周`
})

const trendOption = computed(() => ({
  grid: { left: 44, right: 20, top: 30, bottom: 30 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: scoreTrend.value.map((point) => point.date),
    axisLine: { lineStyle: { color: '#334155' } },
    axisLabel: { color: '#94a3b8' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    splitLine: { lineStyle: { color: '#1e293b' } },
    axisLabel: { color: '#94a3b8' },
  },
  series: [
    {
      name: '运行平均分',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: scoreTrend.value.map((point) => point.score),
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
  ({ SUCCESS: 'success', FAILED: 'danger', ERROR: 'warning', RUNNING: 'primary', PENDING: 'info', CANCELLED: 'info' })[s] ?? 'info'

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 16) : '—')

const scoreColor = (score) => {
  if (score === null || score === undefined) return 'var(--aqg-text-muted)'
  if (score >= 90) return '#10b981'
  if (score >= 80) return '#3b82f6'
  if (score >= 60) return '#f59e0b'
  return '#ef4444'
}

const scoreText = (score) => (score === null || score === undefined ? '—' : Number(score).toFixed(1))

function openRun(run) {
  router.push(`/test/report/${run.runId}`)
}
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="cards">
      <ScoreCard
        title="API 接口总数"
        :value="stats.apiCount"
        suffix="个"
        icon="Collection"
        color="#3b82f6"
        :trend="apiTrend"
      />
      <ScoreCard
        title="测试覆盖率"
        :value="stats.coverage"
        suffix="%"
        icon="PieChart"
        color="#f59e0b"
        :trend="coverageTrend"
      />
      <ScoreCard
        title="平均质量评分"
        :value="stats.avgScore ?? '—'"
        :suffix="stats.avgScore !== null && stats.avgScore !== undefined ? '分' : ''"
        icon="TrendCharts"
        color="#10b981"
        :trend="weekDeltaText"
        :trend-up="(stats.weekDelta ?? 0) >= 0"
      />
      <ScoreCard
        title="今日测试次数"
        :value="stats.todayRunCount"
        suffix="次"
        icon="Timer"
        color="#8b5cf6"
      />
    </div>

    <div class="panel">
      <h3 class="panel-title">
        <span>质量趋势</span>
        <span class="sub">最近 14 天运行平均评分</span>
      </h3>
      <QualityChart :option="trendOption" height="300px" />
    </div>

    <div class="grid-2">
      <div class="panel">
        <h3 class="panel-title">
          <span>风险接口 TOP5</span>
          <router-link class="more" to="/quality/score">质量中心 →</router-link>
        </h3>
        <RiskTable v-if="riskApis.length" :data="riskApis" />
        <el-empty v-else description="暂无风险接口" :image-size="60" />
      </div>

      <div class="panel">
        <h3 class="panel-title">
          <span>最近测试任务</span>
          <router-link class="more" to="/test/records">全部记录 →</router-link>
        </h3>
        <div v-if="recentRuns.length" class="run-list">
          <div
            v-for="r in recentRuns"
            :key="r.runId"
            class="run-row clickable"
            @click="openRun(r)"
          >
            <div class="run-main">
              <span class="run-name">{{ r.planName || '单用例调试' }}</span>
              <span class="run-time">{{ fmtTime(r.finishedAt || r.createdAt) }}</span>
            </div>
            <div class="run-side">
              <el-tag :type="runStatusType(r.status)" size="small" effect="dark">
                {{ r.status }}
              </el-tag>
              <span class="run-score" :style="{ color: scoreColor(r.score) }">
                {{ scoreText(r.score) }}
              </span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无执行记录" :image-size="60" />
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

.run-row.clickable {
  cursor: pointer;
}

.run-row.clickable:hover {
  background: rgba(51, 65, 85, 0.25);
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
}
</style>

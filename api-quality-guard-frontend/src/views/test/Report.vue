<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import ScoreCard from '@/components/ScoreCard.vue'
import QualityChart from '@/components/QualityChart.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const report = ref(null)

async function loadReport() {
  loading.value = true
  try {
    report.value = await http.get(`/reports/runs/${route.params.id}`)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadReport)

const statusType = (s) =>
  ({ SUCCESS: 'success', FAILED: 'danger', ERROR: 'warning', RUNNING: 'primary', PENDING: 'info', CANCELLED: 'info' })[s] ?? 'info'

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 19) : '—')

function fmtDuration(ms) {
  if (ms === null || ms === undefined) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

const scoreColor = (score) => {
  if (score === null || score === undefined) return '#64748b'
  if (score >= 90) return '#10b981'
  if (score >= 80) return '#3b82f6'
  if (score >= 60) return '#f59e0b'
  return '#ef4444'
}

const passRatePct = computed(() =>
  report.value ? (report.value.passRate * 100).toFixed(1) : '—',
)

const executedCount = computed(() => {
  const r = report.value
  return r ? r.successCount + r.failedCount + r.errorCount : 0
})

const distributionOption = computed(() => {
  const buckets = report.value?.durationDistribution ?? []
  const colors = ['#10b981', '#3b82f6', '#f59e0b', '#ef4444']
  return {
    grid: { left: 44, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: buckets.map((b) => b.range),
      axisLine: { lineStyle: { color: '#334155' } },
      axisLabel: { color: '#94a3b8' },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#1e293b' } },
      axisLabel: { color: '#94a3b8' },
    },
    series: [
      {
        type: 'bar',
        barWidth: 42,
        data: buckets.map((b, i) => ({
          value: b.count,
          itemStyle: { color: colors[i], borderRadius: [6, 6, 0, 0] },
        })),
        label: { show: true, position: 'top', color: '#e2e8f0' },
      },
    ],
  }
})

const outcomeOption = computed(() => {
  const r = report.value
  if (!r) return {}
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#94a3b8' } },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['50%', '44%'],
        label: { color: '#e2e8f0', formatter: '{b} {c}' },
        labelLine: { lineStyle: { color: '#475569' } },
        data: [
          { name: '通过', value: r.successCount, itemStyle: { color: '#10b981' } },
          { name: '失败', value: r.failedCount, itemStyle: { color: '#ef4444' } },
          { name: '异常', value: r.errorCount, itemStyle: { color: '#f59e0b' } },
        ],
      },
    ],
  }
})

const scoreDims = computed(() => {
  const detail = report.value?.scoreDetail
  if (!detail) return []
  return [
    { name: '功能通过率', score: detail.functionalScore, weight: detail.functionalWeight },
    { name: '性能达标率', score: detail.performanceScore, weight: detail.performanceWeight },
    { name: '稳定性', score: detail.stabilityScore, weight: detail.stabilityWeight },
  ].filter((dim) => dim.score !== null && dim.score !== undefined)
})

const generationLabel = (type) =>
  ({ RULE: '规则生成', MANUAL: '手动创建', AI: 'AI 增强', UNKNOWN: '未知' })[type] ?? type

const generationTagType = (type) =>
  ({ RULE: 'primary', MANUAL: 'info', AI: 'success' })[type] ?? 'warning'

const generationRate = (row) => (row.total ? Math.round((row.passed / row.total) * 1000) / 10 : 0)

const generationBarColor = (rate) => (rate >= 90 ? '#10b981' : rate >= 60 ? '#f59e0b' : '#ef4444')
</script>

<template>
  <div v-loading="loading" class="page">
    <template v-if="report">
      <div class="panel">
        <div class="head-row">
          <div class="head-left">
            <el-button text class="back-btn" @click="router.push('/test/records')">
              <el-icon><ArrowLeft /></el-icon>
              <span>执行记录</span>
            </el-button>
            <h2 class="run-title">运行报告 <span class="run-no">#{{ report.runId }}</span></h2>
            <el-tag :type="statusType(report.status)" effect="dark">{{ report.status }}</el-tag>
          </div>
          <el-button plain @click="loadReport">
            <el-icon><Refresh /></el-icon>
            <span>刷新</span>
          </el-button>
        </div>
        <div class="head-meta">
          <span>计划：{{ report.planName || '单用例调试' }}</span>
          <span>开始：{{ fmtTime(report.startedAt) }}</span>
          <span>结束：{{ fmtTime(report.finishedAt) }}</span>
          <span>总耗时：{{ fmtDuration(report.durationMs) }}</span>
        </div>
      </div>

      <div class="cards">
        <ScoreCard title="用例总数" :value="report.totalCount" suffix="条" icon="Document" color="#3b82f6" />
        <ScoreCard title="通过率" :value="passRatePct" suffix="%" icon="CircleCheck" color="#10b981" />
        <ScoreCard
          title="平均响应时间"
          :value="report.responseTime?.avgMs ?? '—'"
          :suffix="report.responseTime ? 'ms' : ''"
          icon="Timer"
          color="#f59e0b"
        />
        <ScoreCard
          title="综合评分"
          :value="report.score ?? '—'"
          :suffix="report.score !== null && report.score !== undefined ? '分' : ''"
          icon="TrendCharts"
          color="#8b5cf6"
        />
      </div>

      <div class="grid-2">
        <div class="panel">
          <h3 class="panel-title">
            <span>响应时间分布</span>
            <span v-if="report.responseTime" class="sub">
              min {{ report.responseTime.minMs }} ms · avg {{ report.responseTime.avgMs }} ms · max
              {{ report.responseTime.maxMs }} ms
            </span>
          </h3>
          <QualityChart v-if="report.responseTime" :option="distributionOption" height="260px" />
          <el-empty v-else description="暂无有效响应数据（异常结果不计入响应时间）" :image-size="70" />
        </div>
        <div class="panel">
          <h3 class="panel-title">
            <span>用例结果构成</span>
            <span class="sub">通过 {{ report.successCount }} · 失败 {{ report.failedCount }} · 异常 {{ report.errorCount }}</span>
          </h3>
          <QualityChart v-if="executedCount > 0" :option="outcomeOption" height="260px" />
          <el-empty v-else description="暂无执行结果" :image-size="70" />
        </div>
      </div>

      <div class="grid-2">
        <div class="panel">
          <h3 class="panel-title">
            <span>评分明细</span>
            <span class="sub">评分随运行结束自动计算</span>
          </h3>
          <template v-if="report.scoreDetail">
            <div class="score-top">
              <div class="score-total" :style="{ color: scoreColor(report.scoreDetail.totalScore) }">
                {{ report.scoreDetail.totalScore }}
              </div>
              <div class="score-meta">
                <span class="grade">{{ report.scoreDetail.grade }}</span>
                <span class="sub">
                  功能维度 {{ report.scoreDetail.passedCount }}/{{ report.scoreDetail.totalCount }} 通过
                </span>
              </div>
            </div>
            <div class="dims">
              <div v-for="dim in scoreDims" :key="dim.name" class="dim">
                <span class="dim-name">{{ dim.name }}</span>
                <b :style="{ color: scoreColor(dim.score) }">{{ dim.score }}</b>
                <small>权重 {{ (dim.weight * 100).toFixed(1) }}%</small>
              </div>
            </div>
          </template>
          <el-empty v-else description="本次运行暂无评分（仅正常结束的运行会评分）" :image-size="70" />
        </div>
        <div class="panel">
          <h3 class="panel-title">
            <span>用例来源表现</span>
            <span class="sub">规则 / 手动 / AI 增强</span>
          </h3>
          <el-table
            v-if="report.generationTypeBreakdown.length"
            :data="report.generationTypeBreakdown"
            class="dark-table"
          >
            <el-table-column label="来源" width="120">
              <template #default="{ row }">
                <el-tag :type="generationTagType(row.generationType)" size="small" effect="dark">
                  {{ generationLabel(row.generationType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="total" label="用例数" width="90" />
            <el-table-column prop="passed" label="通过数" width="90" />
            <el-table-column label="通过率" min-width="160">
              <template #default="{ row }">
                <el-progress
                  :percentage="generationRate(row)"
                  :color="generationBarColor(generationRate(row))"
                  :stroke-width="10"
                />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无执行结果" :image-size="70" />
        </div>
      </div>

      <div class="footer-actions">
        <el-button plain @click="router.push('/test/records')">返回执行记录</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  padding: 0 4px;
}

.run-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.run-no {
  color: var(--aqg-text-secondary);
  font-weight: 500;
}

.head-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

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

.score-top {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.score-total {
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
}

.score-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.grade {
  font-size: 14px;
  color: var(--aqg-text);
}

.dims {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}

.dim {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--aqg-border);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.45);
}

.dim-name {
  font-size: 12.5px;
  color: var(--aqg-text-secondary);
}

.dim b {
  font-size: 20px;
}

.dim small {
  color: var(--aqg-text-muted);
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
}
</style>

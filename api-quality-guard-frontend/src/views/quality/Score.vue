<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import QualityChart from '@/components/QualityChart.vue'

const loading = ref(true)
const quality = ref(null)

async function loadQuality() {
  loading.value = true
  try {
    quality.value = await http.get('/reports/quality')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadQuality)

const overview = computed(() => quality.value ?? {
  total: 0,
  grade: '暂无数据',
  weekDelta: null,
  dimensions: [],
  scoreTrend: [],
})

const dimensions = computed(() => overview.value.dimensions ?? [])
const chartDims = computed(() => dimensions.value.filter((d) => d.score !== null && d.score !== undefined))
const scoreTrend = computed(() => overview.value.scoreTrend ?? [])
const hasQualityData = computed(() => chartDims.value.length > 0)

const DIM_COLORS = {
  specification: '#3b82f6',
  coverage: '#f59e0b',
  performance: '#10b981',
  stability: '#8b5cf6',
}

const dimColor = (v) => {
  if (v === null || v === undefined) return 'var(--aqg-text-muted)'
  if (v >= 90) return '#10b981'
  if (v >= 80) return '#3b82f6'
  if (v >= 70) return '#f59e0b'
  return '#ef4444'
}

const totalColor = computed(() => (hasQualityData.value ? dimColor(overview.value.total) : 'var(--aqg-text-muted)'))

const gradeType = (g) =>
  ({ 优秀: 'success', 良好: 'primary', 及格: 'warning', 不合格: 'danger' })[g] ?? 'info'

const scoreText = (v) => (v === null || v === undefined ? '—' : v)

const weekDeltaText = computed(() => {
  const delta = overview.value.weekDelta
  if (delta === null || delta === undefined) return '暂无周环比'
  return `运行评分较上周 ${delta >= 0 ? '+' : ''}${delta}`
})

const weekDeltaUp = computed(() => (overview.value.weekDelta ?? 0) >= 0)

const radarOption = computed(() => ({
  radar: {
    indicator: chartDims.value.map((d) => ({ name: d.name, max: 100 })),
    radius: '68%',
    axisName: { color: '#94a3b8' },
    axisLine: { lineStyle: { color: '#334155' } },
    splitLine: { lineStyle: { color: '#334155' } },
    splitArea: {
      areaStyle: { color: ['rgba(30, 41, 59, 0.35)', 'rgba(15, 23, 42, 0.35)'] },
    },
  },
  series: [
    {
      type: 'radar',
      data: [
        {
          name: '当前评分',
          value: chartDims.value.map((d) => d.score),
          lineStyle: { color: '#3b82f6', width: 2 },
          itemStyle: { color: '#3b82f6' },
          areaStyle: { color: 'rgba(59, 130, 246, 0.3)' },
        },
      ],
    },
  ],
}))

const barOption = computed(() => ({
  grid: { left: 44, right: 20, top: 30, bottom: 30 },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: chartDims.value.map((d) => d.name),
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
      type: 'bar',
      barWidth: 34,
      data: chartDims.value.map((d) => ({
        value: d.score,
        itemStyle: { color: DIM_COLORS[d.key] ?? '#3b82f6', borderRadius: [6, 6, 0, 0] },
      })),
      label: { show: true, position: 'top', color: '#e2e8f0' },
    },
  ],
}))

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
      lineStyle: { color: '#8b5cf6', width: 2.5 },
      itemStyle: { color: '#8b5cf6' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(139, 92, 246, 0.35)' },
            { offset: 1, color: 'rgba(139, 92, 246, 0.02)' },
          ],
        },
      },
    },
  ],
}))
</script>

<template>
  <div v-loading="loading" class="page">
    <div class="grid-2">
      <div class="panel total-panel">
        <div class="total-head">
          <div>
            <div class="total-label">
              总体质量评分
              <span class="hint">四维等权，无数据维度权重按比例摊回</span>
            </div>
            <div class="total-score" :style="{ color: totalColor }">
              {{ hasQualityData ? overview.total : '—' }}
              <span v-if="hasQualityData" class="total-unit">分</span>
            </div>
            <div class="total-meta">
              <el-tag :type="gradeType(overview.grade)" effect="dark" size="small">{{ overview.grade }}</el-tag>
              <span class="delta" :class="weekDeltaUp ? 'up' : 'down'">{{ weekDeltaText }}</span>
            </div>
          </div>
        </div>

        <div class="dim-list">
          <div v-for="d in dimensions" :key="d.key" class="dim-item">
            <div class="dim-head">
              <span class="dim-name">{{ d.name }}</span>
              <span class="dim-value" :style="{ color: dimColor(d.score) }">{{ scoreText(d.score) }}</span>
            </div>
            <el-progress
              :percentage="d.score ?? 0"
              :stroke-width="9"
              :show-text="false"
              :color="dimColor(d.score)"
            />
            <span class="dim-detail">{{ d.detail }}</span>
          </div>
        </div>
      </div>

      <div class="panel">
        <h3 class="panel-title">质量维度雷达</h3>
        <QualityChart v-if="chartDims.length >= 3" :option="radarOption" height="330px" />
        <el-empty v-else description="维度数据不足，暂无法绘制雷达图" :image-size="70" />
      </div>
    </div>

    <div class="grid-2">
      <div class="panel">
        <h3 class="panel-title">各维度评分对比</h3>
        <QualityChart v-if="chartDims.length" :option="barOption" height="300px" />
        <el-empty v-else description="暂无可评分维度" :image-size="70" />
      </div>
      <div class="panel">
        <h3 class="panel-title">
          <span>评分趋势（近 14 天）</span>
          <span class="sub">运行平均分</span>
        </h3>
        <QualityChart :option="trendOption" height="300px" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.total-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.total-label {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.hint {
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.total-score {
  font-size: 56px;
  font-weight: 800;
  line-height: 1.15;
}

.total-unit {
  font-size: 18px;
  font-weight: 500;
  color: var(--aqg-text-muted);
  margin-left: 6px;
}

.total-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.delta {
  font-size: 13px;
}

.delta.up {
  color: var(--aqg-green);
}

.delta.down {
  color: var(--aqg-red);
}

.dim-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px 26px;
}

@media (max-width: 1200px) {
  .dim-list {
    grid-template-columns: 1fr;
  }
}

.dim-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dim-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dim-name {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.dim-value {
  font-weight: 700;
}

.dim-detail {
  font-size: 11.5px;
  color: var(--aqg-text-muted);
}

.sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}
</style>

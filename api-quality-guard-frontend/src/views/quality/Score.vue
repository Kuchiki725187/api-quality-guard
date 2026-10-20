<script setup>
import { computed } from 'vue'
import QualityChart from '@/components/QualityChart.vue'
import { qualityOverview, scoreTrend } from '@/mock/data'

const overview = qualityOverview

const radarOption = computed(() => ({
  radar: {
    indicator: overview.dimensions.map((d) => ({ name: d.name, max: 100 })),
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
          value: overview.dimensions.map((d) => d.score),
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
    data: overview.dimensions.map((d) => d.name),
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
      data: overview.dimensions.map((d) => ({
        value: d.score,
        itemStyle: { color: d.color, borderRadius: [6, 6, 0, 0] },
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
      name: '质量评分',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: scoreTrend.values,
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

const dimColor = (v) => (v >= 90 ? '#10b981' : v >= 80 ? '#3b82f6' : v >= 70 ? '#f59e0b' : '#ef4444')
</script>

<template>
  <div class="page">
    <div class="grid-2">
      <div class="panel total-panel">
        <div class="total-head">
          <div>
            <div class="total-label">总体质量评分</div>
            <div class="total-score">
              {{ overview.total }}
              <span class="total-unit">分</span>
            </div>
            <div class="total-meta">
              <el-tag type="success" effect="dark" size="small">{{ overview.grade }}</el-tag>
              <span class="delta">较上周 +{{ overview.weekDelta }}</span>
            </div>
          </div>
        </div>

        <div class="dim-list">
          <div v-for="d in overview.dimensions" :key="d.name" class="dim-item">
            <div class="dim-head">
              <span class="dim-name">{{ d.name }}</span>
              <span class="dim-value" :style="{ color: dimColor(d.score) }">{{ d.score }}</span>
            </div>
            <el-progress
              :percentage="d.score"
              :stroke-width="9"
              :show-text="false"
              :color="dimColor(d.score)"
            />
          </div>
        </div>
      </div>

      <div class="panel">
        <h3 class="panel-title">质量维度雷达</h3>
        <QualityChart :option="radarOption" height="330px" />
      </div>
    </div>

    <div class="grid-2">
      <div class="panel">
        <h3 class="panel-title">各维度评分对比</h3>
        <QualityChart :option="barOption" height="300px" />
      </div>
      <div class="panel">
        <h3 class="panel-title">评分趋势（近 14 天）</h3>
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
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.total-score {
  font-size: 56px;
  font-weight: 800;
  line-height: 1.15;
  color: var(--aqg-green);
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
  color: var(--aqg-green);
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
</style>

<script setup>
defineProps({
  title: { type: String, required: true },
  value: { type: [String, Number], required: true },
  suffix: { type: String, default: '' },
  icon: { type: String, default: 'DataLine' },
  color: { type: String, default: '#3b82f6' },
  trend: { type: String, default: '' },
  trendUp: { type: Boolean, default: true },
})
</script>

<template>
  <div class="score-card panel">
    <div class="icon-box" :style="{ background: color + '22', color }">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="meta">
      <div class="title">{{ title }}</div>
      <div class="value-row">
        <span class="value">{{ value }}</span>
        <span v-if="suffix" class="suffix">{{ suffix }}</span>
        <span v-if="trend" class="trend" :class="trendUp ? 'up' : 'down'">
          {{ trend }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.score-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  flex-shrink: 0;
}

.meta {
  min-width: 0;
}

.title {
  font-size: 13px;
  color: var(--aqg-text-secondary);
  margin-bottom: 4px;
}

.value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
}

.suffix {
  font-size: 13px;
  color: var(--aqg-text-muted);
}

.trend {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
}

.trend.up {
  color: var(--aqg-green);
  background: rgba(16, 185, 129, 0.14);
}

.trend.down {
  color: var(--aqg-red);
  background: rgba(239, 68, 68, 0.14);
}
</style>

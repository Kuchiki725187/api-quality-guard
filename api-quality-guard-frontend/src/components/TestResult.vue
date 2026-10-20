<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: { type: Object, default: null },
})

const statusMeta = computed(() => {
  const s = props.result?.status
  if (s === 'PASSED') return { text: '通过', type: 'success' }
  if (s === 'FAILED') return { text: '断言失败', type: 'danger' }
  if (s === 'ERROR') return { text: '执行异常', type: 'warning' }
  return { text: '未执行', type: 'info' }
})
</script>

<template>
  <div v-if="result" class="test-result">
    <div class="head">
      <el-tag :type="statusMeta.type" effect="dark">{{ statusMeta.text }}</el-tag>
      <span class="duration">耗时 {{ result.durationMs }} ms</span>
    </div>
    <div class="assertions">
      <div v-for="(a, i) in result.assertions" :key="i" class="assertion-row">
        <el-icon :color="a.passed ? '#10b981' : '#ef4444'" :size="16">
          <CircleCheck v-if="a.passed" />
          <CircleClose v-else />
        </el-icon>
        <span class="a-name">{{ a.name }}</span>
        <span class="a-detail">期望 {{ a.expected }} · 实际 {{ a.actual }}</span>
      </div>
    </div>
  </div>
  <el-empty v-else description="暂无执行结果" :image-size="80" />
</template>

<style scoped>
.test-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.duration {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.assertions {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--aqg-border);
  border-radius: 8px;
  overflow: hidden;
}

.assertion-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--aqg-panel);
  border-bottom: 1px solid var(--aqg-border);
  font-size: 13px;
}

.assertion-row:last-child {
  border-bottom: none;
}

.a-name {
  flex: 1;
  color: var(--aqg-text);
}

.a-detail {
  color: var(--aqg-text-muted);
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
}
</style>

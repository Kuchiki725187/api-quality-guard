<script setup>
defineProps({
  data: { type: Array, default: () => [] },
})

const methodClass = (m) => 'method-tag ' + String(m).toLowerCase()
const riskType = (r) => (r === '高' ? 'danger' : r === '中' ? 'warning' : 'success')
</script>

<template>
  <div class="risk-list">
    <div v-for="item in data" :key="item.apiId ?? item.method + item.path" class="risk-row">
      <div class="left">
        <span :class="methodClass(item.method)">{{ item.method }}</span>
        <div class="name-wrap">
          <span class="name">{{ item.name }}</span>
          <code class="path">{{ item.path }}</code>
        </div>
      </div>
      <div class="right">
        <span class="issue">{{ item.issue }}</span>
        <el-tag :type="riskType(item.risk)" effect="dark" size="small">
          {{ item.risk }}风险
        </el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.risk-list {
  display: flex;
  flex-direction: column;
}

.risk-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 4px;
  border-bottom: 1px solid var(--aqg-border);
}

.risk-row:last-child {
  border-bottom: none;
}

.left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.name-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.name {
  font-size: 14px;
  font-weight: 600;
}

.path {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.issue {
  font-size: 13px;
  color: var(--aqg-text-secondary);
  max-width: 380px;
  text-align: right;
}
</style>

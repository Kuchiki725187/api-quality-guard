<script setup>
defineProps({
  report: { type: Object, required: true },
})

const riskType = (r) =>
  String(r).includes('高') ? 'danger' : String(r).includes('中') ? 'warning' : 'success'
</script>

<template>
  <div class="ai-report">
    <div class="head">
      <div class="head-left">
        <span class="api-name">{{ report.apiName }}</span>
        <code class="api-path">{{ report.api }}</code>
      </div>
      <div class="head-right">
        <el-tag :type="riskType(report.riskLevel)" effect="dark">{{ report.riskLevel }}</el-tag>
        <span class="score">质量分 <b>{{ report.score }}</b></span>
      </div>
    </div>

    <div class="section">
      <div class="section-title">检测问题</div>
      <p class="text">{{ report.issue }}</p>
    </div>

    <div class="section">
      <div class="section-title">潜在影响</div>
      <p class="text impact">{{ report.impact }}</p>
    </div>

    <div class="section advice">
      <div class="section-title ai-title">
        <el-icon><MagicStick /></el-icon>
        <span>AI 建议</span>
      </div>
      <ul class="advice-list">
        <li v-for="(s, i) in report.suggestions" :key="i">{{ s }}</li>
      </ul>
    </div>

    <div class="grid-2">
      <div class="section sub">
        <div class="section-title">优化建议</div>
        <ul class="advice-list">
          <li v-for="(s, i) in report.optimization" :key="i">{{ s }}</li>
        </ul>
      </div>
      <div class="section sub">
        <div class="section-title">测试建议</div>
        <ul class="advice-list">
          <li v-for="(s, i) in report.testAdvice" :key="i">{{ s }}</li>
        </ul>
      </div>
    </div>

    <div class="risk-note">
      <el-icon><Warning /></el-icon>
      <span>{{ report.riskNote }}</span>
    </div>
  </div>
</template>

<style scoped>
.ai-report {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--aqg-border);
}

.head-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.api-name {
  font-size: 17px;
  font-weight: 700;
}

.api-path {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
  color: var(--aqg-text-muted);
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.score {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.score b {
  color: var(--aqg-text);
  font-size: 15px;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--aqg-text-secondary);
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--aqg-purple);
}

.text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
}

.impact {
  color: #fbbf24;
}

.advice {
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid rgba(139, 92, 246, 0.35);
  background: rgba(139, 92, 246, 0.08);
}

.advice-list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--aqg-text);
}

.sub {
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid var(--aqg-border);
  background: var(--aqg-panel);
}

.risk-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.3);
  font-size: 13px;
  line-height: 1.6;
  color: #fca5a5;
}
</style>

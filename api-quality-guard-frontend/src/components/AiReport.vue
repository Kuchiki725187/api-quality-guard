<script setup>
defineProps({
  diag: { type: Object, required: true },
})

const confidencePercent = (c) => Math.round((c ?? 0) * 100)
</script>

<template>
  <div class="ai-report">
    <div class="head">
      <div class="head-left">
        <span class="api-name">{{ diag.caseName || '未知用例' }}</span>
        <code class="api-path">结果 #{{ diag.testResultId }}</code>
      </div>
      <div class="head-right">
        <span class="meta">耗时 {{ diag.durationMs ?? 0 }} ms</span>
      </div>
    </div>

    <div class="section conclusion-block">
      <div class="section-title ai-title">
        <el-icon><MagicStick /></el-icon>
        <span>诊断结论</span>
      </div>
      <p class="conclusion">{{ diag.candidateCauses?.conclusion ?? '—' }}</p>
    </div>

    <div class="section">
      <div class="section-title">候选原因</div>
      <div
        v-for="(c, i) in diag.candidateCauses?.causes ?? []"
        :key="i"
        class="cause-card"
      >
        <div class="cause-head">
          <span class="cause-title">{{ c.cause }}</span>
          <span class="confidence">置信度 {{ confidencePercent(c.confidence) }}%</span>
        </div>
        <el-progress
          :percentage="confidencePercent(c.confidence)"
          :stroke-width="6"
          :show-text="false"
          color="#8b5cf6"
        />
        <ul class="evidence">
          <li v-for="(e, j) in c.evidence" :key="j">{{ e }}</li>
        </ul>
      </div>
      <el-empty
        v-if="!diag.candidateCauses?.causes?.length"
        description="无候选原因"
        :image-size="60"
      />
    </div>

    <div class="section advice" v-if="diag.candidateCauses?.suggestions?.length">
      <div class="section-title ai-title">
        <el-icon><MagicStick /></el-icon>
        <span>处理建议</span>
      </div>
      <ul class="advice-list">
        <li v-for="(s, i) in diag.candidateCauses.suggestions" :key="i">{{ s }}</li>
      </ul>
    </div>

    <el-collapse class="raw">
      <el-collapse-item title="模型原始输出（排查用）" name="raw">
        <pre class="json-view">{{ diag.rawModelOutput || '—' }}</pre>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.ai-report {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta {
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.section {
  display: flex;
  flex-direction: column;
  gap: 10px;
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

.conclusion {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.8;
  padding: 14px 16px;
  border-radius: 8px;
  background: rgba(139, 92, 246, 0.08);
  border: 1px solid rgba(139, 92, 246, 0.35);
}

.cause-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid var(--aqg-border);
  background: var(--aqg-panel);
}

.cause-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cause-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--aqg-text);
}

.confidence {
  font-size: 12px;
  color: #a78bfa;
  white-space: nowrap;
}

.evidence {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--aqg-text-secondary);
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

.raw {
  --el-collapse-border-color: var(--aqg-border);
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
  --el-collapse-header-text-color: var(--aqg-text-secondary);
}
</style>

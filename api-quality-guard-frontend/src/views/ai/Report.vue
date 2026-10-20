<script setup>
import { computed, ref } from 'vue'
import AiReport from '@/components/AiReport.vue'
import { aiReports } from '@/mock/data'

const currentId = ref(aiReports[0].id)
const current = computed(() => aiReports.find((r) => r.id === currentId.value))

const riskType = (r) =>
  String(r).includes('高') ? 'danger' : String(r).includes('中') ? 'warning' : 'success'
</script>

<template>
  <div class="page">
    <div class="panel head-panel">
      <div class="ai-title">
        <el-icon color="#8b5cf6" :size="20"><MagicStick /></el-icon>
        <span>AI 质量分析报告</span>
        <el-tag effect="plain" size="small" class="disclaimer">由 AI 生成 · 供参考</el-tag>
      </div>
      <el-select v-model="currentId" style="width: 320px">
        <el-option
          v-for="r in aiReports"
          :key="r.id"
          :label="`${r.apiName}（${r.api}）`"
          :value="r.id"
        >
          <div class="option-row">
            <span>{{ r.apiName }}</span>
            <el-tag :type="riskType(r.riskLevel)" size="small" effect="dark">
              {{ r.riskLevel }}
            </el-tag>
          </div>
        </el-option>
      </el-select>
    </div>

    <div class="panel">
      <AiReport :report="current" />
    </div>
  </div>
</template>

<style scoped>
.head-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
}

.disclaimer {
  color: var(--aqg-purple);
  border-color: rgba(139, 92, 246, 0.5);
}

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>

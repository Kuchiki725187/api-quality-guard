<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const runs = ref([])
const selectedRunId = ref(null)
const checking = ref(false)
const result = ref(null)

const records = ref([])
const recordTotal = ref(0)
const recordsLoading = ref(false)
const recordsQuery = reactive({ page: 1, pageSize: 10 })

onMounted(() => {
  loadRuns()
  loadRecords()
})

async function loadRuns() {
  try {
    const data = await http.get('/runs', { params: { page: 1, pageSize: 50 } })
    runs.value = data.records.filter((r) => r.status === 'SUCCESS' || r.status === 'FAILED')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function check() {
  if (!selectedRunId.value) {
    ElMessage.warning('请选择已结束的执行任务')
    return
  }
  checking.value = true
  try {
    result.value = await http.post('/gates/check', { runId: selectedRunId.value })
    ElMessage.success(result.value.passed ? '门禁通过' : '门禁阻断')
    loadRecords()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    checking.value = false
  }
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const data = await http.get('/gates/records', {
      params: { page: recordsQuery.page, pageSize: recordsQuery.pageSize },
    })
    records.value = data.records
    recordTotal.value = data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    recordsLoading.value = false
  }
}

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 19) : '—')
const scoreView = (s) => (s === null || s === undefined ? '—' : Number(s).toFixed(1))
</script>

<template>
  <div class="page">
    <div class="panel">
      <h3 class="panel-title">
        <span>质量门禁检查</span>
        <span class="hint">默认规则：评分 ≥ 80 · 执行异常 = 0 · 失败用例 ≤ 0（阈值可在计划配置或请求中覆盖）</span>
      </h3>
      <div class="check-row">
        <el-select
          v-model="selectedRunId"
          placeholder="选择已结束的执行任务"
          filterable
          style="width: 420px"
        >
          <el-option
            v-for="r in runs"
            :key="r.id"
            :label="`#${r.id} · ${r.planName || '单用例调试'} · ${r.status} · 评分 ${scoreView(r.score)}`"
            :value="r.id"
          />
        </el-select>
        <el-button type="primary" :loading="checking" @click="check">执行门禁检查</el-button>
      </div>

      <div v-if="result" class="status-panel" :class="result.passed ? 'pass' : 'block'">
        <el-icon :size="48" :color="result.passed ? '#10b981' : '#ef4444'">
          <CircleCheck v-if="result.passed" />
          <CircleClose v-else />
        </el-icon>
        <div class="status-main">
          <div class="status-title">{{ result.passed ? '发布通过' : '发布阻断' }}</div>
          <div class="status-sub">
            任务 #{{ result.runId }} · 评分 {{ scoreView(result.score) }}
            （阈值 ≥ {{ result.thresholds.scoreThreshold }}） · 记录 #{{ result.recordId }}
          </div>
        </div>
        <el-tag :type="result.passed ? 'success' : 'danger'" effect="dark" size="large">
          {{ result.passed ? 'GATE PASSED' : 'GATE BLOCKED' }}
        </el-tag>
      </div>

      <div v-if="result && result.violations.length" class="violation-list">
        <div v-for="(v, i) in result.violations" :key="i" class="violation-row">
          <el-tag type="danger" size="small" effect="dark">阻断</el-tag>
          <div class="violation-main">
            <div class="violation-rule">{{ v.rule }}</div>
            <div class="violation-detail">{{ v.detail }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="panel">
      <h3 class="panel-title"><span>门禁检查历史</span></h3>
      <el-table v-loading="recordsLoading" :data="records" class="dark-table">
        <el-table-column label="检查时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="任务" width="100">
          <template #default="{ row }">#{{ row.runId }}</template>
        </el-table-column>
        <el-table-column label="结论" width="100">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.passed ? '通过' : '阻断' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="100">
          <template #default="{ row }">{{ scoreView(row.score) }}</template>
        </el-table-column>
        <el-table-column label="阈值" min-width="240">
          <template #default="{ row }">
            <span class="threshold-text">
              评分 ≥ {{ row.thresholds?.scoreThreshold }} · 失败 ≤ {{ row.thresholds?.maxFailedCount }} ·
              异常 ≤ {{ row.thresholds?.maxErrorCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="违规数" width="90">
          <template #default="{ row }">{{ row.violations?.length ?? 0 }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="recordsQuery.page"
          v-model:page-size="recordsQuery.pageSize"
          :total="recordTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="loadRecords"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.check-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.status-panel {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 20px;
  border: 1px solid var(--aqg-border);
  border-radius: 10px;
}

.status-panel.block {
  border-color: rgba(239, 68, 68, 0.45);
  background: linear-gradient(90deg, rgba(239, 68, 68, 0.1), var(--aqg-card) 55%);
}

.status-panel.pass {
  border-color: rgba(16, 185, 129, 0.45);
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.1), var(--aqg-card) 55%);
}

.status-main {
  flex: 1;
}

.status-title {
  font-size: 24px;
  font-weight: 800;
}

.status-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.violation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.violation-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--aqg-panel);
  border: 1px solid var(--aqg-border);
}

.violation-rule {
  font-size: 14px;
  font-weight: 600;
}

.violation-detail {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--aqg-text-secondary);
}

.threshold-text {
  font-size: 12.5px;
  color: var(--aqg-text-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.dark-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--aqg-panel);
  --el-table-header-text-color: var(--aqg-text-secondary);
  --el-table-border-color: var(--aqg-border);
  --el-table-row-hover-bg-color: #273449;
}
</style>

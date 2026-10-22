<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const router = useRouter()

// ---------- 测试计划 ----------
const plans = ref([])
const plansLoading = ref(false)
const createVisible = ref(false)
const creating = ref(false)
const planForm = reactive({ name: '', description: '', caseIds: [] })
const allCases = ref([])
const runningPlanId = ref(null)

async function loadPlans() {
  plansLoading.value = true
  try {
    const data = await http.get('/plans', { params: { page: 1, pageSize: 100 } })
    plans.value = data.records
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    plansLoading.value = false
  }
}

async function loadCases() {
  try {
    const data = await http.get('/cases', { params: { page: 1, pageSize: 200 } })
    allCases.value = data.records
  } catch {
    // 用例列表加载失败不阻塞计划管理
  }
}

function openCreatePlan() {
  Object.assign(planForm, { name: '', description: '', caseIds: [] })
  createVisible.value = true
  if (!allCases.value.length) {
    loadCases()
  }
}

async function submitPlan() {
  if (!planForm.name.trim()) {
    ElMessage.warning('请填写计划名称')
    return
  }
  if (!planForm.caseIds.length) {
    ElMessage.warning('请至少选择一个用例')
    return
  }
  creating.value = true
  try {
    await http.post('/plans', {
      name: planForm.name.trim(),
      description: planForm.description.trim() || undefined,
      caseIds: planForm.caseIds,
    })
    ElMessage.success('计划创建成功')
    createVisible.value = false
    loadPlans()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    creating.value = false
  }
}

async function triggerRun(plan) {
  runningPlanId.value = plan.id
  try {
    const data = await http.post(`/plans/${plan.id}/runs`)
    ElMessage.success(`已提交执行（任务 #${data.runId}）`)
    runsQuery.page = 1
    await loadRuns()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    runningPlanId.value = null
  }
}

async function removePlan(plan) {
  try {
    await ElMessageBox.confirm(`确定删除计划「${plan.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await http.delete(`/plans/${plan.id}`)
    ElMessage.success('计划已删除')
    loadPlans()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------- 执行记录 ----------
const runs = ref([])
const runTotal = ref(0)
const runsLoading = ref(false)
const runsQuery = reactive({ page: 1, pageSize: 10 })
let pollTimer = null

async function loadRuns() {
  runsLoading.value = true
  try {
    const data = await http.get('/runs', {
      params: { page: runsQuery.page, pageSize: runsQuery.pageSize },
    })
    runs.value = data.records
    runTotal.value = data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    runsLoading.value = false
    syncPolling()
  }
}

// 存在进行中的任务时每 3 秒轮询刷新
function syncPolling() {
  const hasActive = runs.value.some((r) => r.status === 'PENDING' || r.status === 'RUNNING')
  if (hasActive && !pollTimer) {
    pollTimer = setInterval(loadRuns, 3000)
  } else if (!hasActive && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})

async function cancelRun(run) {
  try {
    await http.post(`/runs/${run.id}/cancel`)
    ElMessage.success('已请求取消')
    loadRuns()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------- AI 诊断 ----------
const diagnosingId = ref(null)

async function diagnose(row) {
  diagnosingId.value = row.id
  ElMessage.info('正在请求 AI 诊断，模型分析可能需要 10~60 秒…')
  try {
    // 诊断同步等待模型返回（含工具循环），单独放宽超时
    const data = await http.post(`/results/${row.id}/diagnose`, {}, { timeout: 150000 })
    ElMessage.success('诊断完成')
    resultVisible.value = false
    router.push({ path: '/ai/report', query: { diagnosisId: data.id } })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    diagnosingId.value = null
  }
}

const statusType = (s) =>
  ({ SUCCESS: 'success', FAILED: 'danger', ERROR: 'warning', RUNNING: 'primary', PENDING: 'info', CANCELLED: 'info' })[s] ?? 'info'

const isActive = (s) => s === 'PENDING' || s === 'RUNNING'

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 19) : '—')

const scoreColor = (score) => {
  if (score === null || score === undefined) return '#64748b'
  if (score >= 90) return '#10b981'
  if (score >= 80) return '#3b82f6'
  if (score >= 60) return '#f59e0b'
  return '#ef4444'
}

function durationText(run) {
  if (!run.startedAt || !run.finishedAt) return '—'
  const ms = new Date(run.finishedAt) - new Date(run.startedAt)
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

// ---------- 结果详情 ----------
const resultVisible = ref(false)
const currentRun = ref(null)
const resultRows = ref([])
const resultTotal = ref(0)
const resultLoading = ref(false)
const resultQuery = reactive({ page: 1, pageSize: 10 })

async function openResults(run) {
  currentRun.value = run
  resultQuery.page = 1
  resultVisible.value = true
  await loadResults()
}

async function loadResults() {
  if (!currentRun.value) return
  resultLoading.value = true
  try {
    const data = await http.get(`/runs/${currentRun.value.id}/results`, {
      params: { page: resultQuery.page, pageSize: resultQuery.pageSize },
    })
    resultRows.value = data.records
    resultTotal.value = data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    resultLoading.value = false
  }
}

function assertionText(a) {
  if (a.type === 'STATUS_CODE') return 'HTTP 状态码'
  if (a.type === 'JSON_FIELD') return `${a.path} ${a.operator}`
  if (a.type === 'RESPONSE_TIME') return '响应时间'
  return a.type
}

function expectedText(a) {
  if (a.type === 'RESPONSE_TIME') return `< ${a.expectedMs} ms`
  return a.expected ?? '—'
}

function prettyBody(body) {
  if (body === null || body === undefined || body === '') return '（空响应体）'
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return body
  }
}

onMounted(() => {
  loadPlans()
  loadRuns()
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <h3 class="panel-title">
        <span>测试计划</span>
        <span class="hint">选择用例组成计划，一键提交批量执行</span>
      </h3>
      <div class="toolbar">
        <el-button type="primary" @click="openCreatePlan">
          <el-icon><Plus /></el-icon>
          <span>新建计划</span>
        </el-button>
      </div>
      <el-table v-loading="plansLoading" :data="plans" class="dark-table">
        <el-table-column prop="name" label="计划名称" min-width="200" />
        <el-table-column prop="description" label="描述" min-width="220">
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column prop="caseCount" label="用例数" width="90" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              plain
              :loading="runningPlanId === row.id"
              @click="triggerRun(row)"
            >
              执行
            </el-button>
            <el-button size="small" type="danger" plain @click="removePlan(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel">
      <h3 class="panel-title">
        <span>执行记录</span>
        <span class="hint">进行中的任务每 3 秒自动刷新</span>
      </h3>
      <el-table v-loading="runsLoading" :data="runs" class="dark-table">
        <el-table-column prop="id" label="任务" width="80">
          <template #default="{ row }">#{{ row.id }}</template>
        </el-table-column>
        <el-table-column label="计划" min-width="160">
          <template #default="{ row }">{{ row.planName || '单用例调试' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="dark" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="统计" width="180">
          <template #default="{ row }">
            <span class="stat">
              通过 {{ row.successCount }} / 失败 {{ row.failedCount }} / 异常 {{ row.errorCount }}
              （共 {{ row.totalCount }}）
            </span>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="110">
          <template #default="{ row }">
            <span
              v-if="row.score !== null && row.score !== undefined"
              class="score-text"
              :style="{ color: scoreColor(row.score) }"
            >
              {{ Number(row.score).toFixed(1) }}
              <small v-if="row.scoreDetail">{{ row.scoreDetail.grade }}</small>
            </span>
            <span v-else class="score-text muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ durationText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button size="small" plain @click="openResults(row)">查看结果</el-button>
            <el-button v-if="isActive(row.status)" size="small" type="danger" plain @click="cancelRun(row)">
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="runsQuery.page"
          v-model:page-size="runsQuery.pageSize"
          :total="runTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRuns"
          @size-change="loadRuns"
        />
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新建测试计划" width="560px">
      <el-form label-width="80px" label-position="left">
        <el-form-item label="计划名称" required>
          <el-input v-model="planForm.name" placeholder="如：用户模块回归" maxlength="255" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="planForm.description" type="textarea" :rows="2" maxlength="1000" placeholder="可选" />
        </el-form-item>
        <el-form-item label="关联用例" required>
          <el-select
            v-model="planForm.caseIds"
            multiple
            filterable
            placeholder="选择用例（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="c in allCases"
              :key="c.id"
              :label="`${c.name}（${c.generationType}）`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitPlan">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="resultVisible" title="执行结果" size="720px">
      <template v-if="currentRun">
        <div class="run-summary">
          <el-tag :type="statusType(currentRun.status)" effect="dark">{{ currentRun.status }}</el-tag>
          <span>计划：{{ currentRun.planName || '单用例调试' }}</span>
          <span>通过 {{ currentRun.successCount }} / 失败 {{ currentRun.failedCount }} / 异常 {{ currentRun.errorCount }}</span>
        </div>

        <div v-if="currentRun.scoreDetail" class="score-block">
          <div class="score-total" :style="{ color: scoreColor(currentRun.scoreDetail.totalScore) }">
            {{ currentRun.scoreDetail.totalScore }}
            <span class="score-grade">{{ currentRun.scoreDetail.grade }}</span>
          </div>
          <div class="score-dims">
            <div class="dim">
              <span>功能通过率</span>
              <b>{{ currentRun.scoreDetail.functionalScore }}</b>
              <small>权重 {{ (currentRun.scoreDetail.functionalWeight * 100).toFixed(1) }}%</small>
            </div>
            <div v-if="currentRun.scoreDetail.performanceScore !== null" class="dim">
              <span>性能达标率</span>
              <b>{{ currentRun.scoreDetail.performanceScore }}</b>
              <small>权重 {{ (currentRun.scoreDetail.performanceWeight * 100).toFixed(1) }}%</small>
            </div>
            <div class="dim">
              <span>稳定性</span>
              <b>{{ currentRun.scoreDetail.stabilityScore }}</b>
              <small>权重 {{ (currentRun.scoreDetail.stabilityWeight * 100).toFixed(1) }}%</small>
            </div>
          </div>
        </div>
        <el-table v-loading="resultLoading" :data="resultRows" class="dark-table">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-box">
                <div v-if="row.errorMessage" class="run-error">{{ row.errorMessage }}</div>
                <div v-if="row.assertionResults?.length" class="assert-list">
                  <div v-for="(a, i) in row.assertionResults" :key="i" class="assert-line">
                    <el-icon :color="a.passed ? '#10b981' : '#ef4444'">
                      <CircleCheck v-if="a.passed" />
                      <CircleClose v-else />
                    </el-icon>
                    <span class="assert-name">{{ assertionText(a) }}</span>
                    <span class="assert-detail">期望 {{ expectedText(a) }} · 实际 {{ a.actual ?? '—' }}</span>
                  </div>
                </div>
                <div v-if="row.responseSnapshot" class="snap-block">
                  <div class="snap-label">响应（{{ row.responseSnapshot.statusCode }}）</div>
                  <pre class="json-view">{{ prettyBody(row.responseSnapshot.body) }}</pre>
                </div>
                <div v-if="row.status === 'FAILED' || row.status === 'ERROR'" class="expand-actions">
                  <el-button
                    size="small"
                    type="primary"
                    :loading="diagnosingId === row.id"
                    @click="diagnose(row)"
                  >
                    <el-icon><MagicStick /></el-icon>
                    <span>AI 诊断</span>
                  </el-button>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="caseName" label="用例" min-width="200" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'PASSED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'"
                size="small"
                effect="dark"
              >
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="100">
            <template #default="{ row }">{{ row.durationMs }} ms</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" min-width="200">
            <template #default="{ row }">{{ row.errorMessage || '—' }}</template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="resultQuery.page"
            v-model:page-size="resultQuery.pageSize"
            :total="resultTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            @current-change="loadResults"
            @size-change="loadResults"
          />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.stat {
  font-size: 12.5px;
  color: var(--aqg-text-secondary);
}

.run-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.score-text {
  font-weight: 700;
  font-size: 14px;
}

.score-text small {
  font-weight: 400;
  font-size: 11px;
  margin-left: 4px;
}

.score-text.muted {
  color: var(--aqg-text-muted);
  font-weight: 400;
}

.score-block {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 14px 16px;
  margin-bottom: 14px;
  border: 1px solid var(--aqg-border);
  border-radius: 10px;
  background: var(--aqg-panel);
}

.score-total {
  font-size: 34px;
  font-weight: 800;
}

.score-grade {
  font-size: 13px;
  font-weight: 600;
  margin-left: 6px;
}

.score-dims {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.dim {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--aqg-text-secondary);
}

.dim b {
  font-size: 15px;
  color: var(--aqg-text);
}

.dim small {
  color: var(--aqg-text-muted);
  font-size: 11px;
}

.expand-box {
  padding: 6px 16px 12px 46px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.expand-actions {
  display: flex;
  gap: 8px;
}

.assert-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.assert-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.assert-name {
  color: var(--aqg-text);
}

.assert-detail {
  color: var(--aqg-text-muted);
  font-size: 12px;
}

.snap-label {
  font-size: 13px;
  color: var(--aqg-text-secondary);
  margin-bottom: 6px;
}

.run-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.35);
  color: #fca5a5;
  font-size: 13px;
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

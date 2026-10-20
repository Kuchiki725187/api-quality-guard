<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import TestResult from '@/components/TestResult.vue'
import http from '@/api/http'

const apis = ref([])
const selectedApiId = ref(null)
const selectedApi = computed(() => apis.value.find((a) => a.id === selectedApiId.value))

onMounted(async () => {
  try {
    const data = await http.get('/apis', { params: { page: 1, pageSize: 100 } })
    apis.value = data.records
  } catch (e) {
    ElMessage.error(`加载接口列表失败：${e.message}`)
  }
})

// ---------- 规则生成 ----------
const strategyOptions = [
  { label: '正常值', value: 'NORMAL' },
  { label: '必填缺失', value: 'REQUIRED_MISSING' },
  { label: '类型异常', value: 'TYPE_MISMATCH' },
  { label: '边界值', value: 'BOUNDARY' },
]
const strategies = ref(['NORMAL', 'REQUIRED_MISSING', 'TYPE_MISMATCH', 'BOUNDARY'])
const generating = ref(false)
const generatedCases = ref([])

async function generate() {
  if (!selectedApiId.value) {
    ElMessage.warning('请先选择接口')
    return
  }
  if (!strategies.value.length) {
    ElMessage.warning('请至少选择一种生成策略')
    return
  }
  generating.value = true
  try {
    const data = await http.post('/cases/generate', {
      apiId: selectedApiId.value,
      strategies: strategies.value,
    })
    generatedCases.value = data.cases
    ElMessage.success(`生成 ${data.generatedCount} 条用例（重复生成会幂等更新同名人造用例）`)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    generating.value = false
  }
}

// ---------- 立即执行 ----------
const runningId = ref(null)
const runResult = ref(null)
const runError = ref('')
const lastExecutedName = ref('')

async function runCase(row) {
  runningId.value = row.id
  runError.value = ''
  runResult.value = null
  lastExecutedName.value = row.name ?? ''
  try {
    runResult.value = await http.post(`/cases/${row.id}/run`)
  } catch (e) {
    runError.value = e.message
  } finally {
    runningId.value = null
  }
}

function prettyBody(body) {
  if (body === null || body === undefined || body === '') return '（空响应体）'
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return body
  }
}

function prettyJson(value) {
  if (value === null || value === undefined) return '—'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

// ---------- 用例详情 ----------
const detailVisible = ref(false)
const detailCase = ref(null)

function openDetail(row) {
  detailCase.value = row
  detailVisible.value = true
}

function assertionDesc(a) {
  if (a.type === 'STATUS_CODE') return `HTTP 状态码 = ${a.expected}`
  if (a.type === 'JSON_FIELD') return `${a.path} ${a.operator} ${a.expected}`
  if (a.type === 'RESPONSE_TIME') return `响应时间 < ${a.expectedMs} ms`
  return `${a.type}`
}

const fmtVal = (v) => (v === null || v === undefined ? '—' : String(v))

function assertionName(a) {
  if (a.type === 'STATUS_CODE') return 'HTTP 状态码'
  if (a.type === 'JSON_FIELD') return `${a.path} ${a.operator}`
  if (a.type === 'RESPONSE_TIME') return '响应时间（ms）'
  return a.type
}

const viewResult = computed(() => {
  if (!runResult.value) return null
  return {
    status: runResult.value.status,
    durationMs: runResult.value.durationMs ?? 0,
    assertions: (runResult.value.assertionResults ?? []).map((a) => ({
      name: assertionName(a),
      expected: fmtVal(a.expected ?? a.expectedMs),
      actual: fmtVal(a.actual),
      passed: a.passed,
    })),
  }
})

// ---------- 手动创建（弹窗） ----------
const dialogVisible = ref(false)
const saving = ref(false)
const lastSaved = ref(null)
const form = reactive({ name: '', method: 'GET', url: '', params: '' })

const assertions = ref([
  { type: 'STATUS_CODE', expected: 200 },
  { type: 'JSON_FIELD', path: '$.code', operator: 'EQUALS', expected: 0 },
  { type: 'RESPONSE_TIME', operator: 'LESS_THAN', expectedMs: 500 },
])

const operatorOptions = [
  'EQUALS', 'NOT_EQUALS', 'EXISTS', 'NOT_EXISTS', 'CONTAINS', 'GREATER_THAN', 'LESS_THAN',
]

// 选择接口后自动填充：用例名称、请求方法/URL、请求参数（按 schema 生成合法预填值）
watch(selectedApiId, () => {
  if (!selectedApi.value) return
  form.name = `${selectedApi.value.name}-手动用例`
  form.method = selectedApi.value.httpMethod
  form.url = (selectedApi.value.baseUrl ?? '') + selectedApi.value.path
  form.params = JSON.stringify(buildDefaultParams(selectedApi.value), null, 2)
})

function openCreateDialog() {
  if (!selectedApiId.value) {
    ElMessage.warning('请先在上方选择接口')
    return
  }
  dialogVisible.value = true
}

function buildDefaultParams(api) {
  const params = api.requestSchema?.params ?? []
  const result = {}
  for (const p of params) {
    if (p.in === 'body') {
      result.body = buildBodyValue(p.schema)
    } else {
      const group = p.in === 'header' ? 'headers' : p.in
      if (!result[group]) result[group] = {}
      result[group][p.name] = defaultScalarValue(p.type, p.constraints)
    }
  }
  return result
}

function defaultScalarValue(type, constraints) {
  const c = constraints ?? {}
  if (type === 'integer' || type === 'number') {
    const min = c.minimum ?? null
    const max = c.maximum ?? null
    if (min != null && max != null) return min + Math.floor((max - min) / 2)
    if (min != null) return min + 1
    if (max != null) return max - 1
    return 1
  }
  if (type === 'array') return []
  if (type === 'boolean') return true
  if (Array.isArray(c.enum) && c.enum.length) return String(c.enum[0])
  const minLen = c.minLength ?? null
  const maxLen = c.maxLength ?? null
  let len = 8
  if (minLen != null && maxLen != null) len = minLen + Math.floor((maxLen - minLen) / 2)
  else if (minLen != null) len = minLen
  else if (maxLen != null) len = Math.min(maxLen, 8)
  return 'a'.repeat(Math.max(len, 1))
}

function buildBodyValue(schema) {
  if (!schema || schema.type !== 'object' || !schema.properties) return {}
  const body = {}
  for (const [key, field] of Object.entries(schema.properties)) {
    if (field.type === 'object') body[key] = buildBodyValue(field)
    else if (field.type === 'array') body[key] = []
    else body[key] = defaultScalarValue(field.type, field.constraints)
  }
  return body
}

const assertionLabel = (type) =>
  ({ STATUS_CODE: '状态码', JSON_FIELD: 'JSON 字段', RESPONSE_TIME: '响应时间' })[type] ?? type

function addAssertion(type) {
  if (type === 'STATUS_CODE') {
    assertions.value.push({ type, expected: 200 })
  } else if (type === 'JSON_FIELD') {
    assertions.value.push({ type, path: '$.code', operator: 'EQUALS', expected: 0 })
  } else if (type === 'RESPONSE_TIME') {
    assertions.value.push({ type, operator: 'LESS_THAN', expectedMs: 500 })
  }
}

function removeAssertion(index) {
  assertions.value.splice(index, 1)
}

async function saveCase() {
  if (!selectedApiId.value) {
    ElMessage.warning('请先选择接口')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请填写用例名称')
    return
  }
  let requestParams = {}
  if (form.params.trim()) {
    try {
      requestParams = JSON.parse(form.params)
    } catch {
      ElMessage.error('请求参数不是合法的 JSON')
      return
    }
  }
  if (!assertions.value.length) {
    ElMessage.warning('至少保留一条断言')
    return
  }

  saving.value = true
  try {
    const saved = await http.post('/cases', {
      apiId: selectedApiId.value,
      name: form.name,
      requestParams,
      assertionRules: assertions.value,
    })
    ElMessage.success('测试用例已保存')
    lastSaved.value = { id: saved.id, name: form.name }
    dialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="panel">
      <h3 class="panel-title">
        <span>规则生成</span>
        <span class="hint">基于接口约束自动生成用例；每条用例的生成说明可追溯取值依据</span>
      </h3>
      <div class="gen-row">
        <el-select
          v-model="selectedApiId"
          placeholder="选择接口"
          filterable
          style="width: 420px"
        >
          <el-option
            v-for="api in apis"
            :key="api.id"
            :label="`${api.httpMethod} ${api.path}（${api.name}）`"
            :value="api.id"
          />
        </el-select>
        <el-checkbox-group v-model="strategies">
          <el-checkbox v-for="s in strategyOptions" :key="s.value" :value="s.value">
            {{ s.label }}
          </el-checkbox>
        </el-checkbox-group>
        <el-button type="primary" :loading="generating" @click="generate">生成用例</el-button>
        <el-button @click="openCreateDialog">手动新建用例</el-button>
      </div>

      <div v-if="lastSaved" class="last-saved">
        刚刚保存：{{ lastSaved.name }}
        <el-button
          size="small"
          type="success"
          plain
          :loading="runningId === lastSaved.id"
          @click="runCase(lastSaved)"
        >
          执行
        </el-button>
      </div>

      <el-table
        v-if="generatedCases.length"
        :data="generatedCases"
        class="dark-table"
        style="margin-top: 14px"
      >
        <el-table-column prop="name" label="用例名称" min-width="220" />
        <el-table-column prop="generationNote" label="生成说明" min-width="340" />
        <el-table-column label="断言数" width="90">
          <template #default="{ row }">{{ row.assertionRules?.length ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" plain @click="openDetail(row)">详情</el-button>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="runningId === row.id"
              @click="runCase(row)"
            >
              执行
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="runResult || runError" class="panel">
      <h3 class="panel-title">
        <span>最近执行结果</span>
        <span v-if="lastExecutedName" class="hint">用例：{{ lastExecutedName }}</span>
      </h3>
      <div v-if="runError" class="run-error">{{ runError }}</div>
      <template v-else>
        <div class="run-meta">
          <el-tag
            :type="runResult.status === 'PASSED' ? 'success' : runResult.status === 'FAILED' ? 'danger' : 'warning'"
            effect="dark"
          >
            {{ runResult.status }}
          </el-tag>
          <span class="run-duration">耗时 {{ runResult.durationMs }} ms</span>
          <span class="run-url">{{ runResult.requestSnapshot?.url }}</span>
        </div>
        <div v-if="runResult.errorMessage" class="run-error">{{ runResult.errorMessage }}</div>
        <TestResult :result="viewResult" />
        <div class="response-block">
          <div class="response-head">
            <span>响应</span>
            <el-tag v-if="runResult.responseSnapshot" size="small" type="info">
              {{ runResult.responseSnapshot.statusCode }}
            </el-tag>
            <span v-if="runResult.responseSnapshot?.bodyTruncated" class="hint">（响应体超长，已截断）</span>
            <span v-if="!runResult.responseSnapshot" class="hint">网络层失败，无响应</span>
          </div>
          <pre v-if="runResult.responseSnapshot" class="json-view">{{ prettyBody(runResult.responseSnapshot.body) }}</pre>
        </div>
      </template>
    </div>

    <el-dialog v-model="dialogVisible" title="手动创建用例" width="660px">
      <el-form label-width="90px" label-position="left">
        <el-form-item label="接口">
          <el-select v-model="selectedApiId" placeholder="选择接口" filterable style="width: 100%">
            <el-option
              v-for="api in apis"
              :key="api.id"
              :label="`${api.httpMethod} ${api.path}（${api.name}）`"
              :value="api.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用例名称">
          <el-input v-model="form.name" placeholder="选择接口后自动填充，可修改" />
        </el-form-item>
        <el-form-item label="请求">
          <div class="req-row">
            <span :class="'method-tag ' + form.method.toLowerCase()">{{ form.method }}</span>
            <el-input v-model="form.url" placeholder="选择接口后自动填充，可修改" />
          </div>
        </el-form-item>
        <el-form-item label="请求参数">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="7"
            class="mono-input"
            placeholder='按位置分组的 JSON，如：{ "path": { "id": 1 }, "query": {}, "body": { "name": "test" } }'
          />
        </el-form-item>
        <el-form-item label="断言规则">
          <div class="assert-list">
            <div v-for="(a, i) in assertions" :key="i" class="assert-row">
              <el-tag size="small" effect="dark" type="info">{{ assertionLabel(a.type) }}</el-tag>
              <template v-if="a.type === 'STATUS_CODE'">
                <span class="assert-text">HTTP 状态码 =</span>
                <el-input-number v-model="a.expected" :min="100" :max="599" size="small" controls-position="right" />
              </template>
              <template v-else-if="a.type === 'JSON_FIELD'">
                <el-input v-model="a.path" size="small" style="width: 130px" placeholder="$.code" />
                <el-select v-model="a.operator" size="small" style="width: 140px">
                  <el-option v-for="op in operatorOptions" :key="op" :label="op" :value="op" />
                </el-select>
                <el-input v-model="a.expected" size="small" style="width: 110px" placeholder="期望值" />
              </template>
              <template v-else>
                <span class="assert-text">响应时间 &lt;</span>
                <el-input-number v-model="a.expectedMs" :min="1" size="small" controls-position="right" />
                <span class="assert-text">ms</span>
              </template>
              <el-button link type="danger" @click="removeAssertion(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div class="assert-actions">
              <el-button size="small" plain @click="addAssertion('STATUS_CODE')">＋ 状态码断言</el-button>
              <el-button size="small" plain @click="addAssertion('JSON_FIELD')">＋ JSON 字段断言</el-button>
              <el-button size="small" plain @click="addAssertion('RESPONSE_TIME')">＋ 响应时间断言</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCase">保存用例</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="用例详情" width="640px">
      <template v-if="detailCase">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用例名称">{{ detailCase.name }}</el-descriptions-item>
          <el-descriptions-item label="生成方式">{{ detailCase.generationType }}</el-descriptions-item>
          <el-descriptions-item label="生成说明">{{ detailCase.generationNote ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="detail-title">请求参数</h4>
        <pre class="json-view">{{ prettyJson(detailCase.requestParams) }}</pre>
        <h4 class="detail-title">断言规则</h4>
        <div class="assert-descs">
          <div v-for="(a, i) in detailCase.assertionRules" :key="i" class="assert-desc">
            {{ i + 1 }}. {{ assertionDesc(a) }}
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.gen-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.last-saved {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.req-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.mono-input :deep(textarea) {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
}

.assert-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.assert-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assert-text {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.assert-actions {
  display: flex;
  gap: 8px;
}

.run-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.run-duration {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.run-error {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.35);
  color: #fca5a5;
  font-size: 13px;
}

.run-url {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--aqg-text-muted);
  word-break: break-all;
}

.response-block {
  margin-top: 16px;
}

.response-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.response-block pre.json-view {
  max-height: 340px;
}

.detail-title {
  margin: 16px 0 8px;
  font-size: 14px;
  color: var(--aqg-text);
}

.assert-descs {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
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

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    detail.value = await http.get(`/apis/${route.params.id}`)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

const params = computed(() => detail.value?.requestSchema?.params ?? [])

const constraintText = (c) => {
  if (!c) return '—'
  const parts = []
  if (c.minimum != null) parts.push(`min=${c.minimum}`)
  if (c.maximum != null) parts.push(`max=${c.maximum}`)
  if (c.minLength != null) parts.push(`minLength=${c.minLength}`)
  if (c.maxLength != null) parts.push(`maxLength=${c.maxLength}`)
  if (c.pattern) parts.push(`pattern=${c.pattern}`)
  if (c.enum) parts.push(`enum=${[].concat(c.enum).join(' | ')}`)
  return parts.length ? parts.join(', ') : '—'
}

const pretty = (obj) => (obj == null ? '—' : JSON.stringify(obj, null, 2))
const methodClass = (m) => 'method-tag ' + String(m).toLowerCase()
const inType = (i) => (i === 'path' ? 'warning' : i === 'query' ? 'success' : i === 'body' ? 'primary' : 'info')
</script>

<template>
  <div v-loading="loading" class="page">
    <el-alert v-if="error" type="error" show-icon :closable="false" :title="`加载失败：${error}`" />

    <template v-if="detail">
      <div class="panel head-panel">
        <div class="head-left">
          <el-button link @click="router.back()">
            <el-icon><Back /></el-icon>
          </el-button>
          <div>
            <div class="title-row">
              <span class="api-name">{{ detail.name }}</span>
            </div>
            <div class="path-row">
              <span :class="methodClass(detail.httpMethod)">{{ detail.httpMethod }}</span>
              <code class="path">{{ detail.path }}</code>
            </div>
          </div>
        </div>
        <div class="head-right">
          <div class="meta-line">
            <span class="meta-label">服务地址</span>
            <span class="meta-value">{{ detail.baseUrl ?? '未配置' }}</span>
          </div>
          <div class="meta-line">
            <span class="meta-label">更新时间</span>
            <span class="meta-value">{{ detail.updatedAt?.replace('T', ' ').slice(0, 19) }}</span>
          </div>
        </div>
      </div>

      <div class="panel" v-if="detail.description">
        <h3 class="panel-title">接口描述</h3>
        <p class="desc">{{ detail.description }}</p>
      </div>

      <div class="panel">
        <h3 class="panel-title">
          <span>请求参数</span>
          <span class="sub">共 {{ params.length }} 个，约束信息供用例生成使用</span>
        </h3>
        <el-table :data="params" class="param-table">
          <el-table-column prop="name" label="参数名" min-width="130">
            <template #default="{ row }">
              <code class="param-name">{{ row.name }}</code>
            </template>
          </el-table-column>
          <el-table-column label="位置" width="100">
            <template #default="{ row }">
              <el-tag :type="inType(row.in)" size="small" effect="dark">{{ row.in }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column label="必填" width="80">
            <template #default="{ row }">
              <el-tag :type="row.required ? 'danger' : 'info'" size="small" effect="plain">
                {{ row.required ? '必填' : '可选' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="约束" min-width="220">
            <template #default="{ row }">
              <span class="constraint">{{ constraintText(row.constraints) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="160">
            <template #default="{ row }">
              <span class="param-desc">{{ row.description ?? '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="grid-2">
        <div class="panel">
          <h3 class="panel-title">请求结构（body schema）</h3>
          <pre class="json-view">{{ pretty(detail.requestSchema?.params?.find((p) => p.in === 'body')?.schema) }}</pre>
        </div>
        <div class="panel">
          <h3 class="panel-title">
            <span>响应结构</span>
            <span class="sub">成功状态码 {{ detail.responseSchema?.successStatusCode ?? 200 }}</span>
          </h3>
          <pre class="json-view">{{ pretty(detail.responseSchema?.schema) }}</pre>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.api-name {
  font-size: 18px;
  font-weight: 700;
}

.path-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.path {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
  color: #93c5fd;
}

.head-right {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: right;
}

.meta-line {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  font-size: 13px;
}

.meta-label {
  color: var(--aqg-text-muted);
}

.meta-value {
  color: var(--aqg-text-secondary);
  font-family: ui-monospace, Consolas, monospace;
}

.desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--aqg-text-secondary);
}

.sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.param-name {
  font-family: ui-monospace, Consolas, monospace;
  color: #93c5fd;
}

.constraint {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: #fbbf24;
}

.param-desc {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.param-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--aqg-panel);
  --el-table-header-text-color: var(--aqg-text-secondary);
  --el-table-border-color: var(--aqg-border);
  --el-table-row-hover-bg-color: #273449;
}
</style>

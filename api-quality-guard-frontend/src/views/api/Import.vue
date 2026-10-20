<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const router = useRouter()

const phase = ref('idle')
const fileName = ref('')
const result = ref(null)

const methodStats = computed(() => {
  const counts = result.value?.methodCounts ?? {}
  return [
    { label: '接口数量', value: result.value?.totalParsed ?? 0, color: '#3b82f6' },
    { label: 'GET', value: counts.GET ?? 0, color: '#10b981' },
    { label: 'POST', value: counts.POST ?? 0, color: '#60a5fa' },
    { label: 'PUT', value: counts.PUT ?? 0, color: '#f59e0b' },
    { label: 'DELETE', value: counts.DELETE ?? 0, color: '#f87171' },
  ]
})

async function doUpload({ file }) {
  fileName.value = file.name
  phase.value = 'uploading'
  result.value = null
  const formData = new FormData()
  formData.append('file', file)
  try {
    result.value = await http.post('/apis/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    })
    phase.value = 'done'
    const parts = [`新增 ${result.value.imported}`]
    if (result.value.updated) parts.push(`更新 ${result.value.updated}`)
    if (result.value.failures.length) parts.push(`失败 ${result.value.failures.length}`)
    ElMessage.success(`导入完成：${parts.join(' / ')} 个接口`)
  } catch (e) {
    phase.value = 'idle'
    ElMessage.error(e.message)
  }
}

function reset() {
  phase.value = 'idle'
  fileName.value = ''
  result.value = null
}
</script>

<template>
  <div class="page">
    <div class="panel">
      <h3 class="panel-title">上传 OpenAPI / Swagger 文档并导入</h3>

      <el-upload
        v-if="phase === 'idle'"
        drag
        class="uploader"
        accept=".json,.yaml,.yml"
        :show-file-list="false"
        :http-request="doUpload"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将 swagger.json / openapi.yaml 拖到此处，或点击选择文件</div>
        <div class="upload-hint">支持 OpenAPI 3.x 与 Swagger 2.0，选择文件后自动解析并导入</div>
      </el-upload>

      <div v-else-if="phase === 'uploading'" class="parsing">
        <el-icon class="is-loading parse-icon"><Loading /></el-icon>
        <div class="parse-text">正在解析并导入 {{ fileName }} …</div>
      </div>

      <template v-else>
        <div class="parsed-head">
          <el-icon color="#10b981" :size="18"><CircleCheck /></el-icon>
          <span>
            导入完成：{{ result.documentTitle ?? fileName }}
            <span v-if="result.documentVersion" class="doc-version">v{{ result.documentVersion }}</span>
          </span>
          <el-button link type="info" @click="reset">继续导入</el-button>
        </div>

        <div class="stat-grid">
          <div v-for="s in methodStats" :key="s.label" class="stat-card">
            <span class="stat-value" :style="{ color: s.color }">{{ s.value }}</span>
            <span class="stat-label">{{ s.label }}</span>
          </div>
        </div>

        <div class="result-line">
          <span>新增 <b class="ok">{{ result.imported }}</b> 个</span>
          <span v-if="result.updated">更新 <b class="upd">{{ result.updated }}</b> 个</span>
          <span v-if="result.failures.length" class="fail">
            失败 {{ result.failures.length }} 个
          </span>
        </div>

        <el-table v-if="result.failures.length" :data="result.failures" class="fail-table" size="small">
          <el-table-column prop="path" label="接口" min-width="240" />
          <el-table-column prop="reason" label="失败原因" min-width="260" />
        </el-table>

        <div class="import-actions">
          <el-button type="primary" @click="router.push('/api/list')">查看接口列表</el-button>
          <el-button @click="reset">再导入一份</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.uploader :deep(.el-upload-dragger) {
  background: var(--aqg-panel);
  border: 1px dashed var(--aqg-border);
  border-radius: 10px;
  padding: 46px 0;
  transition: border-color 0.2s;
}

.uploader :deep(.el-upload-dragger:hover) {
  border-color: var(--aqg-blue);
}

.upload-icon {
  font-size: 46px;
  color: var(--aqg-blue);
}

.upload-text {
  margin-top: 10px;
  font-size: 14px;
  color: var(--aqg-text);
}

.upload-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.parsing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 56px 0;
  color: var(--aqg-text-secondary);
}

.parse-icon {
  font-size: 34px;
  color: var(--aqg-blue);
}

.parse-text {
  font-size: 14px;
}

.parsed-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  margin-bottom: 18px;
}

.doc-version {
  margin-left: 6px;
  color: var(--aqg-text-muted);
  font-size: 12px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 0;
  border: 1px solid var(--aqg-border);
  border-radius: 10px;
  background: var(--aqg-panel);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
}

.stat-label {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.result-line {
  display: flex;
  gap: 18px;
  margin-top: 18px;
  font-size: 14px;
  color: var(--aqg-text-secondary);
}

.ok {
  color: var(--aqg-green);
}

.upd {
  color: var(--aqg-blue);
}

.fail {
  color: var(--aqg-red);
}

.fail-table {
  margin-top: 14px;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--aqg-panel);
  --el-table-header-text-color: var(--aqg-text-secondary);
  --el-table-border-color: var(--aqg-border);
}

.import-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 22px;
}
</style>

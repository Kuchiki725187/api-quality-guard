<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AiReport from '@/components/AiReport.vue'
import http from '@/api/http'

const route = useRoute()

const records = ref([])
const total = ref(0)
const loading = ref(false)
const selected = ref(null)
const query = reactive({ page: 1, pageSize: 10 })
const feedbackForm = reactive({ feedback: '', comment: '' })
const submitting = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await http.get('/diagnoses', {
      params: { page: query.page, pageSize: query.pageSize },
    })
    records.value = data.records
    total.value = data.total
    const targetId = route.query.diagnosisId ? Number(route.query.diagnosisId) : null
    let target = targetId ? records.value.find((r) => r.id === targetId) : null
    if (!target && !selected.value && records.value.length) {
      target = records.value[0]
    }
    if (target) {
      select(target)
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function select(record) {
  selected.value = record
  feedbackForm.feedback = record.humanFeedback || ''
  feedbackForm.comment = record.humanComment || ''
}

async function submitFeedback() {
  if (!selected.value) return
  if (!feedbackForm.feedback) {
    ElMessage.warning('请选择反馈类型')
    return
  }
  submitting.value = true
  try {
    await http.put(`/diagnoses/${selected.value.id}/feedback`, {
      feedback: feedbackForm.feedback,
      comment: feedbackForm.comment || undefined,
    })
    ElMessage.success('反馈已提交')
    selected.value.humanFeedback = feedbackForm.feedback
    selected.value.humanComment = feedbackForm.comment
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 19) : '—')
const feedbackLabel = (f) =>
  ({ CONFIRMED: '已确认', REJECTED: '已否定', REVISED: '已修正' })[f] ?? f
const feedbackType = (f) =>
  ({ CONFIRMED: 'success', REJECTED: 'danger', REVISED: 'warning' })[f] ?? 'info'
</script>

<template>
  <div class="page">
    <div class="panel head-panel">
      <div class="ai-title">
        <el-icon color="#8b5cf6" :size="20"><MagicStick /></el-icon>
        <span>AI 质量分析报告</span>
        <el-tag effect="plain" size="small">由独立诊断 Agent 生成 · 仅提供建议，不影响测试判定</el-tag>
      </div>
    </div>

    <div class="report-layout">
      <div class="panel list-panel">
        <h3 class="panel-title"><span>诊断记录</span></h3>
        <div v-loading="loading" class="diag-list">
          <div
            v-for="r in records"
            :key="r.id"
            class="diag-item"
            :class="{ active: selected?.id === r.id }"
            @click="select(r)"
          >
            <div class="diag-top">
              <span class="diag-case">{{ r.caseName || '未知用例' }}</span>
              <el-tag v-if="r.humanFeedback" size="small" :type="feedbackType(r.humanFeedback)">
                {{ feedbackLabel(r.humanFeedback) }}
              </el-tag>
            </div>
            <div class="diag-meta">
              #{{ r.id }} · {{ r.modelName || '—' }} · {{ fmtTime(r.createdAt) }}
            </div>
          </div>
          <el-empty
            v-if="!loading && !records.length"
            description="暂无诊断记录：请到执行记录页对失败结果发起 AI 诊断"
            :image-size="80"
          />
        </div>
        <div class="pager" v-if="total > query.pageSize">
          <el-pagination
            v-model:current-page="query.page"
            :page-size="query.pageSize"
            :total="total"
            layout="prev, pager, next"
            @current-change="load"
          />
        </div>
      </div>

      <div class="panel detail-panel" v-if="selected">
        <AiReport :diag="selected" />

        <div class="feedback-block">
          <div class="feedback-title">人工反馈（用于诊断准确率统计与优化）</div>
          <div class="feedback-row">
            <el-radio-group v-model="feedbackForm.feedback">
              <el-radio value="CONFIRMED">确认准确</el-radio>
              <el-radio value="REJECTED">诊断有误</el-radio>
              <el-radio value="REVISED">部分修正</el-radio>
            </el-radio-group>
            <el-button type="primary" :loading="submitting" @click="submitFeedback">
              提交反馈
            </el-button>
          </div>
          <el-input
            v-model="feedbackForm.comment"
            type="textarea"
            :rows="2"
            maxlength="1000"
            placeholder="补充说明（可选）"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
}

.report-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .report-layout {
    grid-template-columns: 1fr;
  }
}

.diag-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
}

.diag-item {
  padding: 10px 12px;
  border: 1px solid var(--aqg-border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.diag-item:hover {
  border-color: #475569;
}

.diag-item.active {
  border-color: rgba(139, 92, 246, 0.6);
  background: rgba(139, 92, 246, 0.08);
}

.diag-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.diag-case {
  font-size: 13.5px;
  font-weight: 600;
}

.diag-meta {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--aqg-text-muted);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feedback-block {
  border-top: 1px solid var(--aqg-border);
  padding-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feedback-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--aqg-text-secondary);
}

.feedback-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
</style>

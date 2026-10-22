<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const shortcuts = ['为什么接口评分下降？', '哪些接口存在风险？', '如何优化测试覆盖率？']
const question = ref('')
const askedQuestion = ref('')

function ask(text) {
  const q = (text ?? question.value).trim()
  if (!q) return
  askedQuestion.value = q
  question.value = q
}
</script>

<template>
  <div class="page">
    <div class="panel">
      <h3 class="panel-title">
        <span>AI 辅助分析</span>
        <el-tag effect="plain" size="small" type="warning">自然语言问答规划中</el-tag>
      </h3>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="当前版本的 AI 能力聚焦「失败测试结果的故障诊断」：请到执行记录页对 FAILED / ERROR 结果发起诊断，报告可在「AI 质量报告」页查看与反馈。"
      />

      <div class="shortcut-row">
        <el-button v-for="q in shortcuts" :key="q" plain @click="ask(q)">{{ q }}</el-button>
      </div>

      <div class="input-row">
        <el-input
          v-model="question"
          placeholder="输入你想了解的问题（当前版本将给出功能引导）"
          @keyup.enter="ask()"
        />
        <el-button type="primary" @click="ask()">发送</el-button>
      </div>

      <div v-if="askedQuestion" class="guide-card">
        <div class="guide-title">关于「{{ askedQuestion }}」</div>
        <p class="guide-text">自然语言问答能力尚未上线。当前可以使用的 AI 能力：</p>
        <ul class="guide-list">
          <li>在执行记录的失败结果上发起 <b>AI 故障诊断</b>（Agent 会自动调用工具取证）</li>
          <li>在 AI 质量报告中查看结构化诊断（候选原因 / 置信度 / 证据 / 建议）</li>
          <li>对诊断结果提交人工反馈，用于准确率统计与优化</li>
        </ul>
        <div class="guide-actions">
          <el-button type="primary" @click="router.push('/test/records')">去发起诊断</el-button>
          <el-button @click="router.push('/ai/report')">查看诊断报告</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shortcut-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin: 16px 0 12px;
}

.input-row {
  display: flex;
  gap: 10px;
  max-width: 640px;
}

.guide-card {
  margin-top: 18px;
  padding: 16px 18px;
  border-radius: 10px;
  border: 1px solid rgba(139, 92, 246, 0.35);
  background: rgba(139, 92, 246, 0.08);
}

.guide-title {
  font-size: 14px;
  font-weight: 700;
  color: #c4b5fd;
}

.guide-text {
  margin: 10px 0 6px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.guide-list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--aqg-text);
}

.guide-actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}
</style>

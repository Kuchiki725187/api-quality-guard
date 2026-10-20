<script setup>
import { ref } from 'vue'
import { assistantAnswers, assistantShortcuts } from '@/mock/data'

const question = ref('')
const current = ref(null)
const asking = ref(false)

function ask(q) {
  const text = (q ?? question.value).trim()
  if (!text) return
  question.value = text
  asking.value = true
  current.value = null
  setTimeout(() => {
    asking.value = false
    current.value = assistantAnswers[text] ?? {
      conclusion: `已收到问题「${text}」`,
      points: [
        '当前平台质量数据整体平稳，近 7 天无新增高风险接口',
        '订单模块通过率存在小幅波动，已低于平台均值 6 个百分点',
      ],
      advice: ['可在质量评分页查看维度明细定位短板', '对波动接口执行一次回归测试以确认稳定性'],
    }
  }, 800)
}
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="assistant-head">
        <el-icon color="#8b5cf6" :size="22"><MagicStick /></el-icon>
        <div>
          <div class="assistant-title">AI 辅助分析</div>
          <div class="assistant-sub">基于执行记录与质量数据回答你的问题</div>
        </div>
      </div>

      <div class="shortcuts">
        <el-button
          v-for="s in assistantShortcuts"
          :key="s"
          round
          size="small"
          class="shortcut"
          @click="ask(s)"
        >
          {{ s }}
        </el-button>
      </div>

      <div class="ask-row">
        <el-input
          v-model="question"
          placeholder="输入你的问题，例如：为什么接口评分下降？"
          @keyup.enter="ask()"
        />
        <el-button type="primary" :loading="asking" @click="ask()">
          <el-icon><Promotion /></el-icon>
          <span>分析</span>
        </el-button>
      </div>
    </div>

    <div v-if="asking" class="panel loading-panel">
      <el-icon class="is-loading" :size="26" color="#8b5cf6"><Loading /></el-icon>
      <span>AI 正在分析平台质量数据…</span>
    </div>

    <div v-else-if="current" class="panel result-panel">
      <div class="section">
        <div class="section-title">结论</div>
        <div class="conclusion">{{ current.conclusion }}</div>
      </div>

      <div class="section">
        <div class="section-title">分析要点</div>
        <ul class="point-list">
          <li v-for="(p, i) in current.points" :key="i">{{ p }}</li>
        </ul>
      </div>

      <div class="section advice">
        <div class="section-title ai-color">
          <el-icon><MagicStick /></el-icon>
          <span>建议</span>
        </div>
        <ul class="point-list">
          <li v-for="(a, i) in current.advice" :key="i">{{ a }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.assistant-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.assistant-title {
  font-size: 16px;
  font-weight: 700;
}

.assistant-sub {
  font-size: 12px;
  color: var(--aqg-text-muted);
  margin-top: 2px;
}

.shortcuts {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.shortcut {
  border-color: rgba(139, 92, 246, 0.4);
  color: var(--aqg-purple);
  background: rgba(139, 92, 246, 0.08);
}

.ask-row {
  display: flex;
  gap: 12px;
}

.loading-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--aqg-text-secondary);
  font-size: 14px;
}

.result-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
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

.ai-color {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--aqg-purple);
}

.conclusion {
  padding: 14px 16px;
  border-radius: 8px;
  border-left: 3px solid var(--aqg-purple);
  background: var(--aqg-panel);
  font-size: 14.5px;
  line-height: 1.7;
}

.point-list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--aqg-text);
}

.advice {
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid rgba(139, 92, 246, 0.35);
  background: rgba(139, 92, 246, 0.08);
}
</style>

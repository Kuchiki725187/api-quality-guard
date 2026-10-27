<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { renderMarkdown } from '@/utils/markdown'

const auth = useAuthStore()

const shortcuts = [
  '当前平台整体质量怎么样？主要短板是什么？',
  '哪些接口风险最高？为什么？',
  '最近一次执行失败的主要原因是什么？',
  '最近有哪些测试失败了，应该怎么修复？',
]

const messages = ref([])
const input = ref('')
const sending = ref(false)
const loadingHistory = ref(false)
const clearing = ref(false)
const historyWarning = ref('')
const listRef = ref(null)

const userInitial = computed(() =>
  (auth.user?.displayName || auth.user?.username || '我').charAt(0).toUpperCase(),
)

const canSend = computed(() => !sending.value && input.value.trim().length > 0)

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = listRef.value
    if (!el) return
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
    if (force || nearBottom) {
      el.scrollTop = el.scrollHeight
    }
  })
}

async function loadHistory() {
  loadingHistory.value = true
  historyWarning.value = ''
  try {
    const data = await http.get('/assistant/history')
    messages.value = (data || []).map((message) => ({
      role: message.role,
      content: message.content,
      createdAt: message.createdAt,
    }))
    scrollToBottom(true)
  } catch (e) {
    historyWarning.value = e.message
  } finally {
    loadingHistory.value = false
  }
}

onMounted(loadHistory)

function onKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey) return
  if (event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  send()
}

async function send(text) {
  if (sending.value) return
  const question = (text ?? input.value).trim()
  if (!question) return
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  scrollToBottom(true)
  sending.value = true
  try {
    const data = await http.post(
      '/assistant/chat',
      { message: question },
      { timeout: 150000 },
    )
    messages.value.push({
      role: 'assistant',
      content: data.answer,
      meta: formatMeta(data),
    })
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: `请求失败：${e.message}`,
      error: true,
    })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function formatMeta(data) {
  return data.durationMs ? `耗时 ${(data.durationMs / 1000).toFixed(1)}s` : ''
}

async function clearHistory() {
  if (sending.value || clearing.value) return
  try {
    await ElMessageBox.confirm('清空后当前对话历史不可恢复，确定继续吗？', '清空对话', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  clearing.value = true
  try {
    await http.delete('/assistant/history')
    messages.value = []
    ElMessage.success('对话已清空')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    clearing.value = false
  }
}

const fmtTime = (t) => (t ? t.replace('T', ' ').slice(11, 16) : '')
</script>

<template>
  <div class="assistant-page">
    <div class="panel chat-panel">
      <div class="chat-header">
        <div>
          <div class="chat-title">
            AI 智能问答
            <el-tag type="success" size="small" effect="plain">只读分析</el-tag>
          </div>
          <div class="chat-sub">查询平台质量、风险接口、执行结果与失败原因，回答基于真实数据</div>
        </div>
        <el-button
          text
          :disabled="sending || clearing || !messages.length"
          :loading="clearing"
          @click="clearHistory"
        >
          <el-icon><Delete /></el-icon>
          <span>清空对话</span>
        </el-button>
      </div>

      <el-alert
        v-if="historyWarning"
        type="warning"
        show-icon
        :closable="false"
        class="history-alert"
        :title="`历史对话加载失败：${historyWarning}（不影响本次问答）`"
      />

      <div ref="listRef" v-loading="loadingHistory" class="message-list">
        <div v-if="!messages.length && !loadingHistory" class="welcome">
          <div class="welcome-icon">
            <el-icon :size="26"><MagicStick /></el-icon>
          </div>
          <h2>有什么可以帮你？</h2>
          <p>我会先查询平台真实数据，再给出结论和修复建议。</p>
          <div class="shortcut-grid">
            <button
              v-for="question in shortcuts"
              :key="question"
              type="button"
              class="shortcut"
              :disabled="sending"
              @click="send(question)"
            >
              <el-icon><ChatDotRound /></el-icon>
              <span>{{ question }}</span>
            </button>
          </div>
        </div>

        <template v-for="(message, index) in messages" :key="index">
          <div class="msg-row" :class="message.role">
            <div v-if="message.role === 'assistant'" class="avatar assistant-avatar">AI</div>
            <div class="bubble-wrap">
              <div class="bubble" :class="{ error: message.error }">
                <div
                  v-if="message.role === 'assistant' && !message.error"
                  class="md-body"
                  v-html="renderMarkdown(message.content)"
                ></div>
                <div v-else class="plain-body">{{ message.content }}</div>
              </div>
              <div v-if="message.meta || message.createdAt" class="msg-meta">
                <span v-if="message.createdAt">{{ fmtTime(message.createdAt) }}</span>
                <span v-if="message.meta">{{ message.meta }}</span>
              </div>
            </div>
            <div v-if="message.role === 'user'" class="avatar user-avatar">{{ userInitial }}</div>
          </div>
        </template>

        <div v-if="sending" class="msg-row assistant">
          <div class="avatar assistant-avatar">AI</div>
          <div class="bubble typing">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="input"
          type="textarea"
          resize="none"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入问题，例如：哪些接口风险最高？"
          :disabled="sending"
          @keydown="onKeydown"
        />
        <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send()">
          <el-icon v-if="!sending"><Promotion /></el-icon>
          <span>{{ sending ? '分析中' : '发送' }}</span>
        </el-button>
      </div>
      <div class="composer-hint">Enter 发送 · Shift+Enter 换行 · 回答基于平台近 14 天数据</div>
    </div>
  </div>
</template>

<style scoped>
.assistant-page {
  height: calc(100vh - 100px);
}

.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--aqg-border);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
}

.chat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--aqg-text-muted);
}

.history-alert {
  margin: 10px 20px 0;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome {
  margin: auto;
  max-width: 680px;
  width: 100%;
  text-align: center;
  padding: 24px 0;
}

.welcome-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(139, 92, 246, 0.2));
  color: #a5b4fc;
  margin-bottom: 14px;
}

.welcome h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.welcome p {
  margin: 0;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.shortcut-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 20px;
}

.shortcut {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--aqg-border);
  background: var(--aqg-panel);
  color: var(--aqg-text-secondary);
  font-size: 12.5px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}

.shortcut:hover:not(:disabled) {
  border-color: var(--aqg-blue);
  color: var(--aqg-text);
}

.shortcut:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.msg-row.user {
  justify-content: flex-end;
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.assistant-avatar {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
}

.user-avatar {
  background: #334155;
  color: #e2e8f0;
}

.bubble-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: min(76%, 760px);
  min-width: 0;
}

.msg-row.user .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--aqg-border);
  background: var(--aqg-card);
  color: var(--aqg-text);
  font-size: 13.5px;
  line-height: 1.7;
  word-break: break-word;
}

.msg-row.user .bubble {
  background: rgba(59, 130, 246, 0.16);
  border-color: rgba(59, 130, 246, 0.35);
}

.bubble.error {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.4);
  color: #fca5a5;
}

.plain-body {
  white-space: pre-wrap;
}

.msg-meta {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: var(--aqg-text-muted);
}

.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 38px;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  animation: typing-blink 1.2s infinite;
}

.typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing-blink {
  0%, 80%, 100% { opacity: 0.25; }
  40% { opacity: 1; }
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 20px 4px;
  border-top: 1px solid var(--aqg-border);
}

.composer-hint {
  padding: 0 20px 12px;
  font-size: 11px;
  color: var(--aqg-text-muted);
  text-align: right;
}

.md-body :deep(p) {
  margin: 0 0 8px;
}

.md-body :deep(p:last-child) {
  margin-bottom: 0;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  margin: 12px 0 6px;
  font-size: 15px;
  line-height: 1.4;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 6px 0;
  padding-left: 20px;
}

.md-body :deep(li) {
  margin: 2px 0;
}

.md-body :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(15, 23, 42, 0.8);
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12.5px;
}

.md-body :deep(pre) {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--aqg-border);
  background: var(--aqg-panel);
  overflow-x: auto;
}

.md-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

.md-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}

.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid var(--aqg-border);
  padding: 6px 8px;
  font-size: 12.5px;
  text-align: left;
}

.md-body :deep(a) {
  color: var(--aqg-blue);
}

.md-body :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid var(--aqg-blue);
  color: var(--aqg-text-secondary);
}

@media (max-width: 900px) {
  .shortcut-grid {
    grid-template-columns: 1fr;
  }

  .bubble-wrap {
    max-width: 88%;
  }
}
</style>

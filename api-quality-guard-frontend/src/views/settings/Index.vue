<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const health = ref(null)
const healthError = ref('')
const checking = ref(false)

async function checkHealth() {
  checking.value = true
  healthError.value = ''
  try {
    health.value = await http.get('/health')
  } catch (e) {
    health.value = null
    healthError.value = e.message
  } finally {
    checking.value = false
  }
}

onMounted(checkHealth)
</script>

<template>
  <div class="page">
    <div class="panel">
      <h3 class="panel-title">
        <span>平台信息</span>
        <el-button link type="primary" :loading="checking" @click="checkHealth">刷新状态</el-button>
      </h3>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="平台版本">0.1.0</el-descriptions-item>
        <el-descriptions-item label="服务状态">
          <el-tag
            v-if="health"
            :type="health.status === 'UP' ? 'success' : 'warning'"
            size="small"
            effect="dark"
          >
            {{ health.status }}
          </el-tag>
          <el-tag v-else type="danger" size="small" effect="dark">未连接</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据库">
          <el-tag
            v-if="health"
            :type="health.database === 'UP' ? 'success' : 'danger'"
            size="small"
            effect="dark"
          >
            {{ health.database }}
          </el-tag>
          <el-tag v-else type="danger" size="small" effect="dark">未知</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="healthError"
        type="warning"
        show-icon
        :closable="false"
        :title="`后端未连接：${healthError}（启动后端后点击刷新）`"
        class="health-alert"
      />
    </div>
  </div>
</template>

<style scoped>
.health-alert {
  margin-top: 14px;
}
</style>

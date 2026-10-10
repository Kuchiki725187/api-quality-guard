<script setup>
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useHealthStore } from '@/stores/health'

const healthStore = useHealthStore()
const { info, loading, error } = storeToRefs(healthStore)

onMounted(() => {
  healthStore.fetchHealth()
})
</script>

<template>
  <div class="page">
    <el-card class="card">
      <template #header>
        <div class="header">
          <h1>API Quality Guard</h1>
          <span class="subtitle">API 智能质量守护平台</span>
        </div>
      </template>

      <el-descriptions v-if="info" :column="1" border>
        <el-descriptions-item label="后端服务">
          <el-tag :type="info.status === 'UP' ? 'success' : 'warning'">
            {{ info.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据库">
          <el-tag :type="info.database === 'UP' ? 'success' : 'danger'">
            {{ info.database }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检查时间">
          {{ info.timestamp }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="error"
        type="error"
        show-icon
        :closable="false"
        :title="`后端连接失败：${error}`"
      />

      <div class="actions">
        <el-button type="primary" :loading="loading" @click="healthStore.fetchHealth()">
          刷新状态
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card {
  width: 560px;
}

.header h1 {
  margin: 0;
  font-size: 22px;
}

.subtitle {
  color: #909399;
  font-size: 13px;
}

.actions {
  margin-top: 16px;
  text-align: right;
}
</style>

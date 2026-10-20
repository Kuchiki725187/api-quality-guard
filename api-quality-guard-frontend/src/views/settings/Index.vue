<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchHealth } from '@/mock/data'

const modelForm = reactive({
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  modelName: 'qwen-plus',
  apiKey: '',
})

const health = ref(null)
const healthError = ref('')
const checking = ref(false)

async function checkHealth() {
  checking.value = true
  healthError.value = ''
  try {
    health.value = await fetchHealth()
  } catch (e) {
    health.value = null
    healthError.value = e.message
  } finally {
    checking.value = false
  }
}

function saveModel() {
  ElMessage.success('已保存（密钥实际通过服务端环境变量管理，不会保存在前端）')
}

onMounted(checkHealth)
</script>

<template>
  <div class="page">
    <div class="grid-2">
      <div class="panel">
        <h3 class="panel-title">大模型配置</h3>
        <el-form label-width="90px">
          <el-form-item label="模型地址">
            <el-input v-model="modelForm.baseUrl" />
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="modelForm.modelName" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="modelForm.apiKey" type="password" show-password placeholder="通过环境变量注入，此处仅演示" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveModel">保存配置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="panel">
        <h3 class="panel-title">
          <span>平台信息</span>
          <el-button link type="primary" :loading="checking" @click="checkHealth">刷新状态</el-button>
        </h3>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="平台版本">0.1.0</el-descriptions-item>
          <el-descriptions-item label="后端地址">http://localhost:8080（经代理转发）</el-descriptions-item>
          <el-descriptions-item label="后端服务">
            <el-tag v-if="health" :type="health.status === 'UP' ? 'success' : 'warning'" size="small" effect="dark">
              {{ health.status }}
            </el-tag>
            <el-tag v-else type="danger" size="small" effect="dark">未连接</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="数据库">
            <el-tag v-if="health" :type="health.database === 'UP' ? 'success' : 'danger'" size="small" effect="dark">
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
  </div>
</template>

<style scoped>
.health-alert {
  margin-top: 14px;
}
</style>

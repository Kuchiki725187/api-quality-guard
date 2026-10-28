<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AuthShell from '@/views/auth/AuthShell.vue'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{4,32}$/,
      message: '用户名为 4-32 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度为 8-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function submit() {
  if (loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await http.post('/auth/register', {
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
    })
    ElMessage.success('注册成功，请登录')
    await router.replace({ path: '/login', query: { username: form.username.trim() } })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="注册账号" subtitle="注册后即可使用平台全部测试能力">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
      <el-form-item prop="username">
        <el-input v-model="form.username" size="large" placeholder="用户名（4-32 位字母、数字或下划线）" />
      </el-form-item>
      <el-form-item prop="displayName">
        <el-input v-model="form.displayName" size="large" placeholder="显示名称" maxlength="50" />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          size="large"
          type="password"
          show-password
          placeholder="密码（8-32 位）"
        />
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          size="large"
          type="password"
          show-password
          placeholder="确认密码"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-button type="primary" size="large" class="full-btn" :loading="loading" @click="submit">
        注册
      </el-button>
    </el-form>

    <template #footer>
      已有账号？<router-link to="/login">返回登录</router-link>
    </template>
  </AuthShell>
</template>

<style scoped>
.full-btn {
  width: 100%;
  margin-top: 4px;
}
</style>

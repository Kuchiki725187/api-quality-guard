<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, keyword: '' })

async function load() {
  loading.value = true
  try {
    const data = await http.get('/admin/users', {
      params: {
        page: query.page,
        pageSize: query.pageSize,
        keyword: query.keyword.trim() || undefined,
      },
    })
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

onMounted(load)

async function toggleStatus(row) {
  const next = !row.enabled
  if (!next) {
    try {
      await ElMessageBox.confirm(
        `禁用后「${row.username}」的登录会话会立即失效，确定继续吗？`,
        '禁用用户',
        { type: 'warning', confirmButtonText: '禁用', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  try {
    await http.put(`/admin/users/${row.id}/status`, { enabled: next })
    ElMessage.success(next ? '已启用' : '已禁用')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const resetVisible = ref(false)
const resetRow = ref(null)
const resetting = ref(false)
const resetForm = reactive({ newPassword: '', confirmPassword: '' })

function openReset(row) {
  resetRow.value = row
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetVisible.value = true
}

async function submitReset() {
  if (resetForm.newPassword.length < 8 || resetForm.newPassword.length > 32) {
    ElMessage.warning('密码长度需为 8-32 位')
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  resetting.value = true
  try {
    await http.put(`/admin/users/${resetRow.value.id}/password`, {
      newPassword: resetForm.newPassword,
    })
    ElMessage.success('密码已重置')
    resetVisible.value = false
    if (isSelf(resetRow.value)) {
      ElMessage.info('当前账号密码已重置，请重新登录')
      auth.clear()
      router.replace('/login')
      return
    }
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    resetting.value = false
  }
}

const isSelf = (row) => row.id === auth.user?.id
const roleLabel = (role) => (role === 'ADMIN' ? '管理员' : '普通用户')
const fmtTime = (t) => (t ? t.replace('T', ' ').slice(0, 19) : '—')
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="toolbar">
        <div class="toolbar-title">
          用户管理
          <span class="hint">禁用或重置密码会立即失效该用户的登录会话</span>
        </div>
        <div class="toolbar-actions">
          <el-input
            v-model="query.keyword"
            placeholder="搜索用户名 / 显示名称"
            clearable
            style="width: 240px"
            @keyup.enter="search"
            @clear="search"
          />
          <el-button type="primary" @click="search">搜索</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" class="dark-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户名" min-width="170">
          <template #default="{ row }">
            <span>{{ row.username }}</span>
            <el-tag v-if="isSelf(row)" size="small" type="info" class="self-tag">当前账号</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.role === 'ADMIN' ? 'warning' : 'info'"
              size="small"
              effect="dark"
            >
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.enabled ? 'danger' : 'success'"
              plain
              :disabled="isSelf(row)"
              @click="toggleStatus(row)"
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" plain @click="openReset(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="resetVisible" title="重置密码" width="420px">
      <div class="reset-target">用户：{{ resetRow?.username }}</div>
      <el-form label-width="90px" label-position="left">
        <el-form-item label="新密码" required>
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            show-password
            placeholder="8-32 位"
          />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input v-model="resetForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="submitReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.self-tag {
  margin-left: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.reset-target {
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.dark-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--aqg-panel);
  --el-table-header-text-color: var(--aqg-text-secondary);
  --el-table-border-color: var(--aqg-border);
  --el-table-row-hover-bg-color: #273449;
}
</style>

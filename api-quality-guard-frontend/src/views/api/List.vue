<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const router = useRouter()

const query = reactive({ page: 1, pageSize: 10, name: '', httpMethod: '' })
const records = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await http.get('/apis', {
      params: {
        page: query.page,
        pageSize: query.pageSize,
        name: query.name || undefined,
        httpMethod: query.httpMethod || undefined,
      },
    })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

const methodClass = (m) => 'method-tag ' + String(m).toLowerCase()

// ---------- 新建 / 编辑接口 ----------
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const submitting = ref(false)
const apiForm = reactive({
  name: '',
  httpMethod: 'GET',
  path: '',
  baseUrl: '',
  description: '',
})

function fillForm(row = null) {
  Object.assign(apiForm, {
    name: row?.name ?? '',
    httpMethod: row?.httpMethod ?? 'GET',
    path: row?.path ?? '',
    baseUrl: row?.baseUrl ?? '',
    description: row?.description ?? '',
  })
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  fillForm()
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  fillForm(row)
  dialogVisible.value = true
}

async function submitForm() {
  if (!apiForm.name.trim()) {
    ElMessage.warning('请填写接口名称')
    return
  }
  if (!apiForm.path.trim()) {
    ElMessage.warning('请填写请求路径')
    return
  }
  if (apiForm.baseUrl.trim() && !/^https?:\/\//.test(apiForm.baseUrl.trim())) {
    ElMessage.warning('服务地址需以 http:// 或 https:// 开头')
    return
  }
  submitting.value = true
  try {
    const payload = {
      name: apiForm.name.trim(),
      httpMethod: apiForm.httpMethod,
      path: apiForm.path.trim(),
      baseUrl: apiForm.baseUrl.trim(),
      description: apiForm.description.trim(),
    }
    if (dialogMode.value === 'edit') {
      await http.put(`/apis/${editingId.value}`, payload)
      ElMessage.success('接口已更新')
      dialogVisible.value = false
      load()
    } else {
      await http.post('/apis', payload)
      ElMessage.success('接口创建成功')
      dialogVisible.value = false
      query.page = 1
      load()
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function removeApi(row) {
  try {
    await ElMessageBox.confirm(`确定删除接口「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await http.delete(`/apis/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="toolbar">
        <el-input
          v-model="query.name"
          placeholder="搜索接口名称"
          clearable
          class="search"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="query.httpMethod" placeholder="请求方法" clearable class="filter" @change="search">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新建接口</span>
        </el-button>
        <el-button type="primary" plain @click="router.push('/api/import')">
          <el-icon><Upload /></el-icon>
          <span>Swagger 导入</span>
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        class="api-table"
        @row-click="(row) => router.push(`/api/detail/${row.id}`)"
      >
        <el-table-column prop="name" label="接口名称" min-width="160">
          <template #default="{ row }">
            <span class="api-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方法" width="100">
          <template #default="{ row }">
            <span :class="methodClass(row.httpMethod)">{{ row.httpMethod }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="请求路径" min-width="220">
          <template #default="{ row }">
            <code class="path">{{ row.path }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="服务地址" min-width="180">
          <template #default="{ row }">
            <span class="base-url">{{ row.baseUrl || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新于" width="170">
          <template #default="{ row }">
            <span class="time">{{ row.updatedAt?.replace('T', ' ').slice(0, 16) ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="router.push(`/api/detail/${row.id}`)">详情</el-button>
            <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="removeApi(row)">删除</el-button>
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
          @size-change="search"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'edit' ? '编辑接口' : '手动创建接口'"
      width="560px"
    >
      <el-form label-width="90px" label-position="left">
        <el-form-item label="接口名称" required>
          <el-input v-model="apiForm.name" placeholder="如：查询用户详情" maxlength="255" />
        </el-form-item>
        <el-form-item label="请求方法" required>
          <el-select v-model="apiForm.httpMethod" style="width: 160px">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求路径" required>
          <el-input v-model="apiForm.path" placeholder="如：/api/users/{id}" />
        </el-form-item>
        <el-form-item label="服务地址">
          <el-input v-model="apiForm.baseUrl" placeholder="如：http://localhost:8080（可留空）" />
        </el-form-item>
        <el-form-item label="接口描述">
          <el-input
            v-model="apiForm.description"
            type="textarea"
            :rows="3"
            maxlength="1000"
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ dialogMode === 'edit' ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.search {
  width: 260px;
}

.filter {
  width: 130px;
}

.spacer {
  flex: 1;
}

.api-name {
  font-weight: 600;
}

.path {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
  color: #93c5fd;
}

.base-url,
.time {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.api-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--aqg-panel);
  --el-table-header-text-color: var(--aqg-text-secondary);
  --el-table-border-color: var(--aqg-border);
  --el-table-row-hover-bg-color: #273449;
  cursor: pointer;
}
</style>

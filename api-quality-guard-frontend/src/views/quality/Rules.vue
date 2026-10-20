<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qualityRules } from '@/mock/data'

const rules = ref([...qualityRules])

const dialogVisible = ref(false)
const editing = ref(null)
const form = reactive({ name: '', desc: '', level: '重要', enabled: true })

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', desc: '', level: '重要', enabled: true })
  dialogVisible.value = true
}

function openEdit(rule) {
  editing.value = rule
  Object.assign(form, rule)
  dialogVisible.value = true
}

function submit() {
  if (!form.name) {
    ElMessage.warning('请填写规则名称')
    return
  }
  if (editing.value) {
    Object.assign(editing.value, { name: form.name, desc: form.desc, level: form.level })
    ElMessage.success('规则已更新')
  } else {
    rules.value.unshift({ id: Date.now(), ...form })
    ElMessage.success('规则已新增')
  }
  dialogVisible.value = false
}

async function remove(rule) {
  await ElMessageBox.confirm(`确定删除规则「${rule.name}」吗？`, '提示', { type: 'warning' })
  rules.value = rules.value.filter((r) => r.id !== rule.id)
  ElMessage.success('已删除')
}

const levelType = (l) => (l === '严重' ? 'danger' : l === '重要' ? 'warning' : 'info')
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="toolbar">
        <div class="toolbar-title">
          质量规则
          <span class="sub">启用中的规则将参与质量评分与门禁判定</span>
        </div>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新增规则</span>
        </el-button>
      </div>

      <div class="rule-list">
        <div v-for="rule in rules" :key="rule.id" class="rule-card" :class="{ off: !rule.enabled }">
          <div class="rule-main">
            <div class="rule-head">
              <span class="rule-name">{{ rule.name }}</span>
              <el-tag :type="levelType(rule.level)" size="small" effect="dark">{{ rule.level }}</el-tag>
            </div>
            <p class="rule-desc">{{ rule.desc }}</p>
          </div>
          <div class="rule-side">
            <el-switch v-model="rule.enabled" />
            <el-button link type="primary" @click="openEdit(rule)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button link type="danger" @click="remove(rule)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑规则' : '新增规则'"
      width="480px"
    >
      <el-form label-width="72px">
        <el-form-item label="规则名称">
          <el-input v-model="form.name" placeholder="例如：响应时间限制" />
        </el-form-item>
        <el-form-item label="规则说明">
          <el-input v-model="form.desc" type="textarea" :rows="3" placeholder="描述规则的判定标准" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 100%">
            <el-option label="严重" value="严重" />
            <el-option label="重要" value="重要" />
            <el-option label="一般" value="一般" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
}

.sub {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: var(--aqg-text-muted);
}

.rule-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 18px;
  border: 1px solid var(--aqg-border);
  border-radius: 10px;
  background: var(--aqg-panel);
  transition: border-color 0.2s;
}

.rule-card:hover {
  border-color: #475569;
}

.rule-card.off {
  opacity: 0.55;
}

.rule-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.rule-name {
  font-size: 14.5px;
  font-weight: 600;
}

.rule-desc {
  margin: 0;
  font-size: 13px;
  color: var(--aqg-text-secondary);
  line-height: 1.6;
}

.rule-side {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
</style>

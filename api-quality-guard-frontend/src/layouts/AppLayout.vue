<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)
const displayName = computed(() => auth.user?.displayName || auth.user?.username || '用户')
const avatarText = computed(() => (displayName.value.charAt(0) || 'U').toUpperCase())
const roleLabel = computed(() => (auth.isAdmin ? '管理员' : '普通用户'))

async function onCommand(command) {
  if (command === 'users') {
    router.push('/system/users')
    return
  }
  if (command === 'logout') {
    await auth.logout()
    router.replace('/login')
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-mark">AQG</span>
        <span class="logo-text">API Quality Guard</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu index="api">
          <template #title>
            <el-icon><Collection /></el-icon>
            <span>API 资产管理</span>
          </template>
          <el-menu-item index="/api/list">接口列表</el-menu-item>
          <el-menu-item index="/api/import">Swagger 导入</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="test">
          <template #title>
            <el-icon><Aim /></el-icon>
            <span>测试中心</span>
          </template>
          <el-menu-item index="/test/cases">测试用例</el-menu-item>
          <el-menu-item index="/test/records">执行记录</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="quality">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>质量中心</span>
          </template>
          <el-menu-item index="/quality/score">质量评分</el-menu-item>
          <el-menu-item index="/quality/rules">质量规则</el-menu-item>
          <el-menu-item index="/quality/gate">质量门禁</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="ai">
          <template #title>
            <el-icon><MagicStick /></el-icon>
            <span>AI 分析</span>
          </template>
          <el-menu-item index="/ai/report">AI 质量报告</el-menu-item>
          <el-menu-item index="/ai/assistant">AI 辅助分析</el-menu-item>
        </el-sub-menu>
        <el-menu-item v-if="auth.isAdmin" index="/system/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="right">
      <el-header class="header">
        <div class="header-title">{{ route.meta.title }}</div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="onCommand">
            <div class="user">
              <el-avatar :size="30" class="avatar">{{ avatarText }}</el-avatar>
              <div class="user-meta">
                <span class="user-name">{{ displayName }}</span>
                <span class="user-role">{{ roleLabel }}</span>
              </div>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="auth.isAdmin" command="users">
                  <el-icon><User /></el-icon>
                  用户管理
                </el-dropdown-item>
                <el-dropdown-item command="logout" :divided="auth.isAdmin">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: var(--aqg-panel);
  border-right: 1px solid #1e293b;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 16px;
  border-bottom: 1px solid #1e293b;
  flex-shrink: 0;
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  font-weight: 700;
  font-size: 13px;
  flex-shrink: 0;
}

.logo-text {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #94a3b8;
  --el-menu-active-color: #60a5fa;
  --el-menu-hover-bg-color: #1e293b;
  --el-menu-item-height: 46px;
  --el-menu-sub-item-height: 40px;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(59, 130, 246, 0.12);
  border-right: 2px solid #3b82f6;
}

.right {
  height: 100vh;
  overflow: hidden;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  background: var(--aqg-card);
  border-bottom: 1px solid var(--aqg-border);
}

.header-title {
  font-size: 15px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 22px;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.avatar {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-name {
  font-size: 13px;
  color: var(--aqg-text-secondary);
}

.user-role {
  font-size: 11px;
  color: var(--aqg-text-muted);
}

.arrow {
  color: var(--aqg-text-muted);
  font-size: 12px;
}

.main {
  background: var(--aqg-bg);
  padding: 20px;
  overflow-y: auto;
}
</style>

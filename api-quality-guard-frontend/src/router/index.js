import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/authSession'

const routes = [
  {
    path: '/login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { title: '注册', public: true },
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'api/list',
        component: () => import('@/views/api/List.vue'),
        meta: { title: '接口列表' },
      },
      {
        path: 'api/import',
        component: () => import('@/views/api/Import.vue'),
        meta: { title: 'Swagger 导入' },
      },
      {
        path: 'api/detail/:id',
        component: () => import('@/views/api/Detail.vue'),
        meta: { title: '接口详情' },
      },
      {
        path: 'test/cases',
        component: () => import('@/views/test/Cases.vue'),
        meta: { title: '测试用例' },
      },
      {
        path: 'test/records',
        component: () => import('@/views/test/Records.vue'),
        meta: { title: '执行记录' },
      },
      {
        path: 'test/report/:id',
        component: () => import('@/views/test/Report.vue'),
        meta: { title: '运行报告' },
      },
      {
        path: 'quality/score',
        component: () => import('@/views/quality/Score.vue'),
        meta: { title: '质量评分' },
      },
      {
        path: 'quality/rules',
        component: () => import('@/views/quality/Rules.vue'),
        meta: { title: '质量规则' },
      },
      {
        path: 'quality/gate',
        component: () => import('@/views/quality/Gate.vue'),
        meta: { title: '质量门禁' },
      },
      {
        path: 'ai/report',
        component: () => import('@/views/ai/Report.vue'),
        meta: { title: 'AI 质量报告' },
      },
      {
        path: 'ai/assistant',
        component: () => import('@/views/ai/Assistant.vue'),
        meta: { title: 'AI 辅助分析' },
      },
      {
        path: 'system/users',
        component: () => import('@/views/system/Users.vue'),
        meta: { title: '用户管理', requiresAdmin: true },
      },
      {
        path: 'settings',
        component: () => import('@/views/settings/Index.vue'),
        meta: { title: '系统设置' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const auth = useAuthStore()
  try {
    await auth.ensureUser()
  } catch {
    auth.clear()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { path: '/dashboard' }
  }
  return true
})

export default router

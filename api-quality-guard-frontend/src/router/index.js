import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const routes = [
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

export default router

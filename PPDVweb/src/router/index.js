import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes = [
  { path: '/login', component: () => import('@/views/login/index.vue') },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '监控大屏' } },
      { path: 'cockpit', name: 'cockpit', component: () => import('@/views/cockpit/index.vue'), meta: { title: '驾驶舱' } },
      { path: 'planning', name: 'planning', component: () => import('@/views/planning/index.vue'), meta: { title: '作业规划' } },
      { path: 'spray', name: 'spray', component: () => import('@/views/spray/index.vue'), meta: { title: '点喷任务看板' } },
      { path: 'device', name: 'device', component: () => import('@/views/device/index.vue'), meta: { title: '设备管理' } },
      { path: 'report', name: 'report', component: () => import('@/views/report/index.vue'), meta: { title: '数据报表' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.path === '/login') return next()
  if (!getToken()) return next('/login')
  next()
})

export default router

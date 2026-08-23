import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/store/user'

export const pageRoutes = [
  { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '监控大屏', permissions: ['ppdv:device:list', 'ppdv:inspection:query'] } },
  { path: 'cockpit', name: 'cockpit', component: () => import('@/views/cockpit/index.vue'), meta: { title: '驾驶舱', permissions: ['ppdv:device:list', 'ppdv:device:query'] } },
  { path: 'planning', name: 'planning', component: () => import('@/views/planning/index.vue'), meta: { title: '作业规划', permissions: ['ppdv:device:list'] } },
  { path: 'area', name: 'area', component: () => import('@/views/area/index.vue'), meta: { title: '区域管理', permissions: ['ppdv:area:list'] } },
  { path: 'archive', name: 'archive', component: () => import('@/views/archive/index.vue'), meta: { title: '区域档案', permissions: ['ppdv:archive:query'] } },
  { path: 'inspection', name: 'inspection', component: () => import('@/views/inspection/index.vue'), meta: { title: '巡检管理', permissions: ['ppdv:inspection:query'] } },
  { path: 'spray', name: 'spray', component: () => import('@/views/spray/index.vue'), meta: { title: '点喷任务', permissions: ['ppdv:spray:query'] } },
  { path: 'review', name: 'review', component: () => import('@/views/review/index.vue'), meta: { title: '喷后复查', permissions: ['ppdv:spray:query'] } },
  { path: 'risk', name: 'risk', component: () => import('@/views/risk/index.vue'), meta: { title: '风险规则', permissions: ['ppdv:risk:config:query'] } },
  { path: 'device', name: 'device', component: () => import('@/views/device/index.vue'), meta: { title: '设备管理', permissions: ['ppdv:device:list'] } },
  { path: 'report', name: 'report', component: () => import('@/views/report/index.vue'), meta: { title: '数据报表', permissions: ['ppdv:inspection:query'] } }
]

const routes = [
  { path: '/login', component: () => import('@/views/login/index.vue') },
  { path: '/403', component: () => import('@/views/error/403.vue') },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    children: pageRoutes
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export function firstAccessiblePath(userStore) {
  const route = pageRoutes.find(item => userStore.hasAllPermissions(item.meta.permissions))
  return route ? `/${route.path}` : null
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.path === '/login') return true
  if (!getToken()) return { path: '/login', replace: true }

  const userStore = useUserStore()
  try {
    await userStore.ensureInfo()
  } catch {
    userStore.reset()
    return { path: '/login', replace: true }
  }

  const fallback = firstAccessiblePath(userStore)
  if (to.path === '/') return fallback ? { path: fallback, replace: true } : { path: '/403', replace: true }
  if (to.path === '/403') return true

  const permissions = to.meta.permissions || []
  if (!userStore.hasAllPermissions(permissions)) {
    return { path: '/403', query: { from: to.fullPath }, replace: true }
  }
  return true
})

export default router

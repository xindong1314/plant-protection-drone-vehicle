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
      { path: 'planning', name: 'planning', component: () => import('@/views/planning/index.vue'), meta: { title: '作业规划' } }
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

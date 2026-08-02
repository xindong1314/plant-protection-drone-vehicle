import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/views/layout/index.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: () => import('@/views/dashboard/index.vue') },
        { path: 'map-plan',  component: () => import('@/views/map-plan/index.vue') },
        { path: 'report',    component: () => import('@/views/report/index.vue') },
        { path: 'device',    component: () => import('@/views/device/index.vue') },
      ]
    }
  ],
})

export default router

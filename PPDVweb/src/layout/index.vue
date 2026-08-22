<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">A</div>
        <div><strong>植保无人车</strong><span>AGRO OPERATIONS</span></div>
      </div>
      <div class="nav-label">作业中心</div>
      <nav class="nav">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="nav-item" active-class="active">
          <el-icon><component :is="item.icon" /></el-icon>
          <span><strong>{{ item.title }}</strong><small>{{ item.subtitle }}</small></span>
        </router-link>
      </nav>
      <div class="system-state">
        <span class="state-dot"></span>
        <div><strong>系统运行正常</strong><small>数据链路已连接</small></div>
      </div>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div><span class="breadcrumb">植保作业平台 /</span><strong>{{ currentPage.title }}</strong></div>
        <div class="user">
          <div class="avatar">{{ (userStore.name || 'A').slice(0, 1).toUpperCase() }}</div>
          <div class="user-info"><strong>{{ userStore.name || 'admin' }}</strong><span>系统管理员</span></div>
          <el-button link @click="handleLogout">退出</el-button>
        </div>
      </header>
      <main class="content"><router-view /></main>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataLine, Monitor, MapLocation, SetUp, PieChart, CircleCheck } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const navItems = [
  { path: '/dashboard', title: '监控总览', subtitle: '运营数据与告警', icon: DataLine },
  { path: '/cockpit', title: '数字驾驶舱', subtitle: '实时遥测与态势', icon: Monitor },
  { path: '/planning', title: '作业规划', subtitle: '区域与任务编排', icon: MapLocation },
  { path: '/review', title: '喷后复查', subtitle: '效果确认与闭环', icon: CircleCheck },
  { path: '/device', title: '设备管理', subtitle: '车辆资产与状态', icon: SetUp },
  { path: '/report', title: '数据报表', subtitle: '效率与趋势分析', icon: PieChart }
]
const currentPage = computed(() => navItems.find((item) => route.path.startsWith(item.path)) || navItems[0])

onMounted(() => {
  if (!userStore.name) userStore.fetchInfo().catch(() => {})
})

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100%; display: flex; background: var(--ag-canvas); }
.sidebar { width: 238px; flex: 0 0 238px; display: flex; flex-direction: column; padding: 22px 14px 16px; color: #fff; background: var(--ag-dark); border-right: 1px solid #293029; }
.brand { display: flex; align-items: center; gap: 11px; padding: 0 8px 27px; }
.brand-mark { width: 34px; height: 34px; display: grid; place-items: center; color: #101410; background: var(--ag-primary); font-size: 19px; font-weight: 900; }
.brand strong, .brand span { display: block; }
.brand strong { font-size: 15px; letter-spacing: .5px; }
.brand span { margin-top: 3px; color: #7f8a7d; font-size: 9px; letter-spacing: 1.5px; }
.nav-label { padding: 0 10px 9px; color: #697268; font-size: 10px; letter-spacing: 1.5px; }
.nav { display: flex; flex-direction: column; gap: 3px; }
.nav-item { position: relative; display: flex; align-items: center; gap: 12px; min-height: 56px; padding: 8px 11px; color: #abb3aa; text-decoration: none; border: 1px solid transparent; border-radius: 3px; transition: .15s ease; }
.nav-item:hover { color: #fff; background: #171c17; }
.nav-item.active { color: #fff; background: var(--ag-dark-elevated); border-color: #303a2e; }
.nav-item.active::before { position: absolute; left: -15px; width: 3px; height: 30px; background: var(--ag-primary); content: ''; }
.nav-item .el-icon { color: var(--ag-primary); font-size: 19px; }
.nav-item strong, .nav-item small { display: block; }
.nav-item strong { font-size: 13px; font-weight: 620; }
.nav-item small { margin-top: 4px; color: #727c71; font-size: 10px; }
.system-state { margin-top: auto; display: flex; align-items: flex-start; gap: 10px; padding: 14px 10px 2px; border-top: 1px solid #2a302a; }
.state-dot { width: 7px; height: 7px; margin-top: 4px; border-radius: 50%; background: var(--ag-primary); box-shadow: 0 0 0 4px rgb(118 185 0 / 12%); }
.system-state strong, .system-state small { display: block; }
.system-state strong { color: #c9cec8; font-size: 11px; }
.system-state small { margin-top: 4px; color: #697268; font-size: 10px; }
.workspace { min-width: 0; flex: 1; display: flex; flex-direction: column; }
.topbar { height: 58px; flex: 0 0 58px; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; background: #fff; border-bottom: 1px solid var(--ag-border); }
.topbar > div:first-child { font-size: 12px; }
.breadcrumb { margin-right: 5px; color: var(--ag-muted); }
.user { display: flex; align-items: center; gap: 9px; }
.avatar { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 2px; color: #111; background: var(--ag-primary); font-size: 12px; font-weight: 800; }
.user-info strong, .user-info span { display: block; }
.user-info strong { font-size: 11px; }
.user-info span { margin-top: 2px; color: var(--ag-muted); font-size: 9px; }
.user .el-button { margin-left: 7px; color: var(--ag-muted); }
.content { flex: 1; min-height: 0; overflow: auto; padding: 24px; }
@media (max-width: 1180px) { .sidebar { width: 210px; flex-basis: 210px; } .content { padding: 20px; } }
</style>

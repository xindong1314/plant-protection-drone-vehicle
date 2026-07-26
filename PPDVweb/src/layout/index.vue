<template>
  <div class="layout screen-bg">
    <header class="topbar">
      <div class="brand">
        <el-icon><Cpu /></el-icon>
        <span>植保无人车监控平台</span>
      </div>
      <nav class="nav">
        <router-link to="/dashboard" class="nav-item" active-class="active">监控大屏</router-link>
        <router-link to="/cockpit" class="nav-item" active-class="active">驾驶舱</router-link>
        <router-link to="/planning" class="nav-item" active-class="active">作业规划</router-link>
      </nav>
      <div class="user">
        <span>{{ userStore.name || 'admin' }}</span>
        <el-button link type="primary" @click="handleLogout">退出</el-button>
      </div>
    </header>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

onMounted(() => {
  if (!userStore.name) {
    userStore.fetchInfo().catch(() => {})
  }
})

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  background: rgba(6, 21, 41, 0.9);
  border-bottom: 1px solid rgba(64, 158, 255, 0.25);
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: #7ec5ff;
  letter-spacing: 1px;
}
.nav {
  flex: 1;
  display: flex;
  gap: 8px;
  justify-content: center;
}
.nav-item {
  color: #9db8dd;
  text-decoration: none;
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 15px;
}
.nav-item.active {
  color: #fff;
  background: rgba(64, 158, 255, 0.25);
}
.user {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #d6e6ff;
}
.content {
  flex: 1;
  overflow: auto;
  padding: 14px;
}
</style>

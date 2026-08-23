<template>
  <main class="forbidden-page">
    <el-result icon="warning" title="无访问权限" sub-title="你的账号没有访问此页面所需的权限。">
      <template #extra>
        <el-button v-if="hasAccessiblePage" type="primary" @click="goHome">前往可访问页面</el-button>
        <el-button @click="handleLogout">退出登录</el-button>
      </template>
    </el-result>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { firstAccessiblePath } from '@/router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const hasAccessiblePage = computed(() => Boolean(firstAccessiblePath(userStore)))

function goHome() {
  router.replace('/')
}

async function handleLogout() {
  await userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.forbidden-page { min-height: 100%; display: grid; place-items: center; background: var(--ag-canvas); }
</style>

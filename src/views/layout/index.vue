<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <el-menu
      :default-active="activeMenu"
      router
      class="sidebar"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
    >
      <div class="logo">PPDV 管理系统</div>
      <el-menu-item index="/dashboard">
        <el-icon><DataBoard /></el-icon>
        <span>仪表盘</span>
      </el-menu-item>
      <el-menu-item index="/map-plan">
        <el-icon><MapLocation /></el-icon>
        <span>地图规划</span>
      </el-menu-item>
      <el-menu-item index="/report">
        <el-icon><Document /></el-icon>
        <span>报表明细</span>
      </el-menu-item>
      <el-menu-item index="/device">
        <el-icon><Monitor /></el-icon>
        <span>设备管理</span>
      </el-menu-item>
    </el-menu>

    <!-- 内容区 -->
    <div class="main">
      <router-view />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataBoard, MapLocation, Document, Monitor } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => '/' + route.path.split('/')[1])
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 220px;
  flex-shrink: 0;
}

.logo {
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  text-align: center;
  padding: 20px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.main {
  flex: 1;
  padding: 20px;
  background: #f0f2f5;
  overflow-y: auto;
}
</style>

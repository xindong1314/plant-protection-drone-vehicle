<template>
  <div class="sidebar-logo-container" :class="{ collapse }">
    <router-link class="sidebar-logo-link" to="/">
      <span class="brand-mark">A</span>
      <span v-if="!collapse" class="brand-copy"><strong>管理控制台</strong><small>AGRO ADMIN</small></span>
    </router-link>
  </div>
</template>

<script setup>
defineProps({
  collapse: { type: Boolean, required: true }
})
</script>

<style lang="scss" scoped>
.sidebar-logo-container {
  height: 58px;
  overflow: hidden;
  background: #0f130f;
  border-bottom: 1px solid #2b322a;
}
.sidebar-logo-link {
  height: 100%;
  display: flex !important;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
}
.brand-mark {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  display: grid;
  place-items: center;
  color: #101410;
  background: #76b900;
  font-size: 17px;
  font-weight: 900;
}
.brand-copy strong,
.brand-copy small { display: block; white-space: nowrap; }
.brand-copy strong { color: #eef2ec; font-size: 13px; letter-spacing: .4px; }
.brand-copy small { margin-top: 3px; color: #737d71; font-size: 8px; letter-spacing: 1.4px; }
.collapse .sidebar-logo-link { justify-content: center; padding: 0; }
</style>

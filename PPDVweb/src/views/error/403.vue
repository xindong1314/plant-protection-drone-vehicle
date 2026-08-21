<template>
  <main class="forbidden-page">
    <el-result icon="warning" title="无访问权限" sub-title="你的账号没有访问此页面所需的权限。">
      <template #extra>
        <el-button type="primary" @click="$router.back()">返回上一页</el-button>
      </template>
    </el-result>
  </main>
</template>

<style scoped>
.forbidden-page { min-height: 100%; display: grid; place-items: center; background: var(--ag-canvas); }
</style>

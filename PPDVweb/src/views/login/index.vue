<template>
  <div class="login screen-bg">
    <el-card class="login-card">
      <div class="title">植保无人车监控平台</div>
      <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleLogin">登 录</el-button>
      </el-form>
      <div class="tip">默认账号：admin / admin123</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function handleLogin() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form)
      ElMessage.success('登录成功')
      router.push('/')
    } catch (e) {
      /* 错误已在拦截器提示 */
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-card {
  width: 380px;
  background: rgba(16, 42, 74, 0.7);
  border: 1px solid rgba(64, 158, 255, 0.3);
}
.title {
  font-size: 20px;
  font-weight: 700;
  color: #7ec5ff;
  text-align: center;
  margin-bottom: 22px;
}
.tip {
  margin-top: 10px;
  font-size: 12px;
  color: #7f9bc4;
  text-align: center;
}
</style>

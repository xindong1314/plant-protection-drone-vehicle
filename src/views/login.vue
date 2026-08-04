<template>
  <div class="login">
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">PPDV 业务运营系统</h3>
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          placeholder="账号"
          size="large"
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          placeholder="密码"
          size="large"
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <el-input
          v-model="loginForm.code"
          placeholder="验证码"
          size="large"
          style="width: 63%"
          @keyup.enter="handleLogin"
        />
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img" alt="验证码" />
        </div>
      </el-form-item>
      <el-form-item style="width:100%">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          style="width:100%"
          @click="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, login as loginApi } from '@/api/login'
import { setUsername } from '@/utils/auth'

const router = useRouter()

const loginForm = reactive({
  username: '',
  password: '',
  code: '',
  uuid: ''
})

const loginRules = {
  username: [{ required: true, trigger: 'blur', message: '请输入账号' }],
  password: [{ required: true, trigger: 'blur', message: '请输入密码' }],
  code: [{ required: true, trigger: 'change', message: '请输入验证码' }]
}

const codeUrl = ref('')
const loading = ref(false)
const captchaEnabled = ref(true)
const loginRef = ref()

function getCode() {
  getCaptcha().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = 'data:image/gif;base64,' + res.img
      loginForm.uuid = res.uuid
    }
  }).catch(() => {
    // request.js 拦截器已弹错误提示
  })
}

async function handleLogin() {
  try {
    await loginRef.value?.validate()
    loading.value = true
    await loginApi({ ...loginForm })
    setUsername(loginForm.username)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch {
    // 校验不通过 或 接口失败（request.js 拦截器已弹错误提示）
    if (loading.value) {
      getCode()
    }
  } finally {
    loading.value = false
  }
}

getCode()
</script>

<style scoped>
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #304156 0%, #409EFF 100%);
}

.title {
  margin: 0 auto 30px auto;
  text-align: center;
  color: #707070;
  font-size: 20px;
}

.login-form {
  border-radius: 6px;
  background: #ffffff;
  width: 400px;
  padding: 25px 25px 5px 25px;
}

.login-code {
  width: 33%;
  height: 40px;
  float: right;
}

.login-code-img {
  height: 40px;
  padding-left: 12px;
  cursor: pointer;
  vertical-align: middle;
}
</style>

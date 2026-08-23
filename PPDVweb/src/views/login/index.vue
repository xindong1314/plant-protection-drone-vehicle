<template>
  <div class="login">
    <section class="brand-side">
      <div class="login-brand"><span>A</span><strong>植保无人车作业平台</strong></div>
      <div class="hero-copy">
        <div class="eyebrow">AGRO OPERATIONS SYSTEM</div>
        <h1>让每一亩作业<br />清晰、可靠、可追溯</h1>
        <p>统一管理无人车设备、作业任务与生产数据，让现场决策建立在实时状态之上。</p>
      </div>
      <div class="telemetry-preview">
        <div><small>平台状态</small><strong><i></i> ONLINE</strong></div>
        <div><small>数据链路</small><strong>REAL-TIME</strong></div>
        <div><small>作业模式</small><strong>AUTONOMOUS</strong></div>
      </div>
      <div class="grid-lines"></div>
    </section>
    <section class="form-side">
      <div class="form-wrap">
        <div class="form-kicker">欢迎回来</div>
        <h2>登录作业控制台</h2>
        <p class="form-desc">请输入您的平台账号和密码</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
          <el-form-item label="账号" prop="username"><el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" size="large" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" size="large" show-password /></el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="handleLogin">进入控制台</el-button>
        </el-form>
        <div class="tip">演示账号：admin / admin123</div>
      </div>
      <div class="copyright">PLANT PROTECTION OPERATIONS · 2026</div>
    </section>
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
      router.replace('/')
    } catch (e) {
      // 错误由请求拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login { min-height: 100%; display: grid; grid-template-columns: minmax(520px, 1.15fr) minmax(430px, .85fr); background: #fff; }
.brand-side { position: relative; min-height: 650px; display: flex; flex-direction: column; overflow: hidden; padding: 38px 52px 42px; color: #fff; background: #101410; }
.login-brand { position: relative; z-index: 1; display: flex; align-items: center; gap: 11px; font-size: 14px; letter-spacing: .5px; }
.login-brand span { width: 32px; height: 32px; display: grid; place-items: center; color: #101410; background: var(--ag-primary); font-size: 18px; font-weight: 900; }
.hero-copy { position: relative; z-index: 1; max-width: 590px; margin: auto 0; }
.hero-copy .eyebrow { color: var(--ag-primary); }
.hero-copy h1 { margin: 18px 0; font-size: clamp(38px, 4vw, 62px); line-height: 1.1; letter-spacing: -2px; }
.hero-copy p { max-width: 480px; color: #929c91; font-size: 15px; line-height: 1.9; }
.telemetry-preview { position: relative; z-index: 1; display: grid; grid-template-columns: repeat(3, 1fr); border: 1px solid #333b32; }
.telemetry-preview > div { padding: 14px 16px; border-right: 1px solid #333b32; }
.telemetry-preview > div:last-child { border-right: 0; }
.telemetry-preview small, .telemetry-preview strong { display: block; }
.telemetry-preview small { margin-bottom: 9px; color: #667065; font-size: 9px; letter-spacing: 1px; }
.telemetry-preview strong { color: #cad0c9; font-size: 11px; }
.telemetry-preview i { display: inline-block; width: 6px; height: 6px; margin-right: 6px; border-radius: 50%; background: var(--ag-primary); }
.grid-lines { position: absolute; inset: 0; opacity: .2; background-image: linear-gradient(#566153 1px, transparent 1px), linear-gradient(90deg, #566153 1px, transparent 1px); background-size: 64px 64px; mask-image: linear-gradient(to bottom right, transparent 15%, #000 100%); }
.form-side { position: relative; display: grid; place-items: center; padding: 60px; }
.form-wrap { width: min(380px, 100%); }
.form-kicker { color: var(--ag-success); font-size: 11px; font-weight: 700; letter-spacing: 1.5px; }
.form-wrap h2 { margin: 10px 0 8px; font-size: 29px; letter-spacing: -.6px; }
.form-desc { margin-bottom: 34px; color: var(--ag-muted); font-size: 13px; }
.submit { width: 100%; margin-top: 8px; color: #101410; }
.tip { margin-top: 14px; color: #8b9389; font-size: 11px; text-align: center; }
.copyright { position: absolute; bottom: 25px; color: #a0a79e; font-size: 9px; letter-spacing: 1.2px; }
@media (max-width: 1050px) { .login { grid-template-columns: 1fr 440px; } .brand-side { padding-inline: 35px; } }
</style>

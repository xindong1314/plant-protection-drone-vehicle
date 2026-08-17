<template>
  <div class="login">
    <section class="brand-side">
      <div class="login-brand"><span>A</span><strong>植保无人车</strong><small>ADMINISTRATION</small></div>
      <div class="brand-copy">
        <div class="admin-eyebrow">AGRO ADMIN CONSOLE</div>
        <h1>管理设备、人员<br />与每一次关键操作</h1>
        <p>面向运维与系统管理员的统一工作台，集中处理设备资产、实时状态、权限和审计记录。</p>
      </div>
      <div class="capabilities">
        <div><small>01</small><strong>设备资产</strong><span>DEVICE ASSETS</span></div>
        <div><small>02</small><strong>权限安全</strong><span>ACCESS CONTROL</span></div>
        <div><small>03</small><strong>操作审计</strong><span>AUDIT TRAIL</span></div>
      </div>
      <div class="grid-lines"></div>
    </section>

    <section class="form-side">
      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" label-position="top">
        <div class="form-kicker">管理员登录</div>
        <h2>{{ title }}</h2>
        <p class="form-desc">使用已授权的管理账号进入系统</p>
        <el-form-item label="账号" prop="username">
          <el-input v-model="loginForm.username" type="text" size="large" auto-complete="off" placeholder="请输入账号">
            <template #prefix><svg-icon icon-class="user" class="input-icon" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" size="large" auto-complete="off" placeholder="请输入密码" show-password @keyup.enter="handleLogin">
            <template #prefix><svg-icon icon-class="password" class="input-icon" /></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="code" v-if="captchaEnabled">
          <div class="code-row">
            <el-input v-model="loginForm.code" size="large" auto-complete="off" placeholder="验证码" @keyup.enter="handleLogin">
              <template #prefix><svg-icon icon-class="validCode" class="input-icon" /></template>
            </el-input>
            <img :src="codeUrl" @click="getCode" class="login-code-img" title="点击刷新验证码" />
          </div>
        </el-form-item>
        <div class="form-options">
          <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
          <router-link v-if="register" class="link-type" to="/register">立即注册</router-link>
        </div>
        <el-button :loading="loading" size="large" type="primary" class="submit" @click.prevent="handleLogin">
          <span v-if="!loading">进入管理控制台</span><span v-else>正在验证...</span>
        </el-button>
      </el-form>
      <div class="el-login-footer">{{ footerContent }}</div>
    </section>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const loginForm = ref({ username: "admin", password: "admin123", rememberMe: false, code: "", uuid: "" })
const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}
const codeUrl = ref("")
const loading = ref(false)
const captchaEnabled = ref(true)
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
  redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (!valid) return
    loading.value = true
    if (loginForm.value.rememberMe) {
      Cookies.set("username", loginForm.value.username, { expires: 30 })
      Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
      Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
    } else {
      Cookies.remove("username")
      Cookies.remove("password")
      Cookies.remove("rememberMe")
    }
    userStore.login(loginForm.value).then(() => {
      const query = route.query
      const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
        if (cur !== "redirect") acc[cur] = query[cur]
        return acc
      }, {})
      router.push({ path: redirect.value || "/", query: otherQueryParams })
    }).catch(() => {
      loading.value = false
      if (captchaEnabled.value) getCode()
    })
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value.username = username === undefined ? loginForm.value.username : username
  loginForm.value.password = password === undefined ? loginForm.value.password : decrypt(password)
  loginForm.value.rememberMe = rememberMe === undefined ? false : Boolean(rememberMe)
}

getCode()
getCookie()
</script>

<style lang="scss" scoped>
.login { min-height: 100%; display: grid; grid-template-columns: minmax(560px, 1.15fr) minmax(450px, .85fr); background: #fff; }
.brand-side { position: relative; min-height: 680px; display: flex; flex-direction: column; overflow: hidden; padding: 38px 52px 42px; color: #fff; background: #0f130f; }
.login-brand { position: relative; z-index: 1; display: flex; align-items: center; gap: 10px; }
.login-brand > span { width: 32px; height: 32px; display: grid; place-items: center; color: #101410; background: #76b900; font-size: 18px; font-weight: 900; }
.login-brand strong { font-size: 14px; letter-spacing: .4px; }
.login-brand small { color: #717b70; font-size: 8px; letter-spacing: 1.5px; }
.brand-copy { position: relative; z-index: 1; max-width: 620px; margin: auto 0; }
.brand-copy .admin-eyebrow { color: #76b900; }
.brand-copy h1 { margin: 17px 0; font-size: clamp(38px, 4vw, 60px); line-height: 1.12; letter-spacing: -2px; }
.brand-copy p { max-width: 500px; color: #929c91; font-size: 14px; line-height: 1.9; }
.capabilities { position: relative; z-index: 1; display: grid; grid-template-columns: repeat(3, 1fr); border: 1px solid #333b32; }
.capabilities > div { padding: 14px 16px; border-right: 1px solid #333b32; }
.capabilities > div:last-child { border-right: 0; }
.capabilities small, .capabilities strong, .capabilities span { display: block; }
.capabilities small { margin-bottom: 15px; color: #76b900; font-size: 9px; }
.capabilities strong { color: #dce2da; font-size: 11px; }
.capabilities span { margin-top: 4px; color: #636d62; font-size: 8px; letter-spacing: .8px; }
.grid-lines { position: absolute; inset: 0; opacity: .18; background-image: linear-gradient(#596456 1px, transparent 1px), linear-gradient(90deg, #596456 1px, transparent 1px); background-size: 64px 64px; mask-image: linear-gradient(to bottom right, transparent 12%, #000 100%); }
.form-side { position: relative; display: grid; place-items: center; padding: 60px; }
.login-form { width: min(390px, 100%); }
.form-kicker { color: #5f9500; font-size: 10px; font-weight: 700; letter-spacing: 1.4px; }
.login-form h2 { margin: 10px 0 7px; color: #171b17; font-size: 28px; letter-spacing: -.6px; }
.form-desc { margin: 0 0 30px; color: #727c71; font-size: 12px; }
.code-row { width: 100%; display: grid; grid-template-columns: 1fr 118px; gap: 10px; }
.login-code-img { width: 118px; height: 40px; object-fit: cover; cursor: pointer; border: 1px solid #d9ded4; border-radius: 3px; }
.form-options { display: flex; align-items: center; justify-content: space-between; margin: -3px 0 20px; font-size: 12px; }
.submit { width: 100%; color: #101410; }
.input-icon { width: 14px; height: 38px; }
.el-login-footer { position: absolute; bottom: 25px; color: #9ca49a; font-size: 9px; letter-spacing: 1.2px; }
@media (max-width: 1100px) { .login { grid-template-columns: 1fr 450px; } .brand-side { padding-inline: 35px; } }
</style>

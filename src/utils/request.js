import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken, removeUsername } from '@/utils/auth'

// 创建 axios 实例
const service = axios.create({
  // baseURL 从环境变量读取，对应 vite.config.js 里的代理前缀
  baseURL: import.meta.env.VITE_APP_BASE_API,
  // 超时 10 秒
  timeout: 10000
})

// 请求拦截器 —— 自动附加 JWT Token
service.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器 —— 统一处理返回结果
service.interceptors.response.use(
  res => {
    const code = res.data.code
    // 401 = token 过期或无效，清 token 并跳回登录页
    if (code === 401) {
      removeToken()
      removeUsername()
      location.href = '/login'
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    // 200 = 成功，直接把 data 返回给调用方
    if (code === 200) {
      return res.data
    }
    // 其他状态码 = 后端业务异常，弹错误提示
    ElMessage.error(res.data.msg || '请求失败')
    return Promise.reject(new Error(res.data.msg || '请求失败'))
  },
  error => {
    // 网络错误或超时
    let message = error.message
    if (message === 'Network Error') {
      message = '后端接口连接异常，请确认 PPDVapi 是否已启动'
    } else if (message.includes('timeout')) {
      message = '请求超时'
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service

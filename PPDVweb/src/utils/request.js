import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 15000
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code === 200 || data.code === undefined) {
      return data
    }
    if (data.code === 401) {
      removeToken()
      if (location.pathname !== '/login') {
        location.href = '/login'
      }
      return Promise.reject(new Error(data.msg || '未认证'))
    }
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(new Error(data.msg || 'Error'))
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service

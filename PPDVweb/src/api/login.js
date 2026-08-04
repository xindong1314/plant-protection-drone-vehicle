import request from '@/utils/request'
import { setToken } from '@/utils/auth'

// 获取验证码
export function getCaptcha() {
  return request({
    url: '/captchaImage',
    method: 'get',
    timeout: 20000
  })
}

// 登录
export function login(data) {
  return request({
    url: '/login',
    method: 'post',
    data: data
  }).then(res => {
    // 登录成功后把 token 存到 localStorage
    if (res.token) {
      setToken(res.token)
    }
    return res
  })
}

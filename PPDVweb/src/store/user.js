import { defineStore } from 'pinia'
import { login as loginApi, getInfo, logout as logoutApi } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    name: '',
    roles: [],
    permissions: []
  }),
  actions: {
    async login(form) {
      const res = await loginApi(form)
      this.token = res.token
      setToken(res.token)
    },
    async fetchInfo() {
      const res = await getInfo()
      this.name = res.user ? res.user.nickName : ''
      this.roles = res.roles || []
      this.permissions = res.permissions || []
    },
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        /* ignore */
      }
      this.reset()
    },
    reset() {
      this.token = ''
      this.name = ''
      this.roles = []
      this.permissions = []
      removeToken()
    }
  }
})

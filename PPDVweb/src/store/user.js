import { defineStore } from 'pinia'
import { login as loginApi, getInfo, logout as logoutApi } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    name: '',
    roles: [],
    permissions: [],
    infoLoaded: false,
    infoPromise: null
  }),
  getters: {
    hasPermission: (state) => (permission) =>
      state.permissions.includes('*:*:*') || state.permissions.includes(permission),
    hasAllPermissions: (state) => (permissions = []) =>
      state.permissions.includes('*:*:*') || permissions.every(permission => state.permissions.includes(permission))
  },
  actions: {
    async login(form) {
      const res = await loginApi(form)
      this.token = res.token
      this.name = ''
      this.roles = []
      this.permissions = []
      this.infoLoaded = false
      setToken(res.token)
    },
    async fetchInfo() {
      if (this.infoPromise) return this.infoPromise
      this.infoPromise = getInfo().then((res) => {
        this.name = res.user ? res.user.nickName : ''
        this.roles = res.roles || []
        this.permissions = res.permissions || []
        this.infoLoaded = true
      }).finally(() => {
        this.infoPromise = null
      })
      return this.infoPromise
    },
    async ensureInfo() {
      if (!this.infoLoaded) await this.fetchInfo()
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
      this.infoLoaded = false
      this.infoPromise = null
      removeToken()
    }
  }
})

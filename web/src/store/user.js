import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { TOKEN_KEY, USER_KEY } from '@/api/request'

const readUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
    unread: 0
  }),
  getters: {
    isLogin: (s) => Boolean(s.token),
    userId: (s) => s.user?.id || null,
    nickname: (s) => s.user?.nickname || s.user?.realName || '同学',
    creditScore: (s) => s.user?.creditScore ?? 100,
    verified: (s) => s.user?.verifyState === 'VERIFIED'
  },
  actions: {
    async login(account, password) {
      const data = await authApi.login({ account, password })
      this.setToken(data.token)
      this.user = data.user || (await authApi.me())
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return this.user
    },
    async register(payload) {
      const data = await authApi.register(payload)
      if (data?.token) this.setToken(data.token)
      return data
    },
    setToken(token) {
      this.token = token || ''
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    },
    async fetchMe() {
      if (!this.token) return null
      const me = await authApi.me()
      this.user = me
      localStorage.setItem(USER_KEY, JSON.stringify(me))
      return me
    },
    async updateProfile(payload) {
      const user = await authApi.updateProfile(payload)
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },
    async verifyStudent(payload) {
      const user = await authApi.verifyStudent(payload)
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },
    async refreshUnread() {
      if (!this.token) return
      try {
        const res = await (await import('@/api')).noticeApi.unreadCount()
        this.unread = typeof res === 'number' ? res : res?.count || 0
      } catch {
        /* 静默失败，不打断页面 */
      }
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})

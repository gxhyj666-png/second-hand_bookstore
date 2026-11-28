import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { TOKEN_KEY, USER_KEY } from '@/api/request'

const ROLES_KEY = 'tl_roles'

const readUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

const readRoles = () => {
  try {
    const list = JSON.parse(localStorage.getItem(ROLES_KEY) || '[]')
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
    roles: readRoles(),
    unread: 0
  }),
  getters: {
    isLogin: (s) => Boolean(s.token),
    userId: (s) => s.user?.id || null,
    nickname: (s) => s.user?.nickname || s.user?.realName || '同学',
    creditScore: (s) => s.user?.creditScore ?? 100,
    verified: (s) => s.user?.verifyState === 'VERIFIED',
    canTrade: (s) => Boolean(s.user?.idVerified && s.user?.studentVerified),
    /** 主身份 + 附加身份，例如 ['MERCHANT', 'OPERATOR'] */
    allRoles: (s) => (s.roles && s.roles.length ? s.roles : s.user?.role ? [s.user.role] : []),
    hasRole: (s) => (role) => {
      const list = s.roles && s.roles.length ? s.roles : s.user?.role ? [s.user.role] : []
      return list.includes(role)
    }
  },
  actions: {
    async login(account, password, loginType = 'STUDENT') {
      const data = await authApi.login({ account, password, loginType })
      this.setSession(data)
      return this.user
    },
    async sendSmsCode(phone, purpose = 'LOGIN') {
      return authApi.smsCode({ phone, purpose })
    },
    async smsLogin(phone, code, loginType = 'STUDENT') {
      const data = await authApi.smsLogin({ phone, code, loginType })
      this.setSession(data)
      return this.user
    },
    setSession(data) {
      if (!data) return
      this.setToken(data.token)
      this.user = data.user || null
      this.roles = Array.isArray(data.roles) && data.roles.length ? data.roles : data.user?.role ? [data.user.role] : []
      if (this.user) localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      localStorage.setItem(ROLES_KEY, JSON.stringify(this.roles))
    },
    setUser(user) {
      this.user = user || null
      if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
      else localStorage.removeItem(USER_KEY)
    },
    async register(payload) {
      const data = await authApi.register(payload)
      if (data?.token) this.setSession(data)
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
      try {
        const roles = await authApi.roles()
        this.roles = Array.isArray(roles) && roles.length ? roles : me?.role ? [me.role] : []
        localStorage.setItem(ROLES_KEY, JSON.stringify(this.roles))
      } catch {
        /* 旧 token 没有 roles 时不打断页面 */
      }
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
      this.roles = []
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(ROLES_KEY)
    }
  }
})

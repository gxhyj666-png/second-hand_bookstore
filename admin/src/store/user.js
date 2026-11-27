import { defineStore } from 'pinia'
import { login as loginApi, getMe } from '@/api/modules'

const TOKEN_KEY = 'ts_admin_token'
const USER_KEY = 'ts_admin_user'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser()
  }),
  getters: {
    isLogin: (state) => !!state.token,
    nickname: (state) => (state.user && state.user.nickname) || '管理员',
    roleLabel: (state) => {
      const map = { ADMIN: '超级管理员', OPERATOR: '运营', STUDENT: '学生', LEADER: '团长', CLERK: '店员' }
      const role = state.user && state.user.role
      return map[role] || '管理员'
    }
  },
  actions: {
    async login(form) {
      const data = await loginApi({ account: form.account, password: form.password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    async fetchMe() {
      const user = await getMe()
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})

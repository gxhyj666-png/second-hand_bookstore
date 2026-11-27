import axios from 'axios'
import { showToast } from 'vant'

/**
 * 统一请求层：拆 {code,message,data}，token 注入，401 跳登录。
 * baseURL 走 /api，由 dev proxy 或 nginx 反代到后端 /v1（后端本身不带 /api 前缀）。
 * 打包 APK 时把 VITE_API_BASE 设为 https://你的域名，即可直连后端。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

const TOKEN_KEY = 'tl_token'
const USER_KEY = 'tl_user'

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      const error = new Error(body.message || '请求失败')
      error.code = body.code
      if (body.code === 40100) {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
        location.hash = '#/login'
      }
      showToast(body.message || '请求失败')
      return Promise.reject(error)
    }
    return body
  },
  (err) => {
    const status = err?.response?.status
    if (status === 401 || status === 403) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      location.hash = '#/login'
    }
    const msg =
      err?.response?.data?.message ||
      (err.code === 'ECONNABORTED' ? '请求超时，请检查后端是否启动' : '网络异常，请稍后重试')
    showToast(msg)
    return Promise.reject(err)
  }
)

export { TOKEN_KEY, USER_KEY }
export default request

import axios from 'axios'
import { ElMessage } from 'element-plus'
import { setupMock } from '@/mock'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// VITE_USE_MOCK === 'true' 时挂载本地假数据 adapter，其余情况保持真实请求
setupMock(service)

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('ts_admin_token')
    if (token) {
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 统一响应体：{ code, message, data }
    if (res && typeof res === 'object' && Object.prototype.hasOwnProperty.call(res, 'code')) {
      if (res.code === 0) {
        return res.data
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      localStorage.removeItem('ts_admin_token')
      localStorage.removeItem('ts_admin_user')
      ElMessage.error('登录状态已失效，请重新登录')
      if (!window.location.hash.startsWith('#/login')) {
        window.location.hash = '#/login'
      }
      return Promise.reject(error)
    }
    const message =
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service

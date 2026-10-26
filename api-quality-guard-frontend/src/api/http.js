import axios from 'axios'
import { getToken, clearToken } from '@/utils/authSession'

// 统一 Axios 实例：开发环境经 Vite 代理转发到后端 8080 端口
const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 请求拦截器：已登录时携带 Bearer Token；不设置全局 Content-Type，避免影响 FormData 上传
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：后端统一返回 Result 结构 { code, message, data }。
// HTTP 200 且 code=200 时直接把 data 交给调用方；401 清除登录态并回登录页。
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 200) {
      return body.data
    }
    return Promise.reject(new Error(body?.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    const url = error.config?.url || ''
    const isAuthEntry = url.includes('/auth/login') || url.includes('/auth/register')

    if (status === 401 && !isAuthEntry) {
      clearToken()
      if (!['/login', '/register'].includes(window.location.pathname)) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.replace(`/login?redirect=${redirect}`)
      }
    }
    return Promise.reject(new Error(message))
  },
)

export default http

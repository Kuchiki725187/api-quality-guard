import axios from 'axios'

// 统一 Axios 实例：开发环境经 Vite 代理转发到后端 8080 端口
const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 响应拦截器：后端统一返回 Result 结构 { code, message, data }。
// HTTP 200 且 code=200 时直接把 data 交给调用方，其余情况统一抛出 Error。
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 200) {
      return body.data
    }
    return Promise.reject(new Error(body?.message || '请求失败'))
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  },
)

export default http

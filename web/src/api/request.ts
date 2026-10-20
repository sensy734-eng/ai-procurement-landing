import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'procurement_token'

const codeText: Record<string, string> = {
  NotFound: '单据不存在',
  Ambiguous: '问题存在歧义，请补充条件',
  NoPermission: '无权限访问',
  NoData: '条件下无匹配数据',
  Unsupported: '暂不支持该操作',
  InvalidParameter: '参数不合法'
}

const service = axios.create({
  baseURL: '/api',
  timeout: 15000
})

service.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code && body.code !== 'Success') {
      ElMessage.error(body.message || codeText[body.code] || '请求失败')
      return Promise.reject(new Error(body.message || body.code))
    }
    return body.data
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      if (window.location.pathname !== '/login') {
        ElMessage.warning(message || '登录已过期，请重新登录')
        window.location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error(message || '无权限访问')
    } else {
      ElMessage.error(message || '网络或服务异常')
    }
    return Promise.reject(error)
  }
)

export default service

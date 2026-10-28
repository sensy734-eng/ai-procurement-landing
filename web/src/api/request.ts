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
    // skipCodeCheck：智能助手的七类结果码都是业务语义（NoData/Ambiguous/Unsupported…），
    // 页面要拿 payload 渲染澄清选项与拒答话术，不能一律当错误弹掉
    if ((response.config as { skipCodeCheck?: boolean }).skipCodeCheck) {
      return body
    }
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

/**
 * 带会话令牌的 blob 下载。导出接口不能把令牌放 URL 上，
 * 因此用 fetch 带 Authorization 头拿 blob 再触发浏览器下载。
 */
export async function downloadCsv(url: string, filename: string) {
  const token = localStorage.getItem(TOKEN_KEY)
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok) {
    throw new Error('导出失败，状态码 ' + response.status)
  }
  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

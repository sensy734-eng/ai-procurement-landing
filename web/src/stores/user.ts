import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import * as authApi from '@/api/auth'
import type { LoginResult, ScopeInfo, UserInfo } from '@/api/auth'
import { TOKEN_KEY } from '@/api/request'

interface UserState {
  token: string
  user: UserInfo | null
  roles: string[]
  roleNames: string[]
  permissions: string[]
  dataScope: ScopeInfo
  pwdNeedChange: boolean
  loaded: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null,
    roles: [],
    roleNames: [],
    permissions: [],
    dataScope: { all: false, deptIds: [], warehouseIds: [], ownerNos: [] },
    pwdNeedChange: false,
    loaded: false
  }),

  getters: {
    hasPerm: (state) => (code?: string) => !code || state.permissions.includes(code),
    scopeText: (state) => {
      if (state.dataScope.all) {
        return '全部数据'
      }
      const parts: string[] = []
      if (state.dataScope.deptIds.length) {
        parts.push(`部门 ${state.dataScope.deptIds.length} 个`)
      }
      if (state.dataScope.warehouseIds.length) {
        parts.push(`仓库 ${state.dataScope.warehouseIds.length} 个`)
      }
      if (state.dataScope.ownerNos.length) {
        parts.push(`归属对象 ${state.dataScope.ownerNos.length} 个`)
      }
      return parts.length ? parts.join(' / ') : '未授权数据范围'
    }
  },

  actions: {
    applyLoginResult(result: LoginResult) {
      this.token = result.token
      this.user = result.user
      this.roles = result.roles
      this.roleNames = result.roleNames
      this.permissions = result.permissions
      this.dataScope = result.dataScope
      this.pwdNeedChange = result.pwdNeedChange
      this.loaded = true
      localStorage.setItem(TOKEN_KEY, result.token)
    },

    async login(username: string, password: string) {
      const result = await authApi.login(username, password)
      this.applyLoginResult(result)
      if (result.pwdNeedChange) {
        ElMessage.warning('当前账号为管理员重置密码，建议立即修改')
      }
    },

    async fetchMe() {
      const result = await authApi.fetchMe()
      this.applyLoginResult(result)
    },

    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.clear()
      }
    },

    clear() {
      this.token = ''
      this.user = null
      this.roles = []
      this.roleNames = []
      this.permissions = []
      this.dataScope = { all: false, deptIds: [], warehouseIds: [], ownerNos: [] }
      this.pwdNeedChange = false
      this.loaded = false
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})

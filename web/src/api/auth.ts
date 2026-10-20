import request from './request'

export interface UserInfo {
  id: number
  username: string
  realName: string
  deptId: number
  deptName: string
}

export interface ScopeInfo {
  all: boolean
  deptIds: number[]
  warehouseIds: number[]
  ownerNos: string[]
}

export interface LoginResult {
  token: string
  expiresAt: string
  user: UserInfo
  roles: string[]
  roleNames: string[]
  permissions: string[]
  dataScope: ScopeInfo
  pwdNeedChange: boolean
}

export function login(username: string, password: string) {
  return request.post<never, LoginResult>('/auth/login', { username, password })
}

export function fetchMe() {
  return request.get<never, LoginResult>('/auth/me')
}

export function logout() {
  return request.post<never, void>('/auth/logout')
}

export function changePassword(oldPassword: string, newPassword: string) {
  return request.post<never, void>('/auth/password', { oldPassword, newPassword })
}

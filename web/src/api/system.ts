import request from './request'

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface UserRow {
  id: number
  username: string
  realName: string
  deptId: number
  deptName: string
  phone: string
  email: string
  status: number
  pwdNeedChange: number
  lastLoginTime: string
  createdAt: string
}

export interface DictItem {
  id: number
  itemCode: string
  itemName: string
  parentId: number
  sortNo: number
  extValue: string
}

/** M11-2-1 用户列表 */
export function pageUsers(params: { pageNo: number; pageSize: number; keyword?: string }) {
  return request.get<never, PageResult<UserRow>>('/m11/users', { params })
}

/** M1-4-1 字典项 */
export function dictItems(typeCode: string) {
  return request.get<never, DictItem[]>(`/m1/dicts/${typeCode}/items`)
}

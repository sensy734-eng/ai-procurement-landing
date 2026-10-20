import request, { downloadCsv } from './request'

export { downloadCsv }

/**
 * M11 平台与权限管理接口封装。
 * 每个函数标注对应功能点编号，便于论文第 5 章按编号引用。
 */

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
  remark: string
  status: number
  pwdNeedChange: number
  lastLoginTime: string
  createdAt: string
  roleIds: number[]
  roleNames: string[]
}

export interface RoleRow {
  id: number
  roleCode: string
  roleName: string
  dataScopeAll: number
  isSystem: number
  status: number
  remark: string
  userCount: number
  permCount: number
}

export interface PermissionItem {
  id: number
  permCode: string
  permName: string
  actionType: string
  remark: string
}

export interface PermissionGroup {
  moduleCode: string
  moduleName: string
  permissions: PermissionItem[]
}

export interface ScopeRow {
  id: number
  subjectType: number
  subjectId: number
  scopeDimension: number
  scopeValue: string
  scopeValueName: string
}

export interface ScopeOption {
  value: string
  label: string
}

export interface ScopeOptions {
  warehouses: ScopeOption[]
  depts: ScopeOption[]
  owners: ScopeOption[]
}

export interface ParamRow {
  id: number
  paramCode: string
  paramName: string
  paramValue: string
  paramType: string
  paramGroup: string
  editable: number
  remark: string
  updatedAt: string
}

export interface OperationLogRow {
  id: number
  username: string
  realName: string
  moduleCode: string
  action: string
  targetType: string
  targetId: number
  targetNo: string
  detail: string
  resultCode: string
  ip: string
  createdAt: string
}

export interface QueryAuditRow {
  id: number
  sessionId: number
  userId: number
  username: string
  question: string
  intentCode: string
  paramsJson: string
  rejectedParamsJson: string
  dataScopeJson: string
  resultCode: string
  rowCount: number
  elapsedMs: number
  ip: string
  createdAt: string
}

export interface ApproverItem {
  approverType: number
  roleId?: number
  userId?: number
  displayName?: string
}

export interface ApprovalNode {
  nodeSeq: number
  nodeName: string
  signType: number
  remark?: string
  approvers: ApproverItem[]
}

export interface ApproverOptions {
  roles: { id: number; code: string; name: string }[]
  users: { id: number; username: string; realName: string; deptId: number }[]
}

export interface ResetPwdResult {
  userId: number
  username: string
  initialPassword: string
}

// ---------------- M11-2 用户 ----------------

/** M11-2-1 用户列表与检索 */
export function pageUsers(params: {
  pageNo: number
  pageSize: number
  keyword?: string
  deptId?: number
  status?: number
}) {
  return request.get<never, PageResult<UserRow>>('/m11/users', { params })
}

/** M11-2-1 用户详情 */
export function getUser(id: number) {
  return request.get<never, UserRow>(`/m11/users/${id}`)
}

/** M11-2-2 新增用户 */
export function createUser(body: Record<string, unknown>) {
  return request.post<never, ResetPwdResult>('/m11/users', body)
}

/** M11-2-2 编辑用户 */
export function updateUser(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m11/users/${id}`, body)
}

/** M11-2-3 启用与停用 */
export function toggleUserStatus(id: number, status: number) {
  return request.patch<never, void>(`/m11/users/${id}/status`, { status })
}

/** M11-2-4 重置密码 */
export function resetUserPassword(id: number) {
  return request.post<never, ResetPwdResult>(`/m11/users/${id}/reset-password`)
}

/** M11-2-6 用户角色分配 */
export function assignUserRoles(id: number, roleIds: number[]) {
  return request.put<never, void>(`/m11/users/${id}/roles`, { roleIds })
}

// ---------------- M11-2-5 / M11-3 角色与权限 ----------------

/** M11-2-5 角色列表 */
export function listRoles() {
  return request.get<never, RoleRow[]>('/m11/roles')
}

/** M11-2-5 新增角色 */
export function createRole(body: Record<string, unknown>) {
  return request.post<never, number>('/m11/roles', body)
}

/** M11-2-5 编辑角色 */
export function updateRole(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m11/roles/${id}`, body)
}

/** M11-3-1 权限点列表，按模块分组 */
export function listPermissions() {
  return request.get<never, PermissionGroup[]>('/m11/permissions')
}

/** M11-3-2 角色已持有的权限点 */
export function listRolePermissions(roleId: number) {
  return request.get<never, number[]>(`/m11/roles/${roleId}/permissions`)
}

/** M11-3-2 角色授权 */
export function grantRolePermissions(roleId: number, permIds: number[]) {
  return request.put<never, void>(`/m11/roles/${roleId}/permissions`, { permIds })
}

// ---------------- M11-3-3 数据范围 ----------------

/** M11-3-3 范围选项 */
export function getScopeOptions() {
  return request.get<never, ScopeOptions>('/m11/data-scopes/options')
}

/** M11-3-3 查询某主体的数据范围 */
export function listDataScopes(subjectType: number, subjectId: number) {
  return request.get<never, ScopeRow[]>('/m11/data-scopes', { params: { subjectType, subjectId } })
}

/** M11-3-3 保存某主体的数据范围 */
export function grantDataScopes(body: {
  subjectType: number
  subjectId: number
  items: { scopeDimension: number; scopeValue: string }[]
}) {
  return request.put<never, void>('/m11/data-scopes', body)
}

// ---------------- M11-4 参数与审计 ----------------

/** M11-4-1、M11-4-2、M11-4-3 参数查询 */
export function listParams(group?: string) {
  return request.get<never, ParamRow[]>('/m11/params', { params: { group } })
}

/** 参数更新 */
export function updateParam(paramCode: string, paramValue: string) {
  return request.put<never, void>(`/m11/params/${paramCode}`, { paramValue })
}

/** M11-4-4 操作日志检索 */
export function pageOperationLogs(params: Record<string, unknown>) {
  return request.get<never, PageResult<OperationLogRow>>('/m11/operation-logs', { params })
}

/** M11-4-4 操作日志导出地址，交由浏览器直接下载 */
export function operationLogExportUrl(params: Record<string, unknown>) {
  return buildDownloadUrl('/m11/operation-logs/export', params)
}

/** M11-4-5 智能查询审计列表 */
export function pageQueryAudits(params: Record<string, unknown>) {
  return request.get<never, PageResult<QueryAuditRow>>('/m11/query-audits', { params })
}

/** M11-4-6 审计详情 */
export function getQueryAudit(id: number) {
  return request.get<never, QueryAuditRow>(`/m11/query-audits/${id}`)
}

/** M11-4-6 审计导出地址 */
export function queryAuditExportUrl(params: Record<string, unknown>) {
  return buildDownloadUrl('/m11/query-audits/export', params)
}

// ---------------- M11-5 审批流程 ----------------

/** M11-5-1 审批节点配置 */
export function listApprovalFlow(billType: number) {
  return request.get<never, ApprovalNode[]>(`/m11/approval-flows/${billType}`)
}

/** M11-5-2 审批人候选 */
export function getApproverOptions() {
  return request.get<never, ApproverOptions>('/m11/approval-flows/options')
}

/** M11-5-1、M11-5-2 保存审批流程 */
export function saveApprovalFlow(billType: number, nodes: ApprovalNode[]) {
  return request.put<never, void>(`/m11/approval-flows/${billType}`, { nodes })
}

/**
 * 导出走浏览器下载，需要带会话令牌，因此拼接 token 参数不适用。
 * 下载实现见 request.ts 的 downloadCsv，这里只拼查询串。
 */
function buildDownloadUrl(path: string, params: Record<string, unknown>) {
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
  return `/api${path}${query ? '?' + query : ''}`
}

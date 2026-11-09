import request, { downloadCsv } from './request'

/**
 * M2 采购申请管理接口封装。
 * 功能点编号用于关联接口清单、权限定义与回归场景。
 * 页面编号对应功能模块清单 P5 申请单维护、P6 我的待办。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

// ---------------- M2-1 申请单 ----------------

export interface RequestRow {
  id: number
  requestNo: string
  requestDate: string
  deptId: number
  deptName: string
  applicantId: number
  applicantName: string
  requireDate: string
  totalAmount: number
  lifecycleStatus: number
  lifecycleStatusName: string
  auditStatus: number
  auditStatusName: string
  closeReason: string
  remark: string
  items: RequestItemRow[]
  approvalHistory: ApprovalHistoryRow[]
  activeItemCount: number
  remainingQtyTotal: number
}

export interface RequestItemRow {
  id: number
  itemNo: number
  materialId: number
  materialNo: string
  materialName: string
  spec: string
  quantity: number
  unitId: number
  unitName: string
  estPrice: number
  estAmount: number
  suggestSupplierId: number
  suggestSupplierName: string
  requireDate: string
  ownerType: number
  ownerId: number
  ownerNo: string
  ownerName: string
  transferredQty: number
  remainingQty: number
  itemStatus: number
  itemStatusName: string
  remark: string
}

export interface ApprovalHistoryRow {
  taskId: number
  taskNo: string
  nodeSeq: number
  nodeName: string
  approverId: number
  approverName: string
  signType: number
  signTypeName: string
  taskStatus: number
  taskStatusName: string
  opinion: string
  handleTime: string
}

export interface OwnerOption {
  id: number
  ownerType: number
  ownerTypeName: string
  ownerNo: string
  ownerName: string
}

export interface TodoRow {
  taskId: number
  taskNo: string
  billType: number
  billId: number
  billNo: string
  nodeSeq: number
  nodeName: string
  signType: number
  signTypeName: string
  createdTime: string
  applicantName: string
  requestDate: string
  totalAmount: number
  itemCount: number
}

export interface TransferredOrder {
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  itemCount: number
  totalQty: number
  totalAmount: number
  sourceRequestNo: string
}

export interface TransferResult {
  orders: TransferredOrder[]
  requestNos: string[]
}

/** M2-1-1 分页列表，keyword 匹配单号/申请人/备注 */
export function pageRequests(params: Record<string, unknown>) {
  return request.get<never, PageResult<RequestRow>>('/m2/requests/page', { params })
}

/** M2-1-1 详情，含明细行与审批历史（M2-3-6） */
export function getRequest(id: number) {
  return request.get<never, RequestRow>(`/m2/requests/${id}`)
}

/** M2-1-2 新建申请单（草稿） */
export function createRequest(body: Record<string, unknown>) {
  return request.post<never, number>('/m2/requests', body)
}

/** M2-1-3 编辑申请单主表 */
export function updateRequest(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m2/requests/${id}`, body)
}

/** M2-1-4 删除草稿 */
export function deleteRequest(id: number) {
  return request.delete<never, void>(`/m2/requests/${id}`)
}

/** M2-1-5 复制申请单 */
export function copyRequest(id: number) {
  return request.post<never, number>(`/m2/requests/${id}/copy`)
}

/** M2-1-6 导出 CSV */
export function downloadRequestCsv(id: number) {
  return downloadCsv(`/api/m2/requests/${id}/export`, `采购申请单_${id}.csv`)
}

// ---------------- M2-2 明细行 ----------------

/** M2-2-1 新增明细行 */
export function addRequestItem(requestId: number, body: Record<string, unknown>) {
  return request.post<never, number>(`/m2/requests/${requestId}/items`, body)
}

/** M2-2-2 编辑明细行 */
export function updateRequestItem(requestId: number, itemId: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m2/requests/${requestId}/items/${itemId}`, body)
}

/** M2-2-2 删除明细行 */
export function deleteRequestItem(requestId: number, itemId: number) {
  return request.delete<never, void>(`/m2/requests/${requestId}/items/${itemId}`)
}

/** M2-2-5 归属对象下拉 */
export function ownerOptions() {
  return request.get<never, { options: OwnerOption[] }>('/m2/requests/owner-options')
}

// ---------------- M2-3 审批 ----------------

/** M2-3-1 提交审批 */
export function submitRequest(id: number) {
  return request.post<never, void>(`/m2/requests/${id}/submit`)
}

/** M2-3-2 审批通过 */
export function approveRequest(id: number) {
  return request.post<never, void>(`/m2/requests/${id}/approve`)
}

/** M2-3-3 审批驳回，意见必填 */
export function rejectRequest(id: number, opinion: string) {
  return request.post<never, void>(`/m2/requests/${id}/reject`, { opinion })
}

/** M2-3-4 撤回申请 */
export function withdrawRequest(id: number) {
  return request.post<never, void>(`/m2/requests/${id}/withdraw`)
}

/** M2-3-5 整单关闭，原因必填 */
export function closeRequest(id: number, reason: string) {
  return request.post<never, void>(`/m2/requests/${id}/close`, { reason })
}

/** M2-2-4 按行退回：勾选行拆分为退回草稿单，返回新单 id */
export function returnItems(id: number, itemIds: number[], opinion: string) {
  return request.post<never, number>(`/m2/requests/${id}/return-items`, { itemIds, opinion })
}

/** M2-3-8 我的待办 */
export function todoRequests(params: Record<string, unknown>) {
  return request.get<never, PageResult<TodoRow>>('/m2/requests/todo', { params })
}

// ---------------- M2-4 转订单 ----------------

/** M2-4-1 / M2-4-2 申请转订单，按供应商聚合出单 */
export function transferRequests(sources: { requestId: number; itemIds: number[] | null }[]) {
  return request.post<never, TransferResult>('/m2/requests/transfer', { sources })
}

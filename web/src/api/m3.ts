import request, { downloadCsv } from './request'
import type { ApprovalHistoryRow, RequestRow } from './m2'

/**
 * M3 采购订单管理接口封装。
 * 功能点编号用于关联接口清单、权限定义与回归场景。
 * 页面编号对应功能模块清单 P7 订单列表、P8 订单详情（列表页抽屉）、P9 订单审批。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

// ---------------- M3-1 订单主表 ----------------

export interface OrderRow {
  id: number
  orderNo: string
  sourceRequestId: number | null
  sourceRequestNo: string | null
  supplierId: number
  supplierName: string
  buyerId: number
  buyerName: string
  deptId: number
  deptName: string
  orderDate: string
  deliveryDate: string | null
  deliveryPlace: string | null
  settlementType: number
  settlementTypeName: string
  lifecycleStatus: number
  lifecycleStatusName: string
  auditStatus: number
  auditStatusName: string
  fulfillStatus: number
  fulfillStatusName: string
  totalQty: number
  totalAmount: number
  noTaxAmount: number
  taxAmount: number
  /** 整单超期天数，后端按当前日期计算，未超期为 0（M3-5-4） */
  overdueDays: number
  /** 整单偏离标记：0 无、1 关注、2 显著，取明细行最高等级（M3-6） */
  deviationFlag: number
  closeReason: string | null
  remark: string | null
  items: OrderItemRow[]
  approvalHistory: ApprovalHistoryRow[]
  changeLogs: OrderChangeLogRow[]
  deliveryRecords: DeliveryRecordRow[]
}

export interface OrderItemRow {
  id: number
  itemNo: number
  materialId: number
  materialNo: string
  materialName: string
  spec: string
  quantity: number
  unitId: number
  unitName: string
  unitPrice: number
  taxRate: number
  amount: number
  noTaxAmount: number
  deliveryDate: string | null
  deliveryPlace: string | null
  deliveredQty: number
  remainingQty: number
  ownerType: number
  ownerId: number | null
  ownerNo: string | null
  ownerName: string | null
  itemFulfillStatus: number
  itemFulfillStatusName: string
  sourceRequestItemId: number | null
  overdueDays: number
  /** 行偏离率（单价 − 参考价）/ 参考价，无生效参考价为 null（M3-6） */
  deviationRate: number | null
  /** 行偏离等级：0 正常、1 关注、2 显著 */
  deviationLevel: number
  /** 行偏离等级名称：正常 / 关注 / 显著 */
  deviationLevelName: string
  remark: string | null
}

export interface OrderChangeLogRow {
  id: number
  orderId: number
  orderNo: string
  orderItemId: number | null
  itemNo: number | null
  changeField: string
  changeFieldName: string
  beforeValue: string | null
  afterValue: string | null
  changeReason: string
  operatorName: string
  changeAt: string
}

export interface DeliveryRecordRow {
  id: number
  orderId: number
  orderNo: string
  orderItemId: number
  itemNo: number
  materialName: string
  deliveryQty: number
  deliveryDate: string
  batchNo: string | null
  remark: string | null
  operatorName: string
  createdAt: string
}

export interface DeliverySummaryRow {
  supplierId: number
  supplierName: string
  orderCount: number
  totalQty: number
  deliveredQty: number
  remainingQty: number
  totalAmount: number
  deliveredAmount: number
  remainingAmount: number
}

export interface OrderTodoRow {
  taskId: number
  taskNo: string
  billId: number
  billNo: string
  nodeSeq: number
  nodeName: string
  signType: number
  signTypeName: string
  createdTime: string
  supplierName: string
  buyerName: string
  orderDate: string
  deliveryDate: string | null
  totalAmount: number
  itemCount: number
  overdueDays: number
}

/** M3-1-1 分页列表；overdue=true 只取超期未交付单（后端按交货期与履约状态过滤） */
export function pageOrders(params: Record<string, unknown>) {
  return request.get<never, PageResult<OrderRow>>('/m3/orders/page', { params })
}

/** M3-1-1 / P8 详情：主表 + 明细 + 审批历史 + 变更历史 + 交付流水 */
export function getOrder(id: number) {
  return request.get<never, OrderRow>(`/m3/orders/${id}`)
}

/** M3-1-2 新建订单（草稿），返回新单 id */
export function createOrder(body: Record<string, unknown>) {
  return request.post<never, number>('/m3/orders', body)
}

/** M3-1-4 编辑订单主表（草稿/已驳回） */
export function updateOrder(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m3/orders/${id}`, body)
}

/** M3-1-3 由申请单生成订单：带入已审核申请单的明细与归属对象 */
export function createOrderFromRequest(requestId: number) {
  return request.post<never, number>('/m3/orders/from-request', { requestId })
}

/** M3-1-5 作废订单：无交付登记、无到货验收、无入库记录才允许 */
export function voidOrder(id: number) {
  return request.post<never, void>(`/m3/orders/${id}/void`)
}

/** M3-1-6 导出 CSV（一行主表加多行明细的平表视图） */
export function downloadOrderCsv(id: number) {
  return downloadCsv(`/api/m3/orders/${id}/export`, `采购订单_${id}.csv`)
}

// ---------------- M3-2 明细行 ----------------

/** M3-2-1 新增明细行（校验物料启用与供应商供货范围） */
export function addOrderItem(orderId: number, body: Record<string, unknown>) {
  return request.post<never, number>(`/m3/orders/${orderId}/items`, body)
}

/** M3-2-2 编辑明细行 */
export function updateOrderItem(orderId: number, itemId: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m3/orders/${orderId}/items/${itemId}`, body)
}

/** M3-2-2 删除明细行（重算主表合计） */
export function deleteOrderItem(orderId: number, itemId: number) {
  return request.delete<never, void>(`/m3/orders/${orderId}/items/${itemId}`)
}

/** M3-2-4 按行关闭：剩余数量不再要求交付，履约状态按行重算 */
export function closeOrderItems(orderId: number, itemIds: number[]) {
  return request.post<never, void>(`/m3/orders/${orderId}/items/close`, { itemIds })
}

/** M3-2-5 归属对象下拉数据源（与 M2 共用档案） */
export function ownerOptions() {
  return request.get<never, { options: { id: number; ownerType: number; ownerTypeName: string; ownerNo: string; ownerName: string }[] }>(
    '/m3/orders/owner-options'
  )
}

// ---------------- M3-3 审批 ----------------

/** M3-3-1 提交审批（完整性校验 + 生成会签任务） */
export function submitOrder(id: number) {
  return request.post<never, void>(`/m3/orders/${id}/submit`)
}

/** M3-3-2 审批通过（会签：两人全部通过才推进） */
export function approveOrder(id: number) {
  return request.post<never, void>(`/m3/orders/${id}/approve`)
}

/** M3-3-3 审批驳回，意见必填 */
export function rejectOrder(id: number, opinion: string) {
  return request.post<never, void>(`/m3/orders/${id}/reject`, { opinion })
}

/** M3-3-4 撤回审批：本批次无人处理时回到草稿 */
export function withdrawOrder(id: number) {
  return request.post<never, void>(`/m3/orders/${id}/withdraw`)
}

/** M3-3-5 反审：仅系统管理员、已审核且未到货；reason 走查询参数 */
export function reauditOrder(id: number, reason: string) {
  return request.post<never, void>(`/m3/orders/${id}/reaudit`, null, {
    params: { reason }
  })
}

/** M3-3-6 整单关闭，原因必填 */
export function closeOrder(id: number, reason: string) {
  return request.post<never, void>(`/m3/orders/${id}/close`, { reason })
}

// ---------------- M3-4 变更留痕 ----------------

/** M3-4-1 变更登记：itemId 为空为整单级；前后值写入变更历史 */
export function changeOrder(id: number, body: Record<string, unknown>) {
  return request.post<never, void>(`/m3/orders/${id}/change`, body)
}

/** M3-4-2 变更历史（按时间正序） */
export function changeLogs(id: number) {
  return request.get<never, OrderChangeLogRow[]>(`/m3/orders/${id}/change-logs`)
}

// ---------------- M3-5 履约跟踪 ----------------

/** M3-5-1 交付进度登记：累计登记不超过订单行数量 */
export function registerDelivery(id: number, body: Record<string, unknown>) {
  return request.post<never, void>(`/m3/orders/${id}/deliveries`, body)
}

/** M3-5-1 交付流水查询 */
export function deliveryRecords(id: number) {
  return request.get<never, DeliveryRecordRow[]>(`/m3/orders/${id}/deliveries`)
}

/** M3-5-5 交付汇总：按供应商汇总已交付/未交付数量与金额，startDate/endDate 为下单日期窗 */
export function deliverySummary(params: Record<string, unknown>) {
  return request.get<never, DeliverySummaryRow[]>('/m3/orders/delivery-summary', { params })
}

// ---------------- 溯源与待办 ----------------

/** M3-1-1 订单溯源：返回来源申请单详情（复用 M2 RequestVO），手工单返回 NoData */
export function orderTrace(id: number) {
  return request.get<never, RequestRow>(`/m3/orders/${id}/trace`)
}

/** M3-3-2 订单待办（P9）：当前用户的会签任务 */
export function todoOrders(params: Record<string, unknown>) {
  return request.get<never, PageResult<OrderTodoRow>>('/m3/orders/todo', { params })
}

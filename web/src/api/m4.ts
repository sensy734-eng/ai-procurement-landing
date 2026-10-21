import request from './request'

/**
 * M4 到货验收管理接口封装。
 * 每个函数标注对应功能点编号，便于论文第 5 章按编号引用。
 * 页面编号对应功能模块清单 P10 到货验收列表、P11 验收登记。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

// ---------------- M4-1 到货登记 ----------------

/** 可登记的订单明细行（含剩余到货配额，后端现算） */
export interface ArrivalItemRow {
  id?: number
  inspectionId?: number
  orderItemId: number
  orderItemNo: number
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  unitId: number
  unitName: string
  arrivalQty: number | null
  qualifiedQty: number | null
  unqualifiedQty: number | null
  batchNo: string | null
  ownerType: number
  ownerId: number | null
  ownerNo: string | null
  remark: string | null
  /** 订单行数量 */
  orderQty: number
  /** 累计到货量（含本单） */
  arrivedQty: number
  /** 剩余到货配额 */
  remainingArrival: number
  /** 本行可入量：让步单含不合格部分，否则只合格部分 */
  inboundableQty: number
}

/** 可登记订单（选单下拉，M4-1-1） */
export interface ArrivalOrderRow {
  id: number
  orderNo: string
  supplierId: number
  supplierName: string
  deliveryDate: string | null
  deliveryPlace: string | null
  items: ArrivalItemRow[]
  remainingArrivalQty: number
}

/** 验收单列表行/详情（M4-4-1、M4-4-2） */
export interface ArrivalRow {
  id: number
  inspectionNo: string
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  arrivalDate: string
  warehouseId: number
  warehouseName: string
  inspectionMode: number
  inspectionModeName: string
  inspectionResult: number | null
  inspectionResultName: string | null
  qualifiedQty: number
  unqualifiedQty: number
  concessionFlag: number
  concessionReason: string | null
  inspectorId: number
  inspectorName: string
  status: number
  statusName: string
  remark: string | null
  createdAt: string
  createdBy: number
  createdByName: string
  items: ArrivalItemRow[]
  /** 本单已生成的入库单草稿（M4-3-3 可有多张） */
  inboundOrders: InboundDraftRow[]
  /** 整单可入量合计 */
  inboundableQty: number
  /** 已生成入库量合计 */
  generatedQty: number
}

/** 入库单草稿摘要（M4-3，确认在轮次 7） */
export interface InboundDraftRow {
  id: number
  inboundNo: string
  inspectionId: number
  inspectionNo: string
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  warehouseId: number
  warehouseName: string
  inboundDate: string
  status: number
  statusName: string
  totalQty: number
  operatorId: number
  operatorName: string
  remark: string | null
}

/** 验收结果统计行（M4-4-3） */
export interface ArrivalStatRow {
  supplierId: number
  supplierName: string
  inspectionCount: number
  arrivalQty: number
  qualifiedQty: number
  unqualifiedQty: number
  qualifiedRate: number
}

// ---------------- 查询参数 ----------------

export interface ArrivalQuery {
  pageNo?: number
  pageSize?: number
  inspectionNo?: string
  orderNo?: string
  orderId?: number
  supplierId?: number
  warehouseId?: number
  inspectionResult?: number
  status?: number
  startDate?: string
  endDate?: string
}

export interface ArrivalSaveBody {
  orderId: number
  arrivalDate: string
  warehouseId: number
  inspectionMode?: number
  remark?: string
  items: {
    orderItemId: number
    arrivalQty: number
    qualifiedQty?: number
    unqualifiedQty?: number
    batchNo?: string
    remark?: string
  }[]
}

export interface ConcessionBody {
  flag: boolean
  reason: string
}

export interface InboundGenerateBody {
  inboundDate?: string
  remark?: string
  /** 行级入库量；不传则入全部可入量，传了则只入显式列出的行 */
  items?: { inspectionItemId: number; quantity: number }[]
}

export interface ArrivalStatQuery {
  startDate?: string
  endDate?: string
  supplierId?: number
}

// ---------------- 接口 ----------------

/** M4-4-1 分页列表；数据范围按仓库维度注入（仓管员视角） */
export function arrivalPage(query: ArrivalQuery) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, PageResult<ArrivalRow>>('/m4/inspections/page', { params })
}

/** M4-4-2 详情：主表 + 明细行（带配额现算）+ 已生成入库单草稿 */
export function arrivalDetail(id: number) {
  return request.get<never, ArrivalRow>(`/m4/inspections/${id}`)
}

/** M4-4-3 验收结果统计（主管视角，跨仓库） */
export function arrivalStat(query: ArrivalStatQuery = {}) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, ArrivalStatRow[]>('/m4/inspections/stat', { params })
}

/** M4-1-1 选单下拉：可登记的已审核订单（含未到货明细与剩余到货量） */
export function arrivableOrders() {
  return request.get<never, ArrivalOrderRow[]>('/m4/inspections/arrivable-orders')
}

/** M4-1-1 单个订单的未到货明细 */
export function arrivableOrderItems(orderId: number) {
  return request.get<never, ArrivalOrderRow>(`/m4/inspections/arrivable-orders/${orderId}`)
}

/** M4-1-1 至 M4-1-3 新建到货登记（草稿） */
export function arrivalCreate(body: ArrivalSaveBody) {
  return request.post<never, number>('/m4/inspections', body)
}

/** M4-1 编辑到货登记（仅草稿） */
export function arrivalUpdate(id: number, body: ArrivalSaveBody) {
  return request.put<never, void>(`/m4/inspections/${id}`, body)
}

/** 删除草稿验收单（放弃未提交登记，挂 create 权限） */
export function arrivalDelete(id: number) {
  return request.delete<never, void>(`/m4/inspections/${id}`)
}

/** M4-2-5 验收单提交（守恒校验后进入已验收态） */
export function arrivalSubmit(id: number) {
  return request.post<never, void>(`/m4/inspections/${id}/submit`)
}

/** M4-2-4 让步接收标记（整单动作，归采购主管） */
export function arrivalConcession(id: number, body: ConcessionBody) {
  return request.post<never, void>(`/m4/inspections/${id}/concession`, body)
}

/** M4-3-1 至 M4-3-4 由已验收单生成入库单草稿，可多次生成 */
export function arrivalGenerateInbound(id: number, body: InboundGenerateBody) {
  return request.post<never, number>(`/m4/inspections/${id}/inbound`, body)
}

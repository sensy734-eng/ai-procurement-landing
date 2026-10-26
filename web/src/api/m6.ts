import request from './request'

/**
 * M6 价格参考与审批辅助 + M3-6 价格偏离关注接口封装。
 * 每个函数标注对应功能点编号，便于论文第 5 章按编号引用。
 * 页面编号对应功能模块清单 P15 价格参考、P16 偏离关注。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

// ---------------- M6-1-1 参考价 ----------------

/** 参考价行（含是否当前生效版标记） */
export interface PriceReferenceRow {
  id: number
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  refPrice: number
  effectiveDate: string
  priceVersion: number
  sourceType: number
  sourceTypeName: string
  status: number
  statusName: string
  current: boolean
  remark: string | null
  creatorName: string | null
  createdAt: string
}

export interface PriceReferenceSavePayload {
  id?: number
  materialId: number
  refPrice: number
  effectiveDate: string
  status?: number
  remark?: string
}

/** 详情：当前行 + 版本历史 */
export interface PriceReferenceDetail {
  reference: PriceReferenceRow
  versions: PriceReferenceRow[]
}

// ---------------- M6-1-2/1-3 成交统计 ----------------

/** 成交统计行：均价 + 最近成交 + 当前参考价（R6 分离展示） */
export interface PriceStatRow {
  materialId: number
  materialNo: string
  materialName: string
  avgPrice: number | null
  avgSampleCount: number | null
  lastPrice: number | null
  lastBillNo: string | null
  lastSampleCount: number | null
  statWindow: string | null
  calcTime: string | null
  refPrice: number | null
  refEffectiveDate: string | null
  avgDeviationRate: number | null
}

// ---------------- M3-6 偏离关注 ----------------

/** 偏离清单行（订单维度） */
export interface DeviationOrderRow {
  orderId: number
  orderNo: string
  supplierName: string | null
  deviationFlag: number
  deviationFlagName: string
  maxDeviationRate: number | null
  totalAmount: number | null
  createdAt: string
  items: DeviationItemRow[]
}

/** 偏离清单明细行 */
export interface DeviationItemRow {
  itemId: number
  materialId: number
  materialNo: string
  materialName: string
  unitPrice: number
  refPrice: number | null
  deviationRate: number | null
  deviationLevel: number
}

/** 偏离汇总 */
export interface DeviationSummary {
  focusOrders: number
  significantOrders: number
  totalOrders: number
  focusItems: number
  significantItems: number
  focusThreshold: number
  significantThreshold: number
}

// ---------------- M6-4-1 审批证据面板 ----------------

/** 证据面板行：单价/下单时点参考价/偏离/历史成交一屏 */
export interface EvidenceRow {
  itemId: number
  materialNo: string
  materialName: string
  spec: string | null
  quantity: number
  unitPrice: number
  amount: number
  refPrice: number | null
  refEffectiveDate: string | null
  deviationRate: number | null
  deviationLevel: number
  deviationLevelName: string
  avgPrice: number | null
  avgSampleCount: number | null
  lastPrice: number | null
  lastBillNo: string | null
  statWindow: string | null
  note: string | null
}

export interface EvidenceOrder {
  orderId: number
  orderNo: string
  supplierName: string | null
  totalAmount: number | null
  deviationFlag: number
  rows: EvidenceRow[]
}

// ---------------- 接口 ----------------

/** M6-1-1 参考价分页 */
export function listReferences(params: {
  materialId?: number
  status?: number
  pageNo?: number
  pageSize?: number
}) {
  return request.get('/m6/references', { params })
}

/** M6-1-1 参考价详情（含版本历史） */
export function getReference(id: number) {
  return request.get(`/m6/references/${id}`)
}

/** M6-1-1 物料版本历史 */
export function listVersions(materialId: number) {
  return request.get(`/m6/references/versions/${materialId}`)
}

/** M6-1-1 新建参考价（生成新版本） */
export function createReference(payload: PriceReferenceSavePayload) {
  return request.post('/m6/references', payload)
}

/** M6-1-1 更新（改价=新版本；改备注/停用=原地） */
export function updateReference(payload: PriceReferenceSavePayload) {
  return request.put('/m6/references', payload)
}

/** M6-1-2/1-3 成交统计查询（只读快照） */
export function listStats(params: { materialId?: number; supplierId?: number }) {
  return request.get('/m6/stats', { params })
}

/** M6-1-2/1-3 刷新统计快照（挂 m6:param:config） */
export function refreshStats(materialId?: number) {
  return request.post('/m6/stats/refresh', null, {
    params: materialId ? { materialId } : {}
  })
}

/** M3-6 偏离关注清单 */
export function listDeviations(params: {
  deviationLevel?: number
  materialId?: number
  orderNo?: string
  pageNo?: number
  pageSize?: number
}) {
  return request.get('/m6/deviations', { params })
}

/** M3-6 偏离汇总 */
export function getDeviationSummary() {
  return request.get('/m6/deviations/summary')
}

/** M3-6 偏离重算（参考价变更后同步，挂 m6:price:update） */
export function recalcDeviations(payload: { materialId?: number; orderId?: number } | null) {
  return request.post('/m6/deviations/recalc', payload ?? {})
}

/** M6-4-1 订单价格证据面板 */
export function getOrderEvidence(orderId: number) {
  return request.get(`/m6/evidence/order/${orderId}`)
}

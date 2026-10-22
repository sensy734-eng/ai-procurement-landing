import request from './request'

/**
 * M5 采购入库与库存接口封装。
 * 每个函数标注对应功能点编号，便于论文第 5 章按编号引用。
 * 页面编号对应功能模块清单 P12 入库单、P13 库存台账、P14 库存流水。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

// ---------------- M5-1 入库单 ----------------

/** 入库单明细行（详情/编辑态） */
export interface InboundItemRow {
  id?: number
  orderItemId: number | null
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  quantity: number
  unitId: number | null
  unitName: string | null
  batchNo: string
  locationId: number | null
  locationNo: string | null
  locationName: string | null
  ownerType: number
  ownerId: number | null
  ownerNo: string | null
  remark: string | null
}

/** 入库单列表行/详情（M5-1-2） */
export interface InboundRow {
  id: number
  inboundNo: string
  inspectionId: number | null
  inspectionNo: string | null
  orderId: number | null
  orderNo: string | null
  supplierId: number | null
  supplierName: string | null
  warehouseId: number
  warehouseName: string
  inboundDate: string
  /** 状态：1 草稿、2 已确认、3 已作废 */
  status: number
  statusName: string
  confirmTime: string | null
  confirmByName: string | null
  voidTime: string | null
  voidReason: string | null
  remark: string | null
  totalQty: number
  items: InboundItemRow[]
  /** 详情：来源链，手工单（无来源）为 null */
  trace: TraceChain | null
  /** 详情：本单产生的库存流水（入库 + 可能的冲减） */
  flows: FlowBrief[]
  createdAt: string
  createdByName: string
}

/** 溯源链：入库单 → 验收单 → 订单 → 申请单（M5-1-5） */
export interface TraceChain {
  inboundId: number
  inboundNo: string
  inspectionId: number | null
  inspectionNo: string | null
  orderId: number | null
  orderNo: string | null
  requestId: number | null
  requestNo: string | null
  applicantName: string | null
  deptName: string | null
}

/** 流水摘要（M5-4-3 来源回溯列表态） */
export interface FlowBrief {
  id: number
  batchNo: string
  changeType: number
  changeTypeName: string
  changeQty: number
  beforeQty: number
  afterQty: number
  flowTime: string
  operatorName: string | null
}

export interface InboundQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  warehouseId?: number
  dateFrom?: string
  dateTo?: string
  status?: number
  orderId?: number
}

/** 新建/编辑入库单（M5-1-1；明细整体替换，批次与库位在此登记） */
export interface InboundSaveBody {
  warehouseId: number
  inboundDate?: string
  remark?: string
  items: {
    orderItemId?: number | null
    materialId: number
    quantity: number
    unitId?: number | null
    batchNo?: string
    locationId?: number | null
    ownerType?: number
    ownerId?: number | null
    ownerNo?: string | null
    remark?: string
  }[]
}

export interface InboundVoidBody {
  reason: string
}

// ---------------- M5-2 / M5-3 库存 ----------------

/** 库存余额行（M5-3-1；可用量随行现算 M5-3-2） */
export interface StockBalanceRow {
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  categoryName: string | null
  warehouseId: number
  warehouseName: string
  ownerType: number
  ownerTypeName: string | null
  ownerId: number | null
  ownerNo: string | null
  ownerName: string | null
  quantity: number
  frozenQty: number
  reservedQty: number
  availableQty: number
  /** 归属分组时该行展开的批次明细 */
  batches: BatchRow[] | null
}

export interface BatchRow {
  batchNo: string
  quantity: number
  frozenQty: number
  reservedQty: number
  availableQty: number
}

export interface StockQuery {
  pageNo?: number
  pageSize?: number
  materialId?: number
  warehouseId?: number
  materialKeyword?: string
  onlyPositive?: boolean
}

/** 库存预警行（M5-5-1 清单 / M5-5-2 统计通用） */
export interface StockAlertRow {
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  categoryName: string | null
  warehouseId: number | null
  warehouseName: string | null
  quantity: number
  availableQty: number
  safetyStock: number
  gapQty: number
  /** 仅统计行（groupBy=warehouse/category）有值 */
  materialCount: number | null
}

export interface StockAlertQuery {
  warehouseId?: number
  categoryId?: number
  groupBy?: string
}

/** 批次追溯（M5-2-3） */
export interface BatchTraceRow {
  inboundItemId: number
  inboundId: number
  inboundNo: string
  inboundStatus: number
  inboundStatusName: string
  inboundDate: string
  materialId: number
  materialNo: string
  materialName: string
  quantity: number
  warehouseId: number
  warehouseName: string
  orderId: number | null
  orderNo: string | null
  supplierId: number | null
  supplierName: string | null
  inspectionId: number | null
  inspectionNo: string | null
}

export interface BatchTrace {
  batchNo: string
  materialCount: number
  inboundCount: number
  rows: BatchTraceRow[]
}

// ---------------- M5-4 库存流水 ----------------

/** 库存流水分录（M5-4-2） */
export interface FlowRow {
  id: number
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  warehouseId: number
  warehouseName: string
  batchNo: string
  ownerType: number
  ownerTypeName: string | null
  ownerId: number | null
  ownerNo: string | null
  changeType: number
  changeTypeName: string
  changeQty: number
  beforeQty: number
  afterQty: number
  sourceType: number
  sourceTypeName: string | null
  sourceId: number | null
  sourceNo: string | null
  operatorName: string | null
  flowTime: string
  /** 来源单据摘要（M5-4-3） */
  source: {
    inboundNo: string
    orderId: number | null
    orderNo: string | null
    inspectionId: number | null
    inspectionNo: string | null
    inboundStatus: number | null
    inboundStatusName: string | null
  } | null
}

export interface FlowQuery {
  pageNo?: number
  pageSize?: number
  materialId?: number
  warehouseId?: number
  materialKeyword?: string
  sourceType?: number
  sourceId?: number
  batchNo?: string
  changeType?: number
  dateFrom?: string
  dateTo?: string
}

/** 对账结果（M5-4-4，归系统管理员） */
export interface ReconcileRow {
  materialId: number
  materialNo: string
  materialName: string
  warehouseId: number
  warehouseName: string
  batchNo: string
  ownerNo: string | null
  flowQty: number
  balanceQty: number
  diffQty: number
  matched: boolean
}

export interface ReconcileResult {
  asOf: string
  totalKeys: number
  matchedKeys: number
  diffKeys: number
  flowTotal: number
  balanceTotal: number
  rows: ReconcileRow[]
}

// ---------------- 接口 ----------------

/** M5-1-2 入库单列表：单号/仓库/日期/状态筛选，按数据范围过滤 */
export function inboundPage(query: InboundQuery) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, PageResult<InboundRow>>('/m5/inbounds/page', { params })
}

/** M5-1-2 详情：主表 + 明细 + 来源链 + 库存流水摘要 */
export function inboundDetail(id: number) {
  return request.get<never, InboundRow>(`/m5/inbounds/${id}`)
}

/** M5-1-1 手工新建入库单（草稿） */
export function inboundCreate(body: InboundSaveBody) {
  return request.post<never, number>('/m5/inbounds', body)
}

/** M5-1-1 编辑入库单：仅草稿可编辑，明细整体替换 */
export function inboundUpdate(id: number, body: InboundSaveBody) {
  return request.put<never, void>(`/m5/inbounds/${id}`, body)
}

/** M5-1-3 确认入库：库存唯一写入点（R2） */
export function inboundConfirm(id: number) {
  return request.post<never, void>(`/m5/inbounds/${id}/confirm`)
}

/** M5-1-4 作废入库单：归采购主管，冲减库存并回退已交付 */
export function inboundVoid(id: number, body: InboundVoidBody) {
  return request.post<never, void>(`/m5/inbounds/${id}/void`, body)
}

/** M5-1-5 入库单溯源 */
export function inboundTrace(id: number) {
  return request.get<never, TraceChain>(`/m5/inbounds/${id}/trace`)
}

/** M5-2-1 批次号规则生成：B + yyyyMMdd + 3 位当日流水 */
export function nextBatchNo() {
  return request.get<never, string>('/m5/inbounds/next-batch-no')
}

/** M5-3-1 库存余额查询（物料/仓库/关键字） */
export function stockBalancePage(query: StockQuery) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, PageResult<StockBalanceRow>>('/m5/stock/balance', { params })
}

/** M5-3-2 可用量查询：物料 + 仓库 */
export function stockAvailable(materialId: number, warehouseId: number) {
  return request.get<never, StockBalanceRow>('/m5/stock/available', {
    params: { materialId, warehouseId }
  })
}

/** M5-3-3 按归属对象分组查询 */
export function stockOwnerGroup(query: StockQuery) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, PageResult<StockBalanceRow>>('/m5/stock/owner-group', { params })
}

/** M5-3-4 库存余额导出：CSV（UTF-8 BOM） */
export function stockExportUrl(query: StockQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([k, v]) => {
    if (v !== '' && v !== undefined && v !== null) params.append(k, String(v))
  })
  return `/api/m5/stock/export?${params.toString()}`
}

/** M5-5-1 库存不足清单：可用量低于预警下限的物料与缺口 */
export function stockAlert(query: StockAlertQuery = {}) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, StockAlertRow[]>('/m5/stock/alert', { params })
}

/** M5-5-2 短缺物料统计：groupBy=warehouse 按仓库、category 按分类 */
export function stockAlertStat(query: StockAlertQuery = {}) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, StockAlertRow[]>('/m5/stock/alert-stat', { params })
}

/** M5-2-3 批次追溯：批次号 → 入库明细 → 入库单 → 订单 → 供应商 */
export function batchTrace(batchNo: string) {
  return request.get<never, BatchTrace>('/m5/stock/batch-trace', { params: { batchNo } })
}

/** M5-4-2 库存流水分页 */
export function flowPage(query: FlowQuery) {
  const params: Record<string, unknown> = { ...query }
  Object.keys(params).forEach(k => {
    if (params[k] === '' || params[k] === undefined) delete params[k]
  })
  return request.get<never, PageResult<FlowRow>>('/m5/flows/page', { params })
}

/** M5-4-3 来源单据回溯：流水分录 → 对应入库单详情 */
export function flowSource(id: number) {
  return request.get<never, InboundRow>(`/m5/flows/${id}/source`)
}

/** M5-4-4 余额与流水对账（归系统管理员） */
export function flowReconcile(asOf?: string, warehouseId?: number) {
  const params: Record<string, unknown> = {}
  if (asOf) params.asOf = asOf
  if (warehouseId) params.warehouseId = warehouseId
  return request.get<never, ReconcileResult>('/m5/flows/reconcile', { params })
}

import request, { downloadCsv } from './request'

/**
 * M12 管理后台与数据可视化接口封装。
 * 页面编号对应功能模块清单 P25 后台首页、P26 采购运营仪表盘。
 */

// ---------------- M12-1 后台首页 ----------------

export interface OverviewEntry {
  title: string
  path: string
  perm: string
  countLabel: string | null
  count: number | null
  description: string
}

export interface OverviewGroup {
  key: string
  title: string
  icon: string
  entries: OverviewEntry[]
}

export interface OverviewMetric {
  key: string
  label: string
  value: string
  unit: string
  hint: string
}

export interface Overview {
  groups: OverviewGroup[]
  metrics: OverviewMetric[]
  scopeHint: string
  realName: string
  roleNames: string[]
}

/** M12-1-1 / M12-1-2 后台首页：五类维护入口与指标卡，入口由后端按权限过滤 */
export function getOverview() {
  return request.get<never, Overview>('/m12/overview')
}

// ---------------- M12-3 采购运营可视化 ----------------

export interface ChartQuery {
  startDate?: string
  endDate?: string
  topN?: number
  warehouseId?: number
  categoryId?: number
}

export interface StatusBucket {
  code: string
  name: string
  count: number
  amount: number
  ratio: number | null
}

export interface StatusChart {
  orderCount: number
  amount: number
  buckets: StatusBucket[]
  excluded: StatusBucket[]
  startDate: string | null
  endDate: string | null
}

/** M12-3-1 采购订单状态分布（五个业务桶 + 不计入的草稿与作废） */
export function getStatusChart(params: ChartQuery) {
  return request.get<never, StatusChart>('/m12/charts/status', { params })
}

export interface TrendPoint {
  month: string
  label: string
  orderCount: number
  amount: number
}

export interface TrendChart {
  points: TrendPoint[]
  maxAmount: number
  totalAmount: number
  totalOrders: number
  startDate: string
  endDate: string
}

/** M12-3-2 采购金额月度趋势（近 12 个月，缺月补零） */
export function getTrendChart(params: ChartQuery) {
  return request.get<never, TrendChart>('/m12/charts/trend', { params })
}

export interface SupplierRankRow {
  rank: number
  supplierId: number
  supplierNo: string
  supplierName: string
  orderCount: number
  amount: number
  ratio: number
}

export interface SupplierRank {
  rows: SupplierRankRow[]
  totalAmount: number
  supplierCount: number
  topN: number
  startDate: string | null
  endDate: string | null
}

/** M12-3-3 供应商采购金额排名（Top-N，占比分母是区间总金额） */
export function getSupplierRank(params: ChartQuery) {
  return request.get<never, SupplierRank>('/m12/charts/supplier', { params })
}

export interface StockAlertRow {
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  categoryName: string | null
  quantity: number
  availableQty: number
  safetyStock: number
  gapQty: number
}

export interface StockAlertChart {
  materialCount: number
  totalGap: number
  rows: StockAlertRow[]
}

/** M12-3-4 库存不足物料统计（口径与 M5-5-1 库存预警一致） */
export function getStockAlertChart(params: ChartQuery) {
  return request.get<never, StockAlertChart>('/m12/charts/stock-alert', { params })
}

// ---------------- M12-2 汇总查询与导出 ----------------

export interface SummaryQuery {
  groupBy?: string
  startDate?: string
  endDate?: string
  supplierId?: number
  materialId?: number
  lifecycleStatus?: number
  auditStatus?: number
  fulfillStatus?: number
  warehouseId?: number
  categoryId?: number
  ownerNo?: string
}

export interface PurchaseSummaryRow {
  groupKey: string
  groupName: string
  orderCount: number
  itemCount: number | null
  qty: number
  amount: number
}

export interface StockSummaryRow {
  groupKey: string
  groupName: string
  materialCount: number
  rowCount: number
  quantity: number
  frozenQty: number
  reservedQty: number
  availableQty: number
}

/** M12-2-1 采购单据汇总：groupBy=status/supplier/material/month */
export function getPurchaseSummary(params: SummaryQuery) {
  return request.get<never, PurchaseSummaryRow[]>('/m12/summary/purchase', { params })
}

/** M12-2-2 库存汇总：groupBy=warehouse/category/owner */
export function getStockSummary(params: SummaryQuery) {
  return request.get<never, StockSummaryRow[]>('/m12/summary/stock', { params })
}

/** M12-2-3 汇总结果导出（CSV，UTF-8 BOM），type=purchase|stock */
export function exportSummary(type: 'purchase' | 'stock', params: SummaryQuery) {
  const query = new URLSearchParams({ type })
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, String(value))
    }
  })
  const filename = type === 'stock' ? '库存汇总.csv' : '采购单据汇总.csv'
  return downloadCsv(`/api/m12/summary/export?${query.toString()}`, filename)
}

<template>
  <div class="page-card dashboard-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">采购运营仪表盘</h2>
        <p class="text-sub">
          P26｜M12-3：四图共用一套筛选条件联动刷新；金额类只统计已审核及之后的订单，
          库存不足与 M5 库存预警同一判定
        </p>
      </div>
      <div class="actions">
        <el-tag v-if="overview" size="small" effect="plain">数据范围：{{ overview.scopeHint }}</el-tag>
        <el-button :icon="Refresh" :loading="loadingCharts" @click="loadCharts">刷新</el-button>
      </div>
    </header>

    <!-- M12-3-5 时间范围与筛选联动：改任一项，四图一起重取 -->
    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="时间范围">
        <el-radio-group v-model="rangePreset" size="small" @change="onPresetChange">
          <el-radio-button label="12m">近 12 个月</el-radio-button>
          <el-radio-button label="3m">近 3 个月</el-radio-button>
          <el-radio-button label="year">今年</el-radio-button>
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="custom">自定义</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="dateRange" type="daterange" size="small" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 230px"
                        :disabled="rangePreset !== 'custom'" @change="onDateChange" />
      </el-form-item>
      <el-form-item label="仓库">
        <el-select v-model="warehouseId" size="small" placeholder="全部仓库" clearable style="width: 120px"
                   @change="loadAlert">
          <el-option v-for="item in warehouses" :key="item.id" :label="item.warehouseName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="categoryId" size="small" placeholder="全部分类" clearable style="width: 120px"
                   @change="loadAlert">
          <el-option v-for="item in categories" :key="item.id" :label="item.categoryName" :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <!-- 四张图（M12-3-1 至 M12-3-4） -->
    <section class="chart-grid">
      <div class="chart-card">
        <div class="chart-head">
          <div>
            <h3>采购订单状态分布</h3>
            <p class="text-sub">M12-3-1｜三状态字段组合成五个业务桶，草稿与作废不计入</p>
          </div>
          <el-tag v-if="status" size="small" type="info" effect="plain">
            {{ status.orderCount }} 单 · {{ formatMoney(status.amount) }} 元
          </el-tag>
        </div>
        <PieChart v-if="canStatus && status" :items="statusItems" unit="单" />
        <div v-else class="no-perm">当前角色没有 m12:chart:status 权限</div>
        <div v-if="status && status.excluded.length" class="excluded">
          不计入：
          <span v-for="item in status.excluded" :key="item.code" class="excluded-item">
            {{ item.name }} {{ item.count }} 单（{{ formatMoney(item.amount) }} 元）
          </span>
        </div>
      </div>

      <div class="chart-card">
        <div class="chart-head">
          <div>
            <h3>采购金额月度趋势</h3>
            <p class="text-sub">M12-3-2｜已审核及之后订单按月汇总，缺月补零</p>
          </div>
          <el-tag v-if="trend" size="small" type="info" effect="plain">
            合计 {{ formatMoney(trend.totalAmount) }} 元
          </el-tag>
        </div>
        <LineChart v-if="canTrend && trend" :points="trend.points.map((p) => ({
          label: p.label, amount: p.amount, orderCount: p.orderCount, month: p.month }))" />
        <div v-else class="no-perm">当前角色没有 m12:chart:trend 权限</div>
      </div>

      <div class="chart-card">
        <div class="chart-head">
          <div>
            <h3>供应商采购金额排名</h3>
            <p class="text-sub">M12-3-3｜Top-{{ topN }}，占比分母是区间全部已成交金额</p>
          </div>
          <el-select v-model="topN" size="small" style="width: 92px" @change="loadSupplier">
            <el-option v-for="n in [5, 10, 20]" :key="n" :label="'Top ' + n" :value="n" />
          </el-select>
        </div>
        <BarChart v-if="canSupplier && supplier" :items="supplierItems" label-width="150" />
        <div v-else class="no-perm">当前角色没有 m12:chart:supplier 权限</div>
        <div v-if="supplier && supplier.supplierCount > supplier.rows.length" class="chart-foot">
          区间共 {{ supplier.supplierCount }} 家供应商，仅展示前 {{ supplier.rows.length }} 家
        </div>
      </div>

      <div class="chart-card">
        <div class="chart-head">
          <div>
            <h3>库存不足物料</h3>
            <p class="text-sub">M12-3-4｜可用量低于预警下限，缺口 = 下限 − 可用量</p>
          </div>
          <div v-if="alertData" class="alert-metric">
            <span class="metric-value">{{ alertData.materialCount }}</span>
            <span class="metric-label">个物料 / 缺口合计 {{ fmtQty(alertData.totalGap) }}</span>
          </div>
        </div>
        <BarChart v-if="canAlert && alertData" :items="alertItems" label-width="118" />
        <div v-else class="no-perm">当前角色没有 m12:chart:stockalert 权限</div>
        <div v-if="warehouseId || categoryId" class="chart-foot">
          已按{{ warehouseId ? '所选仓库单独' : '' }}{{ warehouseId && categoryId ? '、' : '' }}{{
            categoryId ? '所选分类' : '' }}口径计算：其他仓库的数量不计入，未入库到所选仓库的物料按 0 计。
        </div>
      </div>
    </section>

    <!-- M12-2 汇总查询与导出 -->
    <section class="summary-section">
      <div class="summary-head">
        <div>
          <h3>汇总查询</h3>
          <p class="text-sub">M12-2-1 / M12-2-2｜与上方图表同口径，可切换分组维度并导出 CSV</p>
        </div>
        <div class="summary-actions">
          <el-radio-group v-model="summaryType" size="small" @change="onSummaryTypeChange">
            <el-radio-button label="purchase" :disabled="!canPurchaseSummary">采购单据</el-radio-button>
            <el-radio-button label="stock" :disabled="!canStockSummary">库存</el-radio-button>
          </el-radio-group>
          <el-select v-model="summaryGroup" size="small" style="width: 110px" @change="loadSummary">
            <el-option v-for="item in groupOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button v-if="canExport" size="small" :icon="Download" :loading="exporting" @click="onExport">
            导出 CSV
          </el-button>
        </div>
      </div>

      <el-table v-if="summaryType === 'purchase'" :data="purchaseRows" size="small" border
                v-loading="loadingSummary" empty-text="暂无数据">
        <el-table-column prop="groupName" label="分组" min-width="180" />
        <el-table-column prop="orderCount" label="单据数" width="90" align="right" />
        <el-table-column v-if="summaryGroup === 'material'" prop="itemCount" label="明细行数"
                         width="100" align="right" />
        <el-table-column label="数量合计" width="130" align="right">
          <template #default="{ row }">{{ fmtQty(row.qty) }}</template>
        </el-table-column>
        <el-table-column label="金额合计" width="140" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="stockRows" size="small" border v-loading="loadingSummary"
                empty-text="暂无数据">
        <el-table-column prop="groupName" label="分组" min-width="160" />
        <el-table-column prop="materialCount" label="涉及物料" width="100" align="right" />
        <el-table-column prop="rowCount" label="明细行数" width="100" align="right" />
        <el-table-column label="数量" width="120" align="right">
          <template #default="{ row }">{{ fmtQty(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="冻结" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.frozenQty) }}</template>
        </el-table-column>
        <el-table-column label="预留" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.reservedQty) }}</template>
        </el-table-column>
        <el-table-column label="可用量" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">{{ fmtQty(row.availableQty) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="summary-foot">
        <span v-if="summaryType === 'purchase'">
          合计 {{ purchaseTotals.orders }} 单 / {{ formatMoney(purchaseTotals.amount) }} 元
        </span>
        <span v-else>
          合计 {{ stockTotals.materials }} 个物料 / 可用量 {{ fmtQty(stockTotals.available) }}
        </span>
        <span class="foot-hint">
          {{ summaryType === 'purchase'
            ? '汇总含草稿与已作废单据，上方状态分布的五个业务桶不含这两类；导出与页面同一查询'
            : '可用量 = 数量 − 冻结 − 预留，与库存台账现算口径一致；导出与页面同一查询' }}
        </span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BarChart from './charts/BarChart.vue'
import LineChart from './charts/LineChart.vue'
import PieChart from './charts/PieChart.vue'
import {
  exportSummary,
  getOverview,
  getPurchaseSummary,
  getStatusChart,
  getStockAlertChart,
  getStockSummary,
  getSupplierRank,
  getTrendChart
} from '@/api/m12'
import type {
  Overview,
  PurchaseSummaryRow,
  StatusChart,
  StockAlertChart,
  StockSummaryRow,
  SupplierRank,
  TrendChart
} from '@/api/m12'
import { categoryTree, warehouseOptions } from '@/api/m1'
import type { CategoryNode } from '@/api/m1'
import { useUserStore } from '@/stores/user'

const store = useUserStore()

const canStatus = computed(() => store.hasPerm('m12:chart:status'))
const canTrend = computed(() => store.hasPerm('m12:chart:trend'))
const canSupplier = computed(() => store.hasPerm('m12:chart:supplier'))
const canAlert = computed(() => store.hasPerm('m12:chart:stockalert'))
const canPurchaseSummary = computed(() => store.hasPerm('m12:summary:purchase'))
const canStockSummary = computed(() => store.hasPerm('m12:summary:stock'))
const canExport = computed(() => store.hasPerm('m12:summary:export'))

const overview = ref<Overview | null>(null)
const status = ref<StatusChart | null>(null)
const trend = ref<TrendChart | null>(null)
const supplier = ref<SupplierRank | null>(null)
const alertData = ref<StockAlertChart | null>(null)

const loadingCharts = ref(false)
const loadingSummary = ref(false)
const exporting = ref(false)

const rangePreset = ref<'12m' | '3m' | 'year' | 'all' | 'custom'>('12m')
const dateRange = ref<[string, string] | null>(null)
const topN = ref(10)
const warehouseId = ref<number | undefined>()
const categoryId = ref<number | undefined>()

const warehouses = ref<{ id: number; warehouseName: string }[]>([])
const categories = ref<{ id: number; categoryName: string }[]>([])

const summaryType = ref<'purchase' | 'stock'>('purchase')
const summaryGroup = ref('status')
const purchaseRows = ref<PurchaseSummaryRow[]>([])
const stockRows = ref<StockSummaryRow[]>([])

const STATUS_COLORS = ['#d4a017', '#2e6da4', '#7c9ab8', '#2e7d5b', '#9ca3af']

const statusItems = computed(() =>
  (status.value?.buckets || []).map((item, index) => ({
    name: item.name,
    value: item.count,
    color: STATUS_COLORS[index % STATUS_COLORS.length]
  }))
)

const supplierItems = computed(() =>
  (supplier.value?.rows || []).map((item) => ({
    label: item.supplierName,
    value: item.amount,
    display: shortMoney(item.amount),
    sub: (item.ratio * 100).toFixed(1) + '%'
  }))
)

const alertItems = computed(() =>
  (alertData.value?.rows || []).map((item) => ({
    label: item.materialName,
    value: item.gapQty,
    display: fmtQty(item.gapQty),
    sub: `可用 ${fmtQty(item.availableQty)} / 下限 ${fmtQty(item.safetyStock)}`,
    color: '#c0392b'
  }))
)

const groupOptions = computed(() =>
  summaryType.value === 'purchase'
    ? [
        { label: '按状态', value: 'status' },
        { label: '按供应商', value: 'supplier' },
        { label: '按物料', value: 'material' },
        { label: '按月份', value: 'month' }
      ]
    : [
        { label: '按仓库', value: 'warehouse' },
        { label: '按分类', value: 'category' },
        { label: '按归属', value: 'owner' }
      ]
)

const purchaseTotals = computed(() => ({
  orders: purchaseRows.value.reduce((sum, row) => sum + (row.orderCount || 0), 0),
  amount: purchaseRows.value.reduce((sum, row) => sum + Number(row.amount || 0), 0)
}))

const stockTotals = computed(() => ({
  materials: stockRows.value.reduce((sum, row) => sum + (row.materialCount || 0), 0),
  available: stockRows.value.reduce((sum, row) => sum + Number(row.availableQty || 0), 0)
}))

function shortMoney(value: number) {
  if (!value) return '0'
  if (value >= 10000) return (value / 10000).toFixed(1) + ' 万'
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function formatMoney(value: number | null | undefined) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function fmtQty(value: number | null | undefined) {
  const num = Number(value || 0)
  return Number.isInteger(num) ? String(num) : num.toFixed(2).replace(/\.?0+$/, '')
}

/** 时间范围由预设推算；自定义时用日期控件的值，未选日期则退回近 12 个月 */
const rangeParams = computed<{ startDate?: string; endDate?: string }>(() => {
  if (rangePreset.value === 'all') {
    return {}
  }
  if (rangePreset.value === 'custom' && dateRange.value) {
    return { startDate: dateRange.value[0], endDate: dateRange.value[1] }
  }
  const today = new Date()
  const end = formatDate(today)
  if (rangePreset.value === 'year') {
    return { startDate: `${today.getFullYear()}-01-01`, endDate: end }
  }
  const months = rangePreset.value === '3m' ? 2 : 11
  const start = new Date(today.getFullYear(), today.getMonth() - months, 1)
  return { startDate: formatDate(start), endDate: end }
})

function formatDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function onPresetChange(value: string | number | boolean | undefined) {
  if (value !== 'custom') {
    dateRange.value = null
  }
  loadCharts()
}

function onDateChange() {
  if (dateRange.value) {
    rangePreset.value = 'custom'
    loadCharts()
  }
}

/** 四图并行取数：任一接口失败不影响其他图，各自保留上一次数据 */
async function loadCharts() {
  loadingCharts.value = true
  const params = rangeParams.value
  const tasks: Promise<void>[] = []
  if (canStatus.value) {
    tasks.push(getStatusChart(params).then((data) => { status.value = data }).catch(() => { status.value = null }))
  }
  if (canTrend.value) {
    tasks.push(getTrendChart(params).then((data) => { trend.value = data }).catch(() => { trend.value = null }))
  }
  if (canSupplier.value) {
    tasks.push(getSupplierRank({ ...params, topN: topN.value })
      .then((data) => { supplier.value = data }).catch(() => { supplier.value = null }))
  }
  await Promise.all(tasks)
  await loadAlert()
  loadingCharts.value = false
}

async function loadAlert() {
  if (!canAlert.value) {
    return
  }
  try {
    alertData.value = await getStockAlertChart({
      warehouseId: warehouseId.value,
      categoryId: categoryId.value
    })
  } catch {
    alertData.value = null
  }
}

async function loadSupplier() {
  if (!canSupplier.value) {
    return
  }
  try {
    supplier.value = await getSupplierRank({ ...rangeParams.value, topN: topN.value })
  } catch {
    supplier.value = null
  }
}

function onSummaryTypeChange() {
  summaryGroup.value = summaryType.value === 'purchase' ? 'status' : 'warehouse'
  loadSummary()
}

async function loadSummary() {
  if (summaryType.value === 'purchase') {
    if (!canPurchaseSummary.value) {
      return
    }
    loadingSummary.value = true
    try {
      purchaseRows.value = await getPurchaseSummary({ ...rangeParams.value, groupBy: summaryGroup.value })
    } catch {
      purchaseRows.value = []
    } finally {
      loadingSummary.value = false
    }
    return
  }
  if (!canStockSummary.value) {
    return
  }
  loadingSummary.value = true
  try {
    stockRows.value = await getStockSummary({
      groupBy: summaryGroup.value,
      warehouseId: warehouseId.value,
      categoryId: categoryId.value
    })
  } catch {
    stockRows.value = []
  } finally {
    loadingSummary.value = false
  }
}

async function onExport() {
  exporting.value = true
  try {
    await exportSummary(summaryType.value, {
      ...rangeParams.value,
      groupBy: summaryGroup.value,
      warehouseId: warehouseId.value,
      categoryId: categoryId.value
    })
    ElMessage.success('导出已开始')
  } catch (error) {
    ElMessage.error((error as Error).message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function loadOptions() {
  // 两个下拉来自 M1，各自按权限门控：无权限不发请求，下拉降级为空
  if (store.hasPerm('m1:warehouse:list')) {
    try {
      const list = await warehouseOptions()
      warehouses.value = (list || []).map((item) => ({ id: item.id, warehouseName: item.warehouseName }))
    } catch {
      warehouses.value = []
    }
  }
  if (store.hasPerm('m1:material_category:list')) {
    try {
      const tree = await categoryTree()
      categories.value = flattenCategories(tree || [])
    } catch {
      categories.value = []
    }
  }
}

function flattenCategories(nodes: CategoryNode[]): { id: number; categoryName: string }[] {
  const result: { id: number; categoryName: string }[] = []
  nodes.forEach((node) => {
    result.push({ id: node.id, categoryName: node.categoryName })
    if (node.children && node.children.length) {
      result.push(...flattenCategories(node.children))
    }
  })
  return result
}

onMounted(async () => {
  try {
    overview.value = await getOverview()
  } catch {
    overview.value = null
  }
  if (!canPurchaseSummary.value && canStockSummary.value) {
    summaryType.value = 'stock'
    summaryGroup.value = 'warehouse'
  }
  // 仓库与分类下拉来自 M1，无 M1 查询权限时不发请求（避免 403 冒泡）
  if (store.hasPerm('m1:warehouse:list') || store.hasPerm('m1:material_category:list')) {
    await loadOptions()
  }
  await loadCharts()
  await loadSummary()
})
</script>

<style scoped>
.dashboard-page {
  padding: 16px 20px 24px;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.toolbar .actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-bar {
  padding: 8px 12px 0;
  background: #fafbfd;
  border: 1px solid var(--border);
  border-radius: 6px;
  margin-bottom: 14px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.chart-card {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 14px 16px 10px;
  background: #ffffff;
}

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.chart-head h3 {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 600;
}

.chart-head .text-sub {
  margin: 0;
  font-size: 12px;
}

.no-perm {
  padding: 60px 0;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.excluded {
  font-size: 12px;
  color: var(--text-sub);
  padding-top: 6px;
}

.excluded-item {
  margin-right: 10px;
}

.chart-foot {
  font-size: 12px;
  color: var(--text-sub);
  padding-top: 6px;
}

.alert-metric {
  display: flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}

.metric-value {
  font-size: 22px;
  font-weight: 600;
  color: #c0392b;
}

.metric-label {
  font-size: 12px;
  color: var(--text-sub);
}

.summary-section {
  margin-top: 16px;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 14px 16px;
  background: #ffffff;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.summary-head h3 {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 600;
}

.summary-head .text-sub {
  margin: 0;
  font-size: 12px;
}

.summary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  font-size: 12px;
  color: var(--text-sub);
}

.foot-hint {
  color: #9ca3af;
}

.amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1280px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>

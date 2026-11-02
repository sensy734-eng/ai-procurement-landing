<template>
  <div class="page-card stock-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">库存台账<span class="page-code">M5</span></h2>
        <p class="text-sub">余额与可用量、归属分组、预警与批次追溯</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m5:stock:available')" :icon="Search" @click="availableVisible = true">
          可用量查询
        </el-button>
        <el-button v-if="store.hasPerm('m5:batch:trace')" :icon="Connection" @click="traceVisible = true">
          批次追溯
        </el-button>
        <el-button v-if="store.hasPerm('m5:stock:export')" :icon="Download" @click="exportCsv">
          导出 CSV
        </el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="stock-tabs">
      <!-- 库存余额 -->
      <el-tab-pane label="库存余额" name="balance">
        <el-form :inline="true" class="filter-bar" @submit.prevent>
          <el-form-item label="物料关键字">
            <el-input
              v-model="balanceQuery.materialKeyword"
              placeholder="编码 / 名称"
              clearable
              style="width: 180px"
              @keyup.enter="loadBalance(1)"
              @clear="loadBalance(1)"
            />
          </el-form-item>
          <el-form-item label="仓库">
            <el-select v-model="balanceQuery.warehouseId" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="零库存">
            <el-select v-model="balanceQuery.onlyPositive" placeholder="不限" clearable style="width: 110px">
              <el-option label="隐藏零库存" :value="true" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadBalance(1)">查询</el-button>
            <el-button :icon="Refresh" @click="resetBalanceQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="balanceLoading" :data="balanceRows" border stripe>
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="分类" width="100" show-overflow-tooltip />
          <el-table-column prop="warehouseName" label="仓库" width="100" />
          <el-table-column prop="ownerNo" label="归属" width="90" show-overflow-tooltip />
          <el-table-column prop="quantity" label="库存数量" width="100" />
          <el-table-column prop="frozenQty" label="冻结" width="90" />
          <el-table-column prop="reservedQty" label="预留" width="90" />
          <el-table-column label="可用量" width="100">
            <template #default="{ row }">
              <span :class="{ 'low-stock': row.availableQty <= 0 }">{{ row.availableQty }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="balanceQuery.pageNo"
            v-model:page-size="balanceQuery.pageSize"
            :total="balanceTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadBalance"
            @size-change="loadBalance(1)"
          />
        </div>
      </el-tab-pane>

      <!-- 归属分组 -->
      <el-tab-pane label="归属分组" name="owner">
        <el-alert class="scope-alert" type="info" :closable="false" show-icon
          title="公共库存与项目/工单库存分开呈现；每行展开该归属下的批次明细（批次展开见 M5-2-3 追溯）。" />
        <el-table v-loading="ownerLoading" :data="ownerRows" border stripe>
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="warehouseName" label="仓库" width="100" />
          <el-table-column label="归属类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.ownerType === 1 ? 'info' : 'warning'" size="small">
                {{ row.ownerTypeName || (row.ownerType === 1 ? '公共库存' : '专项归属') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ownerNo" label="归属编号" width="120" show-overflow-tooltip />
          <el-table-column prop="ownerName" label="归属名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="quantity" label="库存数量" width="100" />
          <el-table-column label="可用量" width="100">
            <template #default="{ row }">
              <span>{{ row.availableQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次明细" min-width="220">
            <template #default="{ row }">
              <template v-if="row.batches && row.batches.length">
                <el-tag v-for="b in row.batches" :key="b.batchNo" size="small" style="margin-right: 4px">
                  {{ b.batchNo || '无批次' }}：{{ b.quantity }}
                </el-tag>
              </template>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="ownerQuery.pageNo"
            v-model:page-size="ownerQuery.pageSize"
            :total="ownerTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadOwner"
            @size-change="loadOwner(1)"
          />
        </div>
      </el-tab-pane>

      <!-- 库存预警 -->
      <el-tab-pane label="库存预警" name="alert">
        <el-alert class="scope-alert" type="warning" :closable="false" show-icon
          title="可用量低于物料预警下限即列入短缺；缺口 = 预警下限 − 可用量（现算不落库）。" />
        <div class="alert-toolbar">
          <el-select v-model="alertGroupBy" placeholder="汇总维度" style="width: 140px" @change="loadAlert">
            <el-option label="按物料明细" value="" />
            <el-option label="按仓库汇总" value="warehouse" />
            <el-option label="按分类汇总" value="category" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="loadAlert">刷新</el-button>
        </div>
        <el-table v-loading="alertLoading" :data="alertRows" border stripe>
          <template v-if="alertGroupBy">
            <el-table-column prop="warehouseName" label="仓库" width="120" />
            <el-table-column prop="materialCount" label="短缺物料数" width="110" />
            <el-table-column prop="gapQty" label="缺口合计" width="110" />
          </template>
          <template v-else>
            <el-table-column prop="materialNo" label="物料编码" width="110" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
            <el-table-column prop="categoryName" label="分类" width="100" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" width="100" />
            <el-table-column prop="quantity" label="库存数量" width="100" />
            <el-table-column prop="availableQty" label="可用量" width="90" />
            <el-table-column prop="safetyStock" label="预警下限" width="90" />
            <el-table-column label="缺口" width="90">
              <template #default="{ row }">
                <span class="gap-qty">{{ row.gapQty }}</span>
              </template>
            </el-table-column>
          </template>
        </el-table>
        <el-empty v-if="!alertLoading && !alertRows.length" description="暂无短缺物料" :image-size="50" />
      </el-tab-pane>
    </el-tabs>

    <!-- 可用量查询对话框 -->
    <el-dialog v-model="availableVisible" title="可用量查询" width="480px">
      <el-form label-width="80px" class="dialog-form">
        <el-form-item label="物料" required>
          <el-select v-model="availableForm.materialId" placeholder="选择物料" filterable style="width: 100%">
            <el-option v-for="m in materialOptions" :key="m.id"
              :label="`${m.materialNo} ${m.materialName}`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" required>
          <el-select v-model="availableForm.warehouseId" placeholder="选择仓库" style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="availableLoading" @click="queryAvailable">
            查询
          </el-button>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="availableResult" :column="2" border>
        <el-descriptions-item label="库存数量">{{ availableResult.quantity }}</el-descriptions-item>
        <el-descriptions-item label="冻结数量">{{ availableResult.frozenQty }}</el-descriptions-item>
        <el-descriptions-item label="预留数量">{{ availableResult.reservedQty }}</el-descriptions-item>
        <el-descriptions-item label="可用量">
          <strong>{{ availableResult.availableQty }}</strong>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert type="info" :closable="false" show-icon style="margin-top: 12px"
        title="可用量 = 库存数量 − 冻结 − 预留，查询时现算，不落库。" />
    </el-dialog>

    <!-- 批次追溯对话框 -->
    <el-dialog v-model="traceVisible" title="批次追溯" width="760px">
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item label="批次号">
          <el-input v-model="traceBatchNo" placeholder="输入批次号" clearable style="width: 220px"
            @keyup.enter="queryBatchTrace" @clear="traceResult = null" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="traceLoading" @click="queryBatchTrace">追溯</el-button>
        </el-form-item>
      </el-form>
      <template v-if="traceResult">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="批次号">{{ traceResult.batchNo || '（无批次）' }}</el-descriptions-item>
          <el-descriptions-item label="涉及物料数">{{ traceResult.materialCount }}</el-descriptions-item>
          <el-descriptions-item label="入库次数">{{ traceResult.inboundCount }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="block-title">入库记录链（批次 → 入库单 → 订单 → 供应商）</h4>
        <el-table :data="traceResult.rows" border size="small">
          <el-table-column prop="inboundNo" label="入库单号" width="190" />
          <el-table-column prop="inboundDate" label="入库日期" width="120" />
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="quantity" label="入库数量" width="90" />
          <el-table-column prop="warehouseName" label="仓库" width="100" />
          <el-table-column prop="orderNo" label="来源订单" width="180">
            <template #default="{ row }">
              <span>{{ row.orderNo || '手工单' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="供应商" min-width="140" show-overflow-tooltip />
        </el-table>
      </template>
      <el-empty v-else-if="!traceLoading" description="输入批次号后追溯入库记录链" :image-size="50" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Download, Refresh, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as m5Api from '@/api/m5'
import type { BatchTrace, StockAlertRow, StockBalanceRow } from '@/api/m5'
import * as m1Api from '@/api/m1'

const store = useUserStore()

// ---------------- 下拉数据 ----------------

const warehouseOptions = ref<{ id: number; warehouseNo: string; warehouseName: string }[]>([])
const materialOptions = ref<{ id: number; materialNo: string; materialName: string; unitId: number; unitName: string }[]>([])

// ---------------- 库存余额 ----------------

const activeTab = ref('balance')
const balanceLoading = ref(false)
const balanceRows = ref<StockBalanceRow[]>([])
const balanceTotal = ref(0)
const balanceQuery = reactive({
  materialKeyword: '',
  warehouseId: undefined as number | undefined,
  onlyPositive: undefined as boolean | undefined,
  pageNo: 1,
  pageSize: 10
})

async function loadBalance(pageNo?: number) {
  if (pageNo) balanceQuery.pageNo = pageNo
  balanceLoading.value = true
  try {
    const result = await m5Api.stockBalancePage({
      pageNo: balanceQuery.pageNo,
      pageSize: balanceQuery.pageSize,
      materialKeyword: balanceQuery.materialKeyword || undefined,
      warehouseId: balanceQuery.warehouseId,
      onlyPositive: balanceQuery.onlyPositive
    })
    balanceRows.value = result.records
    balanceTotal.value = result.total
  } finally {
    balanceLoading.value = false
  }
}

function resetBalanceQuery() {
  balanceQuery.materialKeyword = ''
  balanceQuery.warehouseId = undefined
  balanceQuery.onlyPositive = undefined
  loadBalance(1)
}

function exportCsv() {
  if (!store.hasPerm('m5:stock:export')) {
    ElMessage.warning('无导出权限（导出归采购主管）')
    return
  }
  window.open(m5Api.stockExportUrl({
    materialKeyword: balanceQuery.materialKeyword || undefined,
    warehouseId: balanceQuery.warehouseId
  }))
}

// ---------------- 归属分组 ----------------

const ownerLoading = ref(false)
const ownerRows = ref<StockBalanceRow[]>([])
const ownerTotal = ref(0)
const ownerQuery = reactive({
  pageNo: 1,
  pageSize: 10
})

async function loadOwner(pageNo?: number) {
  if (pageNo) ownerQuery.pageNo = pageNo
  ownerLoading.value = true
  try {
    const result = await m5Api.stockOwnerGroup({
      pageNo: ownerQuery.pageNo,
      pageSize: ownerQuery.pageSize
    })
    ownerRows.value = result.records
    ownerTotal.value = result.total
  } finally {
    ownerLoading.value = false
  }
}

// ---------------- 库存预警 ----------------

const alertLoading = ref(false)
const alertRows = ref<StockAlertRow[]>([])
const alertGroupBy = ref('')

async function loadAlert() {
  alertLoading.value = true
  try {
    alertRows.value = alertGroupBy.value
      ? await m5Api.stockAlertStat({ groupBy: alertGroupBy.value })
      : await m5Api.stockAlert({})
  } finally {
    alertLoading.value = false
  }
}

// ---------------- 可用量查询 ----------------

const availableVisible = ref(false)
const availableLoading = ref(false)
const availableResult = ref<StockBalanceRow | null>(null)
const availableForm = reactive({
  materialId: undefined as number | undefined,
  warehouseId: undefined as number | undefined
})

async function queryAvailable() {
  if (!availableForm.materialId || !availableForm.warehouseId) {
    ElMessage.warning('请选择物料与仓库')
    return
  }
  availableLoading.value = true
  try {
    availableResult.value = await m5Api.stockAvailable(
      availableForm.materialId,
      availableForm.warehouseId
    )
  } finally {
    availableLoading.value = false
  }
}

// ---------------- 批次追溯 ----------------

const traceVisible = ref(false)
const traceLoading = ref(false)
const traceBatchNo = ref('')
const traceResult = ref<BatchTrace | null>(null)

async function queryBatchTrace() {
  if (!traceBatchNo.value.trim()) {
    ElMessage.warning('请输入批次号')
    return
  }
  traceLoading.value = true
  try {
    traceResult.value = await m5Api.batchTrace(traceBatchNo.value.trim())
    if (!traceResult.value.rows.length) {
      ElMessage.info('该批次号暂无入库记录')
    }
  } finally {
    traceLoading.value = false
  }
}

onMounted(async () => {
  const [warehouses, materials] = await Promise.all([
    m1Api.warehouseOptions(),
    m1Api.materialOptions()
  ])
  warehouseOptions.value = warehouses
  materialOptions.value = materials
  loadBalance()
})
</script>

<style scoped>
.block-title {
  margin: 18px 0 10px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.dialog-form {
  margin-top: 12px;
}
.scope-alert {
  margin-bottom: 12px;
}
.alert-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.low-stock {
  color: var(--el-color-danger);
  font-weight: 600;
}
.gap-qty {
  color: var(--el-color-danger);
  font-weight: 600;
}
.stock-tabs {
  margin-top: 4px;
}
</style>

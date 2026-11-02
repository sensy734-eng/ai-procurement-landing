<template>
  <div class="page-card flow-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">库存流水<span class="page-code">M5</span></h2>
        <p class="text-sub">变动记录查询、来源单据回溯与余额对账</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m5:flow:reconcile')" :icon="ScaleToOriginal" @click="openReconcile">
          余额对账
        </el-button>
      </div>
    </header>

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="物料关键字">
        <el-input
          v-model="query.materialKeyword"
          placeholder="编码 / 名称"
          clearable
          style="width: 180px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="仓库">
        <el-select v-model="query.warehouseId" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="变动类型">
        <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 110px">
          <el-option label="入库" :value="1" />
          <el-option label="冲减" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="批次号">
        <el-input
          v-model="query.batchNo"
          placeholder="批次号"
          clearable
          style="width: 150px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="变动时间">
        <el-date-picker
          v-model="query.dateRange"
          type="daterange"
          range-separator="~"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="reload(1)">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      class="scope-alert"
      type="info"
      :closable="false"
      show-icon
      title="流水按数据范围过滤：仓管员只见管辖仓库的流水（部门维度账号失败关闭返回空）。每条分录记录变动前/后余额，是余额对账的依据。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="flowTime" label="变动时间" width="170" />
      <el-table-column prop="materialNo" label="物料编码" width="110" />
      <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="仓库" width="100" />
      <el-table-column prop="batchNo" label="批次号" width="140" />
      <el-table-column label="变动类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.changeType === 1 ? 'success' : 'danger'" size="small">
            {{ row.changeTypeName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="changeQty" label="变动数量" width="100" />
      <el-table-column label="变动前 → 变动后" width="150">
        <template #default="{ row }">
          <span>{{ row.beforeQty }} → {{ row.afterQty }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="sourceNo" label="来源单据" width="200" />
      <el-table-column prop="operatorName" label="操作人" width="90" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="store.hasPerm('m5:flow:trace')"
            link type="primary"
            @click="openSource(row)"
          >
            来源回溯
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="reload(1)"
      />
    </div>

    <!-- 来源单据回溯抽屉 -->
    <el-drawer v-model="sourceVisible" title="来源单据回溯" size="60%">
      <template v-if="sourceBill">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="入库单号">{{ sourceBill.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="来源订单">{{ sourceBill.orderNo || '手工单' }}</el-descriptions-item>
          <el-descriptions-item label="来源验收单">{{ sourceBill.inspectionNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ sourceBill.supplierName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="收货仓库">{{ sourceBill.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="入库日期">{{ sourceBill.inboundDate }}</el-descriptions-item>
          <el-descriptions-item label="入库数量">{{ sourceBill.totalQty }}</el-descriptions-item>
          <el-descriptions-item label="单据状态">
            <el-tag :type="sourceBill.status === 2 ? 'success' : sourceBill.status === 3 ? 'danger' : 'info'" size="small">
              {{ sourceBill.statusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="制单人">
            {{ sourceBill.createdByName }}（{{ sourceBill.createdAt }}）
          </el-descriptions-item>
        </el-descriptions>
        <h4 class="block-title">入库明细</h4>
        <el-table :data="sourceBill.items" border size="small">
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="quantity" label="入库数量" width="100" />
          <el-table-column prop="batchNo" label="批次号" width="150" />
          <el-table-column prop="locationNo" label="库位" width="120" />
          <el-table-column prop="ownerNo" label="归属" width="100" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>

    <!-- 余额对账对话框（归系统管理员） -->
    <el-dialog v-model="reconcileVisible" title="余额与流水对账" width="860px">
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item label="对账截止">
          <el-date-picker v-model="reconcileAsOf" type="date" value-format="YYYY-MM-DD"
            placeholder="默认今天" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="reconcileLoading" @click="runReconcile">
            开始对账
          </el-button>
        </el-form-item>
      </el-form>
      <template v-if="reconcile">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="截止时间">{{ reconcile.asOf }}</el-descriptions-item>
          <el-descriptions-item label="对账键数">{{ reconcile.totalKeys }}</el-descriptions-item>
          <el-descriptions-item label="一致键数">
            <span class="match-count">{{ reconcile.matchedKeys }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="差异键数">
            <span :class="{ 'diff-count': reconcile.diffKeys > 0 }">{{ reconcile.diffKeys }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="流水累计">{{ reconcile.flowTotal }}</el-descriptions-item>
          <el-descriptions-item label="余额合计">{{ reconcile.balanceTotal }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          class="reconcile-alert"
          :type="reconcile.diffKeys === 0 ? 'success' : 'error'"
          :closable="false"
          show-icon
          :title="reconcile.diffKeys === 0
            ? '对账通过：流水累计与当前余额逐键一致。'
            : `存在 ${reconcile.diffKeys} 个差异键，请逐行核查。`"
        />
        <h4 class="block-title">逐键比对（物料 + 仓库 + 批次 + 归属）</h4>
        <el-table :data="reconcile.rows" border size="small" max-height="360">
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="warehouseName" label="仓库" width="100" />
          <el-table-column prop="batchNo" label="批次号" width="140" />
          <el-table-column prop="ownerNo" label="归属" width="90" show-overflow-tooltip />
          <el-table-column prop="flowQty" label="流水累计" width="100" />
          <el-table-column prop="balanceQty" label="当前余额" width="100" />
          <el-table-column prop="diffQty" label="差异" width="90" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.matched ? 'success' : 'danger'" size="small">
                {{ row.matched ? '一致' : '差异' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else-if="!reconcileLoading" description="选择截止时间后开始对账" :image-size="50" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, ScaleToOriginal, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as m5Api from '@/api/m5'
import type { FlowRow, InboundRow, ReconcileResult } from '@/api/m5'
import * as m1Api from '@/api/m1'

const store = useUserStore()

const loading = ref(false)
const rows = ref<FlowRow[]>([])
const total = ref(0)
const warehouseOptions = ref<{ id: number; warehouseNo: string; warehouseName: string }[]>([])

const query = reactive({
  materialKeyword: '',
  warehouseId: undefined as number | undefined,
  changeType: undefined as number | undefined,
  batchNo: '',
  dateRange: [] as string[],
  pageNo: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const result = await m5Api.flowPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      materialKeyword: query.materialKeyword || undefined,
      warehouseId: query.warehouseId,
      changeType: query.changeType,
      batchNo: query.batchNo || undefined,
      dateFrom: query.dateRange?.[0],
      dateTo: query.dateRange?.[1]
    })
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function reload(pageNo: number) {
  query.pageNo = pageNo
  load()
}

function resetQuery() {
  query.materialKeyword = ''
  query.warehouseId = undefined
  query.changeType = undefined
  query.batchNo = ''
  query.dateRange = []
  reload(1)
}

// ---------------- 来源单据回溯 ----------------

const sourceVisible = ref(false)
const sourceBill = ref<InboundRow | null>(null)

async function openSource(row: FlowRow) {
  sourceBill.value = await m5Api.flowSource(row.id)
  sourceVisible.value = true
}

// ---------------- 余额对账 ----------------

const reconcileVisible = ref(false)
const reconcileLoading = ref(false)
const reconcile = ref<ReconcileResult | null>(null)
const reconcileAsOf = ref('')

async function openReconcile() {
  reconcileVisible.value = true
  await runReconcile()
}

async function runReconcile() {
  reconcileLoading.value = true
  try {
    reconcile.value = await m5Api.flowReconcile(reconcileAsOf.value || undefined)
  } finally {
    reconcileLoading.value = false
  }
}

onMounted(async () => {
  warehouseOptions.value = await m1Api.warehouseOptions()
  load()
})
</script>

<style scoped>
.block-title {
  margin: 18px 0 10px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.scope-alert {
  margin-bottom: 12px;
}
.reconcile-alert {
  margin-top: 12px;
}
.match-count {
  color: var(--el-color-success);
  font-weight: 600;
}
.diff-count {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>

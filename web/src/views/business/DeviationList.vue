<template>
  <div class="page-card deviation-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">价格偏离关注</h2>
        <p class="text-sub">P16｜M3-6：偏离率 =（单价 − 参考价）/ 参考价，按绝对值与阈值比较；整单取明细行最高等级</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m6:price:update')" :icon="RefreshLeft" :loading="recalculating" @click="recalcVisible = true">
          重算偏离
        </el-button>
      </div>
    </header>

    <!-- 汇总卡（M3-6） -->
    <el-row v-if="summary" :gutter="12" class="summary-row">
      <el-col :span="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">关注订单</div>
          <div class="summary-value dev-focus">{{ summary.focusOrders }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">显著订单</div>
          <div class="summary-value dev-significant">{{ summary.significantOrders }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">关注阈值</div>
          <div class="summary-value">{{ (summary.focusThreshold * 100).toFixed(0) }}%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">显著阈值</div>
          <div class="summary-value">{{ (summary.significantThreshold * 100).toFixed(0) }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert class="scope-alert" type="info" :closable="false" show-icon
      title="偏离在保存订单行时按当时生效参考价判定（快照）；参考价变更后不自动重算，点「重算偏离」按当前生效参考价同步。无生效参考价的物料不判定。" />

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="偏离等级">
        <el-select v-model="query.deviationLevel" placeholder="全部" clearable style="width: 120px">
          <el-option label="关注" :value="1" />
          <el-option label="显著" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="订单号">
        <el-input
          v-model="query.orderNo"
          placeholder="订单号关键字"
          clearable
          style="width: 200px"
          @keyup.enter="loadDeviations(1)"
          @clear="loadDeviations(1)"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="loadDeviations(1)">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.items" border size="small" class="inner-table">
            <el-table-column prop="materialNo" label="物料编码" width="110" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="订单单价" width="100">
              <template #default="{ row: item }">
                <span class="price-value">{{ item.unitPrice }}</span>
              </template>
            </el-table-column>
            <el-table-column label="偏离率" width="100">
              <template #default="{ row: item }">
                <span v-if="item.deviationRate !== null" :class="levelClass(item.deviationLevel)">
                  {{ formatRate(item.deviationRate) }}
                </span>
                <span v-else class="text-sub">未判定</span>
              </template>
            </el-table-column>
            <el-table-column label="等级" width="80" align="center">
              <template #default="{ row: item }">
                <el-tag :type="levelTagType(item.deviationLevel)" size="small">
                  {{ levelName(item.deviationLevel) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="订单号" width="185" fixed="left" />
      <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
      <el-table-column label="整单偏离" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.deviationFlag)" size="small">
            {{ row.deviationFlagName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最高偏离率" width="120">
        <template #default="{ row }">
          <span v-if="row.maxDeviationRate !== null" :class="levelClass(row.deviationFlag)">
            {{ formatRate(row.maxDeviationRate) }}
          </span>
          <span v-else class="text-sub">—</span>
        </template>
      </el-table-column>
      <el-table-column label="含税金额" width="120" align="right">
        <template #default="{ row }">{{ row.totalAmount }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="下单时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <router-link :to="`/order?orderId=${row.orderId}`">
            <el-link type="primary" :underline="false">查看订单</el-link>
          </router-link>
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
        @current-change="loadDeviations"
        @size-change="loadDeviations(1)"
      />
    </div>

    <!-- 重算对话框（M3-6） -->
    <el-dialog v-model="recalcVisible" title="重算偏离" width="440px">
      <el-alert type="warning" :closable="false" show-icon
        title="按当前生效参考价重算订单行偏离并同步整单标记，操作留痕。全量重算会覆盖所有订单行的既有判定。" />
      <el-form label-width="88px" style="margin-top: 12px">
        <el-form-item label="重算范围">
          <el-select v-model="recalcScope" placeholder="全部物料" clearable filterable style="width: 100%">
            <el-option
              v-for="m in materialOptions"
              :key="m.id"
              :label="`${m.materialNo} ${m.materialName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recalcVisible = false">取消</el-button>
        <el-button type="primary" :loading="recalculating" @click="submitRecalc">重算</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as m6Api from '@/api/m6'
import type { DeviationOrderRow, DeviationSummary } from '@/api/m6'
import * as m1Api from '@/api/m1'

const store = useUserStore()

const loading = ref(false)
const rows = ref<DeviationOrderRow[]>([])
const total = ref(0)
const summary = ref<DeviationSummary | null>(null)
const query = reactive({
  deviationLevel: undefined as number | undefined,
  orderNo: '',
  pageNo: 1,
  pageSize: 10
})

const materialOptions = ref<{ id: number; materialNo: string; materialName: string }[]>([])
const recalcVisible = ref(false)
const recalculating = ref(false)
const recalcScope = ref<number | undefined>(undefined)

async function loadDeviations(pageNo?: number) {
  if (pageNo) query.pageNo = pageNo
  loading.value = true
  try {
    const result = await m6Api.listDeviations({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      deviationLevel: query.deviationLevel,
      orderNo: query.orderNo || undefined
    })
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  summary.value = await m6Api.getDeviationSummary()
}

function resetQuery() {
  query.deviationLevel = undefined
  query.orderNo = ''
  loadDeviations(1)
}

async function submitRecalc() {
  recalculating.value = true
  try {
    const updated = await m6Api.recalcDeviations(
      recalcScope.value ? { materialId: recalcScope.value } : {}
    )
    ElMessage.success(`已重算，更新 ${updated} 行`)
    recalcVisible.value = false
    await loadDeviations(1)
    await loadSummary()
  } catch {
    // 拦截器已提示错误
  } finally {
    recalculating.value = false
  }
}

function formatRate(rate: number) {
  return (rate * 100).toFixed(2) + '%'
}

function levelName(level: number) {
  if (level === 2) return '显著'
  if (level === 1) return '关注'
  return '正常'
}

function levelTagType(level: number) {
  if (level === 2) return 'danger'
  if (level === 1) return 'warning'
  return 'success'
}

function levelClass(level: number) {
  if (level === 2) return 'dev-significant'
  if (level === 1) return 'dev-focus'
  return 'dev-normal'
}

onMounted(async () => {
  materialOptions.value = await m1Api.materialOptions()
  await loadDeviations(1)
  await loadSummary()
})
</script>

<style scoped>
.summary-row {
  margin-bottom: 12px;
}

.summary-card {
  text-align: center;
}

.summary-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.price-value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.inner-table {
  margin: 4px 24px 12px;
}

.dev-normal {
  color: var(--el-color-success);
}

.dev-focus {
  color: var(--el-color-warning);
  font-weight: 600;
}

.dev-significant {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>

<template>
  <div class="page-card price-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">价格参考</h2>
        <p class="text-sub">P15｜M6：参考价维护与版本、成交价统计快照（参考价为手工治理基准，与当前供应商报价严格分离）</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m6:param:config')" :icon="Refresh" @click="refreshStats">
          刷新成交统计
        </el-button>
        <el-button v-if="store.hasPerm('m6:price:create')" type="primary" :icon="Plus" @click="openCreate">
          新建参考价
        </el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="price-tabs">
      <!-- 参考价维护（M6-1-1） -->
      <el-tab-pane label="参考价维护" name="reference">
        <el-form :inline="true" class="filter-bar" @submit.prevent>
          <el-form-item label="物料">
            <el-select
              v-model="referenceQuery.materialId"
              placeholder="全部"
              clearable
              filterable
              style="width: 220px"
            >
              <el-option
                v-for="m in materialOptions"
                :key="m.id"
                :label="`${m.materialNo} ${m.materialName}`"
                :value="m.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="referenceQuery.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="生效" :value="1" />
              <el-option label="停用" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadReferences(1)">查询</el-button>
            <el-button :icon="Refresh" @click="resetReferenceQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="referenceLoading" :data="referenceRows" border stripe>
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
          <el-table-column label="参考价" width="100">
            <template #default="{ row }">
              <span class="price-value">{{ row.refPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="70" align="center">
            <template #default="{ row }">V{{ row.priceVersion }}</template>
          </el-table-column>
          <el-table-column prop="effectiveDate" label="生效日期" width="120" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前生效" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.current" type="warning" size="small">当前</el-tag>
              <span v-else class="text-sub">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="sourceTypeName" label="来源" width="90" />
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-if="store.hasPerm('m6:price:view')" link type="primary" @click="openDetail(row)">
                详情
              </el-button>
              <el-button v-if="store.hasPerm('m6:price:update')" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="referenceQuery.pageNo"
            v-model:page-size="referenceQuery.pageSize"
            :total="referenceTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadReferences"
            @size-change="loadReferences(1)"
          />
        </div>
      </el-tab-pane>

      <!-- 成交统计（M6-1-2 / M6-1-3） -->
      <el-tab-pane label="成交统计" name="stat">
        <el-alert class="scope-alert" type="info" :closable="false" show-icon
          title="统计快照由刷新任务写入、查询只读；均价按窗口内 Σ金额/Σ数量 加权计算，最近成交价带来源单号可溯源。无成交样本的物料不伪造数据。" />
        <el-table v-loading="statLoading" :data="statRows" border stripe>
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="历史均价" width="100">
            <template #default="{ row }">
              <span v-if="row.avgPrice !== null" class="price-value">{{ row.avgPrice }}</span>
              <span v-else class="text-sub">无样本</span>
            </template>
          </el-table-column>
          <el-table-column label="样本数" width="80" align="center">
            <template #default="{ row }">
              {{ row.avgSampleCount ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="最近成交价" width="100">
            <template #default="{ row }">
              <span v-if="row.lastPrice !== null" class="price-value">{{ row.lastPrice }}</span>
              <span v-else class="text-sub">无样本</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastBillNo" label="来源单号" width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.lastBillNo ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column prop="statWindow" label="统计窗口" width="130" />
          <el-table-column prop="calcTime" label="快照时间" width="170" />
          <el-table-column label="当前参考价" width="100">
            <template #default="{ row }">
              <span v-if="row.refPrice !== null" class="price-value">{{ row.refPrice }}</span>
              <span v-else class="text-sub">未设定</span>
            </template>
          </el-table-column>
          <el-table-column label="均价偏离" width="100">
            <template #default="{ row }">
              <span v-if="row.avgDeviationRate !== null" :class="deviationClass(row.avgDeviationRate)">
                {{ formatRate(row.avgDeviationRate) }}
              </span>
              <span v-else class="text-sub">—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 口径说明（M6-2） -->
      <el-tab-pane label="口径说明" name="rule">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="偏离率">
            （订单单价 − 参考价）/ 参考价；按绝对值与阈值比较，低于参考价过多同样计入偏离。
          </el-descriptions-item>
          <el-descriptions-item label="偏离等级">
            0 正常 / 1 关注（|偏离率| ≥ 关注阈值）/ 2 显著（|偏离率| ≥ 显著阈值）；整单取明细行最高等级。
          </el-descriptions-item>
          <el-descriptions-item label="参考价选价">
            按订单创建日选用当时生效的版本（同物料生效日不超过基准日的最新一版）；证据面板还原下单时点，历史版本停用后仍可追溯。
          </el-descriptions-item>
          <el-descriptions-item label="版本策略">
            改价格或生效日 = 生成新版本（旧版留痕）；立即生效的新版本停用旧生效版；未来生效的预售版与旧版并存，到期自动接任。
          </el-descriptions-item>
          <el-descriptions-item label="统计口径">
            窗口类型与取值、关注/显著阈值由系统参数（价格分组）固化；统计只取已审核通过的订单，草稿与审批中不计入。
          </el-descriptions-item>
          <el-descriptions-item label="阈值现状">
            关注阈值 {{ focusThreshold }}、显著阈值 {{ significantThreshold }}（参数管理页可改，改后可在偏离关注页重算）。
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建 / 编辑对话框（M6-1-1） -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑参考价' : '新建参考价'"
      width="480px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="88px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="form.materialId" placeholder="选择物料" filterable style="width: 100%">
            <el-option
              v-for="m in materialOptions"
              :key="m.id"
              :label="`${m.materialNo} ${m.materialName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="参考价" prop="refPrice">
          <el-input-number v-model="form.refPrice" :min="0" :precision="4" :step="0.01" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker
            v-model="form.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择生效日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="300" show-word-limit />
        </el-form-item>
        <el-alert v-if="editingId" type="warning" :closable="false" show-icon
          title="修改价格或生效日期将生成新版本，旧版本留痕；仅改备注则原地更新。" />
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：版本历史（M6-1-1） -->
    <el-drawer v-model="detailVisible" title="参考价详情与版本历史" size="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="物料">
            {{ detail.reference.materialNo }} {{ detail.reference.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="当前参考价">
            <span class="price-value">{{ detail.reference.refPrice }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ detail.reference.effectiveDate }}</el-descriptions-item>
          <el-descriptions-item label="版本">V{{ detail.reference.priceVersion }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="drawer-subtitle">版本历史（新版本在前，历史版本永久保留）</h4>
        <el-table :data="detail.versions" border stripe>
          <el-table-column label="版本" width="70" align="center">
            <template #default="{ row }">V{{ row.priceVersion }}</template>
          </el-table-column>
          <el-table-column prop="refPrice" label="参考价" width="100" />
          <el-table-column prop="effectiveDate" label="生效日期" width="120" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前生效" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.current" type="warning" size="small">当前</el-tag>
              <span v-else class="text-sub">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import * as m6Api from '@/api/m6'
import type { PriceReferenceDetail, PriceReferenceRow, PriceStatRow } from '@/api/m6'
import * as m1Api from '@/api/m1'

const store = useUserStore()

// ---------------- 下拉数据 ----------------

const materialOptions = ref<{ id: number; materialNo: string; materialName: string }[]>([])

// ---------------- 参考价（M6-1-1） ----------------

const activeTab = ref('reference')
const referenceLoading = ref(false)
const referenceRows = ref<PriceReferenceRow[]>([])
const referenceTotal = ref(0)
const referenceQuery = reactive({
  materialId: undefined as number | undefined,
  status: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10
})

async function loadReferences(pageNo?: number) {
  if (pageNo) referenceQuery.pageNo = pageNo
  referenceLoading.value = true
  try {
    const result = await m6Api.listReferences({
      pageNo: referenceQuery.pageNo,
      pageSize: referenceQuery.pageSize,
      materialId: referenceQuery.materialId,
      status: referenceQuery.status
    })
    referenceRows.value = result.records
    referenceTotal.value = result.total
  } finally {
    referenceLoading.value = false
  }
}

function resetReferenceQuery() {
  referenceQuery.materialId = undefined
  referenceQuery.status = undefined
  loadReferences(1)
}

// ---------------- 成交统计（M6-1-2 / M6-1-3） ----------------

const statLoading = ref(false)
const statRows = ref<PriceStatRow[]>([])
const focusThreshold = ref<number | null>(null)
const significantThreshold = ref<number | null>(null)

async function loadStats() {
  statLoading.value = true
  try {
    statRows.value = await m6Api.listStats({})
    const summary = await m6Api.getDeviationSummary()
    focusThreshold.value = summary.focusThreshold
    significantThreshold.value = summary.significantThreshold
  } finally {
    statLoading.value = false
  }
}

async function refreshStats() {
  try {
    const updated = await m6Api.refreshStats()
    ElMessage.success(`已刷新 ${updated} 个物料的成交统计`)
    await loadStats()
  } catch {
    // 拦截器已提示错误
  }
}

function formatRate(rate: number) {
  return (rate * 100).toFixed(2) + '%'
}

function deviationClass(rate: number) {
  const abs = Math.abs(rate)
  if (significantThreshold.value !== null && abs >= significantThreshold.value) return 'dev-significant'
  if (focusThreshold.value !== null && abs >= focusThreshold.value) return 'dev-focus'
  return 'dev-normal'
}

// ---------------- 新建 / 编辑（M6-1-1） ----------------

const formVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  materialId: undefined as number | undefined,
  refPrice: 0,
  effectiveDate: '',
  remark: ''
})
const formRules: FormRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  refPrice: [{ required: true, message: '请输入参考价', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
}

function openCreate() {
  editingId.value = null
  form.materialId = undefined
  form.refPrice = 0
  form.effectiveDate = ''
  form.remark = ''
  formVisible.value = true
}

function openEdit(row: PriceReferenceRow) {
  editingId.value = row.id
  form.materialId = row.materialId
  form.refPrice = row.refPrice
  form.effectiveDate = row.effectiveDate
  form.remark = row.remark ?? ''
  formVisible.value = true
}

function resetForm() {
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      id: editingId.value ?? undefined,
      materialId: form.materialId!,
      refPrice: form.refPrice,
      effectiveDate: form.effectiveDate,
      remark: form.remark || undefined
    }
    if (editingId.value) {
      await m6Api.updateReference(payload)
      ElMessage.success('已保存（改价将生成新版本）')
    } else {
      await m6Api.createReference(payload)
      ElMessage.success('已创建新版本参考价')
    }
    formVisible.value = false
    await loadReferences(1)
  } catch {
    // 拦截器已提示错误
  } finally {
    saving.value = false
  }
}

// ---------------- 详情抽屉 ----------------

const detailVisible = ref(false)
const detail = ref<PriceReferenceDetail | null>(null)

async function openDetail(row: PriceReferenceRow) {
  detail.value = await m6Api.getReference(row.id)
  detailVisible.value = true
}

onMounted(async () => {
  materialOptions.value = await m1Api.materialOptions()
  await loadReferences(1)
  await loadStats()
})
</script>

<style scoped>
.price-value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.price-tabs {
  margin-top: 4px;
}

.drawer-subtitle {
  margin: 16px 0 8px;
  font-size: 14px;
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

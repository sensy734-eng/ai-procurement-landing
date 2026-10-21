<template>
  <div class="page-card arrival-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">到货验收</h2>
        <p class="text-sub">P10｜M4 到货验收：到货登记、合格判定、让步接收与入库单生成</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m4:inspection:create')" type="primary" :icon="Plus" @click="openRegister">
          新建到货登记
        </el-button>
        <el-button v-if="store.hasPerm('m4:inspection:stat')" :icon="DataLine" @click="openStat">
          验收统计
        </el-button>
      </div>
    </header>

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="验收单号">
        <el-input
          v-model="query.inspectionNo"
          placeholder="验收单号模糊匹配"
          clearable
          style="width: 200px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="订单号">
        <el-input
          v-model="query.orderNo"
          placeholder="订单号模糊匹配"
          clearable
          style="width: 200px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="验收结果">
        <el-select v-model="query.inspectionResult" placeholder="全部" clearable style="width: 120px">
          <el-option label="合格" :value="1" />
          <el-option label="不合格" :value="2" />
          <el-option label="让步接收" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="单据状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
          <el-option label="草稿" :value="1" />
          <el-option label="已验收" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="到货日期">
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
      title="列表按数据范围过滤：仓管员只见管辖仓库的验收单（部门维度账号失败关闭返回空）；统计为跨仓库质量视角，归采购主管（M4-4-3）。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="inspectionNo" label="验收单号" width="185" fixed="left" />
      <el-table-column prop="orderNo" label="来源订单" width="185" />
      <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="收货仓库" width="100" />
      <el-table-column prop="arrivalDate" label="到货日期" width="120" />
      <el-table-column label="验收方式" width="90">
        <template #default="{ row }">
          <span>{{ row.inspectionModeName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="验收结果" width="110">
        <template #default="{ row }">
          <el-tag v-if="!row.inspectionResult" type="info" size="small">未判定</el-tag>
          <el-tag v-else-if="row.inspectionResult === 1" type="success" size="small">
            {{ row.inspectionResultName }}
          </el-tag>
          <el-tag v-else-if="row.inspectionResult === 2" type="danger" size="small">
            {{ row.inspectionResultName }}
          </el-tag>
          <el-tag v-else type="warning" size="small">{{ row.inspectionResultName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="合格 / 不合格" width="150">
        <template #default="{ row }">
          <span>{{ row.qualifiedQty }} / {{ row.unqualifiedQty }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单据状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 2 ? 'success' : 'info'" size="small">
            {{ row.statusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdByName" label="制单人" width="90" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m4:inspection:view')" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button
            v-if="store.hasPerm('m4:inspection:update') && row.status === 1"
            link type="primary"
            @click="openRegister(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="store.hasPerm('m4:inspection:submit') && row.status === 1"
            link type="success"
            @click="submitBill(row)"
          >
            提交
          </el-button>
          <el-button
            v-if="store.hasPerm('m4:inspection:concession') && row.status === 2"
            link type="warning"
            @click="openConcession(row)"
          >
            让步
          </el-button>
          <el-button
            v-if="store.hasPerm('m4:inbound:generate') && row.status === 2"
            link type="primary"
            @click="openInbound(row)"
          >
            生成入库单
          </el-button>
          <el-button
            v-if="store.hasPerm('m4:inspection:create') && row.status === 1"
            link type="danger"
            @click="removeBill(row)"
          >
            删除
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

    <!-- 详情抽屉：明细带配额现算 + 已生成入库单草稿 -->
    <el-drawer v-model="detailVisible" title="到货验收单详情" size="72%">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="验收单号">{{ detail.inspectionNo }}</el-descriptions-item>
          <el-descriptions-item label="来源订单">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="收货仓库">{{ detail.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="到货日期">{{ detail.arrivalDate }}</el-descriptions-item>
          <el-descriptions-item label="验收方式">{{ detail.inspectionModeName }}</el-descriptions-item>
          <el-descriptions-item label="验收结果">
            <el-tag v-if="!detail.inspectionResult" type="info" size="small">未判定</el-tag>
            <el-tag v-else-if="detail.inspectionResult === 1" type="success" size="small">
              {{ detail.inspectionResultName }}
            </el-tag>
            <el-tag v-else-if="detail.inspectionResult === 2" type="danger" size="small">
              {{ detail.inspectionResultName }}
            </el-tag>
            <el-tag v-else type="warning" size="small">{{ detail.inspectionResultName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="让步理由">
            {{ detail.concessionReason || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="制单人">
            {{ detail.createdByName }}（{{ detail.createdAt }}）
          </el-descriptions-item>
          <el-descriptions-item label="验收人">{{ detail.inspectorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="block-title">验收明细（配额为服务端现算，不落库）</h4>
        <el-table :data="detail.items" border size="small">
          <el-table-column prop="orderItemNo" label="订单行" width="70" />
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
          <el-table-column prop="unitName" label="单位" width="60" />
          <el-table-column prop="arrivalQty" label="本次到货" width="90" />
          <el-table-column prop="qualifiedQty" label="合格" width="80" />
          <el-table-column prop="unqualifiedQty" label="不合格" width="80" />
          <el-table-column prop="batchNo" label="批次" width="100" />
          <el-table-column prop="orderQty" label="订单量" width="80" />
          <el-table-column prop="arrivedQty" label="累计到货" width="90" />
          <el-table-column prop="remainingArrival" label="剩余到货" width="90" />
          <el-table-column prop="inboundableQty" label="可入量" width="80" />
          <el-table-column prop="ownerNo" label="归属" width="110" show-overflow-tooltip />
        </el-table>

        <h4 class="block-title">已生成的入库单草稿（多批次可有多张，确认入库在 M5）</h4>
        <el-table :data="detail.inboundOrders" border size="small">
          <el-table-column prop="inboundNo" label="入库单号" width="185" />
          <el-table-column prop="inboundDate" label="入库日期" width="120" />
          <el-table-column prop="warehouseName" label="仓库" width="100" />
          <el-table-column prop="totalQty" label="入库数量" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 2 ? 'success' : 'info'" size="small">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="制单人" width="90" />
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!detail.inboundOrders.length" description="尚未生成入库单" :image-size="50" />
      </template>
    </el-drawer>

    <!-- 让步接收对话框（M4-2-4，整单动作） -->
    <el-dialog v-model="concessionVisible" title="让步接收标记" width="480px">
      <el-alert type="warning" :closable="false" show-icon
        title="让步是整单动作：一单多行不能只让一行；打标后不合格数量也可生成入库单。" />
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="验收单号">
          <span>{{ current?.inspectionNo }}</span>
        </el-form-item>
        <el-form-item label="不合格数量">
          <span>{{ current?.unqualifiedQty }}</span>
        </el-form-item>
        <el-form-item label="让步理由" required>
          <el-input v-model="concessionForm.reason" type="textarea" :rows="3"
            placeholder="请填写让步接收理由（必填）" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="concessionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitConcession(true)">打让步标记</el-button>
      </template>
    </el-dialog>

    <!-- 生成入库单对话框（M4-3，缺省全量，改量即部分入库） -->
    <el-dialog v-model="inboundVisible" title="生成入库单草稿" width="720px">
      <template v-if="current">
        <el-alert :closable="false" show-icon type="info"
          :title="current.concessionFlag === 1
            ? '让步接收单：入库量 = 合格 + 不合格；可多次生成直到可入量耗尽（多批次）。'
            : '非让步单：入库量只取合格数量；可多次生成直到可入量耗尽（多批次）。'" />
        <el-form label-width="90px" class="dialog-form">
          <el-form-item label="入库日期">
            <el-date-picker v-model="inboundForm.inboundDate" type="date" value-format="YYYY-MM-DD"
              placeholder="默认今天" style="width: 160px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="inboundForm.remark" maxlength="500" style="width: 320px" />
          </el-form-item>
        </el-form>
        <el-table :data="inboundRows" border size="small">
          <el-table-column prop="orderItemNo" label="订单行" width="70" />
          <el-table-column prop="materialName" label="物料" min-width="130" show-overflow-tooltip />
          <el-table-column prop="qualifiedQty" label="合格" width="80" />
          <el-table-column prop="unqualifiedQty" label="不合格" width="80" />
          <el-table-column prop="inboundableQty" label="可入量" width="80" />
          <el-table-column label="本次入库量" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.qty" :min="0" :max="row.inboundableQty" :precision="4"
                :step="1" size="small" controls-position="right" style="width: 120px" />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="inboundVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitInbound">生成草稿</el-button>
      </template>
    </el-dialog>

    <!-- 验收统计对话框（M4-4-3） -->
    <el-dialog v-model="statVisible" title="验收结果统计（按供应商）" width="720px">
      <el-form :inline="true" class="filter-bar" @submit.prevent>
        <el-form-item label="统计周期">
          <el-date-picker v-model="statRange" type="daterange" range-separator="~"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadStat">统计</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="statLoading" :data="statRows" border size="small">
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column prop="inspectionCount" label="验收单数" width="90" />
        <el-table-column prop="arrivalQty" label="到货合计" width="110" />
        <el-table-column prop="qualifiedQty" label="合格合计" width="110" />
        <el-table-column prop="unqualifiedQty" label="不合格合计" width="110" />
        <el-table-column label="合格率" width="100">
          <template #default="{ row }">
            {{ (row.qualifiedRate * 100).toFixed(2) }}%
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!statLoading && !statRows.length" description="周期内暂无已验收单据" :image-size="50" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataLine, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as m4Api from '@/api/m4'
import type { ArrivalItemRow, ArrivalRow, ArrivalStatRow } from '@/api/m4'

const store = useUserStore()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref<ArrivalRow[]>([])
const total = ref(0)

const query = reactive({
  inspectionNo: '',
  orderNo: '',
  inspectionResult: undefined as number | undefined,
  status: undefined as number | undefined,
  dateRange: [] as string[],
  pageNo: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const result = await m4Api.arrivalPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      inspectionNo: query.inspectionNo || undefined,
      orderNo: query.orderNo || undefined,
      inspectionResult: query.inspectionResult,
      status: query.status,
      startDate: query.dateRange?.[0],
      endDate: query.dateRange?.[1]
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
  query.inspectionNo = ''
  query.orderNo = ''
  query.inspectionResult = undefined
  query.status = undefined
  query.dateRange = []
  reload(1)
}

// ---------------- 详情 ----------------

const detailVisible = ref(false)
const detail = ref<ArrivalRow | null>(null)

async function openDetail(row: ArrivalRow) {
  detail.value = await m4Api.arrivalDetail(row.id)
  detailVisible.value = true
}

// ---------------- 登记页跳转 ----------------

function openRegister(row?: ArrivalRow) {
  if (row) {
    router.push(`/arrival/register?id=${row.id}`)
  } else {
    router.push('/arrival/register')
  }
}

// ---------------- 提交（M4-2-5） ----------------

async function submitBill(row: ArrivalRow) {
  await ElMessageBox.confirm(
    `提交后验收单进入已验收态，明细与判定不可再修改。确认提交 ${row.inspectionNo}？`,
    '提交验收单',
    { type: 'warning' }
  )
  await m4Api.arrivalSubmit(row.id)
  ElMessage.success('已提交，进入可入库状态')
  load()
}

// ---------------- 删除草稿 ----------------

async function removeBill(row: ArrivalRow) {
  await ElMessageBox.confirm(
    `删除后占到货配额释放。确认删除草稿 ${row.inspectionNo}？`,
    '删除草稿',
    { type: 'warning' }
  )
  await m4Api.arrivalDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ---------------- 让步接收（M4-2-4） ----------------

const concessionVisible = ref(false)
const current = ref<ArrivalRow | null>(null)
const concessionForm = reactive({ reason: '' })

function openConcession(row: ArrivalRow) {
  current.value = row
  concessionForm.reason = ''
  concessionVisible.value = true
}

async function submitConcession(flag: boolean) {
  if (!current.value) return
  saving.value = true
  try {
    await m4Api.arrivalConcession(current.value.id, { flag, reason: concessionForm.reason })
    ElMessage.success(flag ? '已打让步标记' : '已取消让步')
    concessionVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 生成入库单（M4-3） ----------------

const inboundVisible = ref(false)
const inboundForm = reactive({ inboundDate: '', remark: '' })
const inboundRows = ref<(ArrivalItemRow & { qty: number })[]>([])

function openInbound(row: ArrivalRow) {
  current.value = row
  inboundForm.inboundDate = ''
  inboundForm.remark = ''
  inboundRows.value = row.items
    .filter((item) => item.inboundableQty > 0)
    .map((item) => ({ ...item, qty: item.inboundableQty }))
  inboundVisible.value = true
}

async function submitInbound() {
  if (!current.value) return
  saving.value = true
  try {
    const full = inboundRows.value.every((item) => item.qty === item.inboundableQty)
    const items = full
      ? undefined
      : inboundRows.value
          .filter((item) => item.qty > 0)
          .map((item) => ({ inspectionItemId: item.id as number, quantity: item.qty }))
    const id = await m4Api.arrivalGenerateInbound(current.value.id, {
      inboundDate: inboundForm.inboundDate || undefined,
      remark: inboundForm.remark || undefined,
      items
    })
    ElMessage.success(`已生成入库单草稿（id=${id}），确认入库在 M5 采购入库`)
    inboundVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 验收统计（M4-4-3） ----------------

const statVisible = ref(false)
const statLoading = ref(false)
const statRows = ref<ArrivalStatRow[]>([])
const statRange = ref<string[]>([])

async function openStat() {
  statVisible.value = true
  await loadStat()
}

async function loadStat() {
  statLoading.value = true
  try {
    statRows.value = await m4Api.arrivalStat({
      startDate: statRange.value?.[0],
      endDate: statRange.value?.[1]
    })
  } finally {
    statLoading.value = false
  }
}

onMounted(load)
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
</style>

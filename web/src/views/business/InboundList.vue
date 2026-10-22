<template>
  <div class="page-card inbound-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">采购入库</h2>
        <p class="text-sub">P12｜M5 采购入库：入库单维护、确认（库存唯一写入点）、作废与溯源</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m5:inbound:create')" type="primary" :icon="Plus" @click="openCreate">
          新建入库单
        </el-button>
      </div>
    </header>

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="入库单号 / 订单号"
          clearable
          style="width: 200px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="收货仓库">
        <el-select v-model="query.warehouseId" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="单据状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
          <el-option label="草稿" :value="1" />
          <el-option label="已确认" :value="2" />
          <el-option label="已作废" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="入库日期">
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
      title="列表按数据范围过滤：仓管员只见管辖仓库的入库单（部门维度账号失败关闭返回空）。确认入库是库存唯一写入点（R2）：同事务写余额、流水并回写订单已交付数量。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="inboundNo" label="入库单号" width="200" fixed="left" />
      <el-table-column prop="orderNo" label="来源订单" width="185">
        <template #default="{ row }">
          <span>{{ row.orderNo || '手工单' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="supplierName" label="供应商" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.supplierName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="warehouseName" label="收货仓库" width="100" />
      <el-table-column prop="inboundDate" label="入库日期" width="120" />
      <el-table-column prop="totalQty" label="入库数量" width="100" />
      <el-table-column label="单据状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 2 ? 'success' : row.status === 3 ? 'danger' : 'info'" size="small">
            {{ row.statusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdByName" label="制单人" width="90" />
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m5:inbound:view')" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button
            v-if="store.hasPerm('m5:inbound:update') && row.status === 1"
            link type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="store.hasPerm('m5:inbound:confirm') && row.status === 1"
            link type="success"
            @click="confirmBill(row)"
          >
            确认入库
          </el-button>
          <el-button
            v-if="store.hasPerm('m5:inbound:trace')"
            link type="primary"
            @click="openTrace(row)"
          >
            溯源
          </el-button>
          <el-button
            v-if="store.hasPerm('m5:inbound:void') && row.status !== 3"
            link type="danger"
            @click="openVoid(row)"
          >
            作废
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

    <!-- 详情抽屉：明细 + 来源链 + 库存流水摘要 -->
    <el-drawer v-model="detailVisible" title="入库单详情" size="72%">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="入库单号">{{ detail.inboundNo }}</el-descriptions-item>
          <el-descriptions-item label="来源订单">{{ detail.orderNo || '手工单' }}</el-descriptions-item>
          <el-descriptions-item label="来源验收单">{{ detail.inspectionNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detail.supplierName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="收货仓库">{{ detail.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="入库日期">{{ detail.inboundDate }}</el-descriptions-item>
          <el-descriptions-item label="入库数量">{{ detail.totalQty }}</el-descriptions-item>
          <el-descriptions-item label="单据状态">
            <el-tag :type="detail.status === 2 ? 'success' : detail.status === 3 ? 'danger' : 'info'" size="small">
              {{ detail.statusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="确认信息">
            {{ detail.confirmByName ? `${detail.confirmByName}（${detail.confirmTime}）` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="制单人">
            {{ detail.createdByName }}（{{ detail.createdAt }}）
          </el-descriptions-item>
          <el-descriptions-item label="作废原因" :span="detail.voidReason ? 2 : 1">
            {{ detail.voidReason || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="block-title">入库明细（批次与库位在编辑时登记）</h4>
        <el-table :data="detail.items" border size="small">
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
          <el-table-column prop="unitName" label="单位" width="60" />
          <el-table-column prop="quantity" label="入库数量" width="90" />
          <el-table-column prop="batchNo" label="批次号" width="140" />
          <el-table-column prop="locationNo" label="库位" width="120">
            <template #default="{ row }">
              <span>{{ row.locationNo || '未登记' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="ownerNo" label="归属" width="110" show-overflow-tooltip />
        </el-table>

        <h4 class="block-title">库存流水（本单确认/作废产生，M5-4-1）</h4>
        <el-table :data="detail.flows" border size="small">
          <el-table-column prop="flowTime" label="时间" width="170" />
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
          <el-table-column prop="operatorName" label="操作人" width="90" />
        </el-table>
        <el-empty v-if="!detail.flows.length" description="尚未确认，暂无库存流水" :image-size="50" />
      </template>
    </el-drawer>

    <!-- 溯源对话框（M5-1-5） -->
    <el-dialog v-model="traceVisible" title="入库单溯源" width="560px">
      <template v-if="traceData">
        <el-steps direction="vertical" :active="traceActive">
          <el-step title="入库单" :description="traceData.inboundNo" />
          <el-step title="到货验收单" :description="traceData.inspectionNo || '手工单，无验收来源'" />
          <el-step title="采购订单" :description="traceData.orderNo || '手工单，无订单来源'" />
          <el-step title="采购申请单" :description="traceData.requestNo || '—'" />
        </el-steps>
        <el-descriptions :column="2" border style="margin-top: 12px">
          <el-descriptions-item label="申请人">{{ traceData.applicantName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="申请部门">{{ traceData.deptName || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="traceVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 作废对话框（M5-1-4，归采购主管） -->
    <el-dialog v-model="voidVisible" title="作废入库单" width="480px">
      <template v-if="current">
        <el-alert type="warning" :closable="false" show-icon
          :title="current.status === 2
            ? '已确认入库单作废将冲减库存并写冲减流水，同时回退订单已交付数量。'
            : '作废后单据不可恢复。'" />
        <el-form label-width="90px" class="dialog-form">
          <el-form-item label="入库单号">
            <span>{{ current.inboundNo }}</span>
          </el-form-item>
          <el-form-item label="作废原因" required>
            <el-input v-model="voidForm.reason" type="textarea" :rows="3"
              placeholder="请填写作废原因（必填）" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="voidVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitVoid">确认作废</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑对话框（M5-1-1；批次号与库位在此登记，M5-2-1） -->
    <el-dialog v-model="formVisible" :title="editing ? '编辑入库单' : '新建入库单'" width="860px">
      <template v-if="form">
        <el-form label-width="90px" class="dialog-form">
          <el-form-item label="收货仓库" required>
            <el-select v-model="form.warehouseId" placeholder="选择仓库" style="width: 200px" @change="onWarehouseChange">
              <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="入库日期">
            <el-date-picker v-model="form.inboundDate" type="date" value-format="YYYY-MM-DD"
              placeholder="默认今天" style="width: 160px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" maxlength="500" style="width: 320px" />
          </el-form-item>
        </el-form>

        <div class="item-toolbar">
          <span class="block-title" style="margin: 0">入库明细</span>
          <el-button v-if="!editing" size="small" :icon="Plus" @click="addItem">添加行</el-button>
        </div>
        <el-table :data="form.items" border size="small">
          <el-table-column label="物料" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.materialId" placeholder="选择物料" filterable style="width: 100%"
                :disabled="!!row.orderItemId" @change="(v: number) => onMaterialChange(row, v)">
                <el-option v-for="m in materialOptions" :key="m.id"
                  :label="`${m.materialNo} ${m.materialName}`" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="入库数量" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0.0001" :precision="4" :step="1"
                size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="60">
            <template #default="{ row }">
              <span>{{ row.unitName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次号" width="220">
            <template #default="{ row }">
              <div class="batch-cell">
                <el-input v-model="row.batchNo" placeholder="批次号" size="small" style="width: 120px" />
                <el-button size="small" link type="primary" @click="fillBatchNo(row)">生成</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="库位" width="150">
            <template #default="{ row }">
              <el-select v-model="row.locationId" placeholder="选择库位" filterable size="small" style="width: 100%">
                <el-option v-for="l in locationOptions" :key="l.id"
                  :label="`${l.locationNo} ${l.locationName || ''}`" :value="l.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="归属" width="150">
            <template #default="{ row }">
              <el-select v-model="row.ownerKey" placeholder="公共库存" size="small" style="width: 100%"
                @change="(v: string) => onOwnerChange(row, v)">
                <el-option label="公共库存" value="public" />
                <el-option v-for="o in ownerOptions" :key="o.id" :label="o.ownerName" :value="String(o.id)" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button v-if="!editing || true" link type="danger" size="small" @click="removeItem($index)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert class="dialog-tip" type="info" :closable="false" show-icon
          title="确认入库时逐行校验：入库数量须大于 0 且已登记库位；超订单剩余入库配额将被拒绝。" />
      </template>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as m5Api from '@/api/m5'
import type { InboundRow, InboundItemRow, TraceChain } from '@/api/m5'
import * as m1Api from '@/api/m1'
import { ownerOptions as loadOwnerOptions } from '@/api/m2'
import type { OwnerOption } from '@/api/m2'

const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<InboundRow[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  warehouseId: undefined as number | undefined,
  status: undefined as number | undefined,
  dateRange: [] as string[],
  pageNo: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const result = await m5Api.inboundPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      warehouseId: query.warehouseId,
      status: query.status,
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
  query.keyword = ''
  query.warehouseId = undefined
  query.status = undefined
  query.dateRange = []
  reload(1)
}

// ---------------- 详情 ----------------

const detailVisible = ref(false)
const detail = ref<InboundRow | null>(null)

async function openDetail(row: InboundRow) {
  detail.value = await m5Api.inboundDetail(row.id)
  detailVisible.value = true
}

// ---------------- 溯源（M5-1-5） ----------------

const traceVisible = ref(false)
const traceData = ref<TraceChain | null>(null)
const traceActive = ref(0)

async function openTrace(row: InboundRow) {
  traceData.value = await m5Api.inboundTrace(row.id)
  // 链条深度：手工单只亮第 1 级，关联单按实际来源亮到对应级
  traceActive.value = traceData.value.requestNo ? 4
    : traceData.value.orderNo ? 3
    : traceData.value.inspectionNo ? 2 : 1
  traceVisible.value = true
}

// ---------------- 作废（M5-1-4） ----------------

const voidVisible = ref(false)
const current = ref<InboundRow | null>(null)
const voidForm = reactive({ reason: '' })

function openVoid(row: InboundRow) {
  current.value = row
  voidForm.reason = ''
  voidVisible.value = true
}

async function submitVoid() {
  if (!current.value) return
  if (!voidForm.reason.trim()) {
    ElMessage.warning('请填写作废原因')
    return
  }
  saving.value = true
  try {
    await m5Api.inboundVoid(current.value.id, { reason: voidForm.reason.trim() })
    ElMessage.success('已作废：库存已冲减，订单已交付数量已回退')
    voidVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 确认入库（M5-1-3，库存唯一写入点） ----------------

async function confirmBill(row: InboundRow) {
  await ElMessageBox.confirm(
    `确认后写入库存余额与流水，并回写订单已交付数量，不可撤销。确认入库 ${row.inboundNo}？`,
    '确认入库',
    { type: 'warning' }
  )
  await m5Api.inboundConfirm(row.id)
  ElMessage.success('已确认入库，库存已更新')
  load()
}

// ---------------- 新建 / 编辑（M5-1-1） ----------------

interface FormItem {
  orderItemId: number | null
  materialId: number | null
  materialNo: string
  materialName: string
  spec: string
  quantity: number
  unitId: number | null
  unitName: string
  batchNo: string
  locationId: number | null
  ownerType: number
  ownerId: number | null
  ownerNo: string | null
  ownerKey: string
  remark: string
}

const formVisible = ref(false)
const editing = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  warehouseId: undefined as number | undefined,
  inboundDate: '',
  remark: '',
  items: [] as FormItem[]
})

const warehouseOptions = ref<{ id: number; warehouseNo: string; warehouseName: string }[]>([])
const materialOptions = ref<{ id: number; materialNo: string; materialName: string; unitId: number; unitName: string }[]>([])
const locationOptions = ref<{ id: number; locationNo: string; locationName: string }[]>([])
const ownerOptions = ref<OwnerOption[]>([])

async function loadFormOptions() {
  const [warehouses, materials] = await Promise.all([
    m1Api.warehouseOptions(),
    m1Api.materialOptions()
  ])
  warehouseOptions.value = warehouses
  materialOptions.value = materials
  // 归属对象下拉来自 M2（m2:request_item:owner）：仓管员无该权限时不发请求，
  // 归属仅提供公共库存——手工入公共库存不依赖 M2 数据源
  if (store.hasPerm('m2:request_item:owner')) {
    try {
      const owners = await loadOwnerOptions()
      ownerOptions.value = owners.options
    } catch {
      ownerOptions.value = []
    }
  } else {
    ownerOptions.value = []
  }
}

function emptyItem(): FormItem {
  return {
    orderItemId: null,
    materialId: null,
    materialNo: '',
    materialName: '',
    spec: '',
    quantity: 1,
    unitId: null,
    unitName: '',
    batchNo: '',
    locationId: null,
    ownerType: 1,
    ownerId: null,
    ownerNo: null,
    ownerKey: 'public',
    remark: ''
  }
}

function addItem() {
  form.items.push(emptyItem())
}

function removeItem(index: number) {
  form.items.splice(index, 1)
}

function onMaterialChange(row: FormItem, materialId: number) {
  const material = materialOptions.value.find((m) => m.id === materialId)
  if (material) {
    row.unitId = material.unitId
    row.unitName = material.unitName
    row.materialNo = material.materialNo
    row.materialName = material.materialName
  }
}

async function onWarehouseChange() {
  form.items.forEach((item) => { item.locationId = null })
  await loadLocations()
}

async function loadLocations() {
  if (!form.warehouseId) {
    locationOptions.value = []
    return
  }
  locationOptions.value = await m1Api.warehouseLocations(form.warehouseId)
}

function onOwnerChange(row: FormItem, key: string) {
  if (key === 'public') {
    row.ownerType = 1
    row.ownerId = null
    row.ownerNo = null
  } else {
    const owner = ownerOptions.value.find((o) => String(o.id) === key)
    if (owner) {
      row.ownerType = owner.ownerType
      row.ownerId = owner.id
      row.ownerNo = owner.ownerNo
    }
  }
}

async function fillBatchNo(row: FormItem) {
  row.batchNo = await m5Api.nextBatchNo()
}

function openCreate() {
  editing.value = false
  editingId.value = null
  form.warehouseId = undefined
  form.inboundDate = ''
  form.remark = ''
  form.items = [emptyItem()]
  formVisible.value = true
  loadLocations()
}

async function openEdit(row: InboundRow) {
  editing.value = true
  editingId.value = row.id
  const detail = await m5Api.inboundDetail(row.id)
  form.warehouseId = detail.warehouseId
  form.inboundDate = detail.inboundDate
  form.remark = detail.remark || ''
  form.items = detail.items.map((item) => ({
    orderItemId: item.orderItemId,
    materialId: item.materialId,
    materialNo: item.materialNo,
    materialName: item.materialName,
    spec: item.spec || '',
    quantity: item.quantity,
    unitId: item.unitId,
    unitName: item.unitName || '',
    batchNo: item.batchNo || '',
    locationId: item.locationId,
    ownerType: item.ownerType ?? 1,
    ownerId: item.ownerId,
    ownerNo: item.ownerNo,
    ownerKey: item.ownerType && item.ownerType !== 1 && item.ownerId ? String(item.ownerId) : 'public',
    remark: item.remark || ''
  }))
  formVisible.value = true
  await loadLocations()
}

async function submitForm() {
  if (!form.warehouseId) {
    ElMessage.warning('请选择收货仓库')
    return
  }
  if (!form.items.length) {
    ElMessage.warning('至少需要一行入库明细')
    return
  }
  for (const item of form.items) {
    if (!item.materialId) {
      ElMessage.warning('存在未选择物料的行')
      return
    }
    if (!(item.quantity > 0)) {
      ElMessage.warning('入库数量必须大于 0')
      return
    }
  }
  saving.value = true
  try {
    const body = {
      warehouseId: form.warehouseId as number,
      inboundDate: form.inboundDate || undefined,
      remark: form.remark || undefined,
      items: form.items.map((item) => ({
        orderItemId: item.orderItemId,
        materialId: item.materialId as number,
        quantity: item.quantity,
        unitId: item.unitId,
        batchNo: item.batchNo || undefined,
        locationId: item.locationId,
        ownerType: item.ownerType,
        ownerId: item.ownerId,
        ownerNo: item.ownerNo,
        remark: item.remark || undefined
      }))
    }
    if (editing.value && editingId.value) {
      await m5Api.inboundUpdate(editingId.value, body)
      ElMessage.success('已保存，批次与库位登记生效')
    } else {
      const id = await m5Api.inboundCreate(body)
      ElMessage.success(`已创建入库单草稿（id=${id}）`)
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadFormOptions()
  load()
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
.dialog-tip {
  margin-top: 12px;
}
.item-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.batch-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>

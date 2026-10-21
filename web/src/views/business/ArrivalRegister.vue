<template>
  <div class="page-card register-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">{{ editing ? '编辑到货登记' : '新建到货登记' }}</h2>
        <p class="text-sub">P11｜M4-1 到货登记、M4-2 验收判定：守恒律 合格 + 不合格 = 到货</p>
      </div>
      <div class="actions">
        <el-button :icon="Back" @click="router.back()">返回</el-button>
      </div>
    </header>

    <el-skeleton v-if="pageLoading" :rows="8" animated />

    <template v-else>
      <el-alert
        class="scope-alert"
        type="info"
        :closable="false"
        show-icon
        title="到货量不得超过订单行剩余到货量；草稿也占配额（防止同时登记超发）。合格/不合格可先只记到货量（缺省全合格），提交时按守恒律校验。"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="bill-form">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="来源订单" required>
              <el-select
                v-model="form.orderId"
                placeholder="选择已审核订单"
                :disabled="editing"
                filterable
                style="width: 100%"
                @change="onOrderChange"
              >
                <el-option
                  v-for="order in orders"
                  :key="order.id"
                  :label="`${order.orderNo}（${order.supplierName}）`"
                  :value="order.id"
                >
                  <span>{{ order.orderNo }}</span>
                  <span class="option-sub">{{ order.supplierName }}｜剩余到货 {{ order.remainingArrivalQty }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="到货日期" required>
              <el-date-picker v-model="form.arrivalDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货仓库" required>
              <el-select v-model="form.warehouseId" placeholder="选择仓库" filterable style="width: 100%">
                <el-option
                  v-for="wh in warehouses"
                  :key="wh.id"
                  :label="`${wh.warehouseNo} ${wh.warehouseName}`"
                  :value="wh.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="验收方式">
              <el-radio-group v-model="form.inspectionMode">
                <el-radio :value="1">免检</el-radio>
                <el-radio :value="2">抽样</el-radio>
                <el-radio :value="3">全检</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="500" show-word-limit placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <h4 class="block-title">
        到货明细
        <span class="text-sub">（本次到货量填 0 表示该行不登记）</span>
      </h4>
      <el-table :data="draftItems" border>
        <el-table-column prop="orderItemNo" label="订单行" width="70" />
        <el-table-column prop="materialNo" label="物料编码" width="110" />
        <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
        <el-table-column prop="unitName" label="单位" width="60" />
        <el-table-column prop="orderQty" label="订单量" width="90" />
        <el-table-column prop="arrivedQty" label="累计到货" width="90" />
        <el-table-column prop="remainingArrival" label="剩余到货" width="90" />
        <el-table-column label="本次到货" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.arrivalQty" :min="0" :max="row.remainingArrival"
              :precision="4" :step="1" size="small" controls-position="right" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="合格" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.qualifiedQty" :min="0" :precision="4" :step="1"
              size="small" controls-position="right" style="width: 100px"
              placeholder="缺省全合格" />
          </template>
        </el-table-column>
        <el-table-column label="不合格" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.unqualifiedQty" :min="0" :precision="4" :step="1"
              size="small" controls-position="right" style="width: 100px"
              placeholder="缺省 0" />
          </template>
        </el-table-column>
        <el-table-column label="批次" width="130">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" maxlength="50" size="small" placeholder="预登记批次" />
          </template>
        </el-table-column>
        <el-table-column label="守恒" width="80">
          <template #default="{ row }">
            <el-tag v-if="conserved(row)" type="success" size="small">OK</el-tag>
            <el-tag v-else type="danger" size="small">不等</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!draftItems.length" description="该订单暂无未到满的明细行" :image-size="50" />

      <div class="form-footer">
        <span class="text-sub">
          合计到货 {{ totalArrival }}，合格 {{ totalQualified }}，不合格 {{ totalUnqualified }}
        </span>
        <div>
          <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
          <el-button v-if="!editing" type="primary" :loading="saving" @click="save(true)">
            保存并提交
          </el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Back } from '@element-plus/icons-vue'
import * as m4Api from '@/api/m4'
import type { ArrivalItemRow, ArrivalOrderRow } from '@/api/m4'
import { warehouseOptions } from '@/api/m1'
import type { WarehouseRow } from '@/api/m1'

const route = useRoute()
const router = useRouter()

interface DraftItem {
  id?: number
  orderItemId: number
  orderItemNo: number
  materialId: number
  materialNo: string
  materialName: string
  spec: string | null
  unitId: number
  unitName: string
  arrivalQty: number
  qualifiedQty: number | null
  unqualifiedQty: number | null
  batchNo: string
  remark: string
  orderQty: number
  arrivedQty: number
  remainingArrival: number
  inboundableQty: number
}

const pageLoading = ref(true)
const saving = ref(false)
const editing = ref(false)
const orders = ref<ArrivalOrderRow[]>([])
const warehouses = ref<WarehouseRow[]>([])
const draftItems = ref<DraftItem[]>([])
const formRef = ref<FormInstance>()

const form = reactive({
  orderId: undefined as number | undefined,
  arrivalDate: new Date().toISOString().slice(0, 10),
  warehouseId: undefined as number | undefined,
  inspectionMode: 2,
  remark: ''
})

const rules: FormRules = {
  orderId: [{ required: true, message: '请选择来源订单', trigger: 'change' }],
  arrivalDate: [{ required: true, message: '请选择到货日期', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择收货仓库', trigger: 'change' }]
}

/** 守恒判定：未填合格/不合格时缺省全合格视为守恒 */
function conserved(row: DraftItem): boolean {
  if (!row.arrivalQty || row.arrivalQty <= 0) return true
  const qualified = row.qualifiedQty == null ? row.arrivalQty : row.qualifiedQty
  const unqualified = row.unqualifiedQty == null ? 0 : row.unqualifiedQty
  return Math.abs(qualified + unqualified - row.arrivalQty) < 1e-6
}

const totalArrival = computed(() =>
  draftItems.value.reduce((sum, row) => sum + (row.arrivalQty || 0), 0).toFixed(4)
)
const totalQualified = computed(() =>
  draftItems.value
    .reduce((sum, row) => {
      if (!row.arrivalQty || row.arrivalQty <= 0) return sum
      return sum + (row.qualifiedQty == null ? row.arrivalQty : row.qualifiedQty)
    }, 0)
    .toFixed(4)
)
const totalUnqualified = computed(() =>
  draftItems.value
    .reduce((sum, row) => {
      if (!row.arrivalQty || row.arrivalQty <= 0) return sum
      return sum + (row.unqualifiedQty == null ? 0 : row.unqualifiedQty)
    }, 0)
    .toFixed(4)
)

async function onOrderChange(orderId: number | undefined) {
  if (!orderId) {
    draftItems.value = []
    return
  }
  const order = await m4Api.arrivableOrderItems(orderId)
  draftItems.value = order.items.map((item) => toDraft(item))
}

function toDraft(item: ArrivalItemRow): DraftItem {
  return {
    id: item.id,
    orderItemId: item.orderItemId,
    orderItemNo: item.orderItemNo,
    materialId: item.materialId,
    materialNo: item.materialNo,
    materialName: item.materialName,
    spec: item.spec,
    unitId: item.unitId,
    unitName: item.unitName,
    arrivalQty: item.arrivalQty ?? 0,
    qualifiedQty: item.qualifiedQty,
    unqualifiedQty: item.unqualifiedQty,
    batchNo: item.batchNo ?? '',
    remark: item.remark ?? '',
    orderQty: item.orderQty,
    arrivedQty: item.arrivedQty,
    remainingArrival: item.remainingArrival,
    inboundableQty: item.inboundableQty
  }
}

async function save(submit: boolean) {
  await formRef.value?.validate(async (valid) => {
    if (!valid) return
    const active = draftItems.value.filter((row) => row.arrivalQty > 0)
    if (!active.length) {
      ElMessage.warning('请至少登记一行到货明细')
      return
    }
    const bad = active.find((row) => !conserved(row))
    if (bad) {
      ElMessage.error(`订单行 ${bad.orderItemNo} 合格加不合格必须等于到货数量`)
      return
    }
    saving.value = true
    try {
      const body = {
        orderId: form.orderId as number,
        arrivalDate: form.arrivalDate,
        warehouseId: form.warehouseId as number,
        inspectionMode: form.inspectionMode,
        remark: form.remark || undefined,
        items: active.map((row) => ({
          orderItemId: row.orderItemId,
          arrivalQty: row.arrivalQty,
          qualifiedQty: row.qualifiedQty ?? undefined,
          unqualifiedQty: row.unqualifiedQty ?? undefined,
          batchNo: row.batchNo || undefined,
          remark: row.remark || undefined
        }))
      }
      let id: number
      if (editing.value && route.query.id) {
        await m4Api.arrivalUpdate(Number(route.query.id), body)
        id = Number(route.query.id)
      } else {
        id = await m4Api.arrivalCreate(body)
      }
      if (submit) {
        await m4Api.arrivalSubmit(id)
        ElMessage.success('已保存并提交，进入可入库状态')
      } else {
        ElMessage.success('草稿已保存')
      }
      router.push('/arrival')
    } finally {
      saving.value = false
    }
  })
}

onMounted(async () => {
  try {
    const [orderList, warehouseList] = await Promise.all([
      m4Api.arrivableOrders(),
      warehouseOptions() as Promise<WarehouseRow[]>
    ])
    orders.value = orderList
    warehouses.value = warehouseList
    const editId = route.query.id
    if (editId) {
      editing.value = true
      const bill = await m4Api.arrivalDetail(Number(editId))
      form.orderId = bill.orderId
      form.arrivalDate = bill.arrivalDate
      form.warehouseId = bill.warehouseId
      form.inspectionMode = bill.inspectionMode
      form.remark = bill.remark ?? ''
      draftItems.value = bill.items.map((item) => toDraft(item))
    } else if (route.query.orderId) {
      form.orderId = Number(route.query.orderId)
      await onOrderChange(form.orderId)
    }
  } finally {
    pageLoading.value = false
  }
})
</script>

<style scoped>
.bill-form {
  margin-top: 8px;
}
.option-sub {
  float: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.block-title {
  margin: 18px 0 10px;
  font-size: 14px;
}
.form-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
</style>

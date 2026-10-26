<template>
  <div class="page-card request-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">采购申请</h2>
        <p class="text-sub">P5｜M2 申请单维护：草稿、明细行、审批流转与转订单</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m2:request:create')" type="primary" :icon="Plus" @click="openCreate">
          新建申请
        </el-button>
        <el-button
          v-if="store.hasPerm('m2:request:transfer')"
          :icon="Switch"
          :disabled="!selectedApproved.length"
          @click="openTransfer"
        >
          转订单<span v-if="selectedApproved.length">（{{ selectedApproved.length }}）</span>
        </el-button>
      </div>
    </header>

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="单号 / 申请人 / 备注"
          clearable
          style="width: 220px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="生命周期">
        <el-select v-model="query.lifecycleStatus" placeholder="全部" clearable style="width: 120px">
          <el-option label="草稿" :value="1" />
          <el-option label="已提交" :value="2" />
          <el-option label="已审核" :value="3" />
          <el-option label="已关闭" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="审核状态">
        <el-select v-model="query.auditStatus" placeholder="全部" clearable style="width: 110px">
          <el-option label="未审核" :value="1" />
          <el-option label="已通过" :value="2" />
          <el-option label="已驳回" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请日期">
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
      title="列表按数据范围过滤：采购员只见本部门单据，主管可见被授权部门；勾选已审核单据可批量转订单（M2-4）。"
    />

    <el-table
      v-loading="loading"
      :data="rows"
      border
      stripe
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="store.hasPerm('m2:request:transfer')" type="selection" width="42" />
      <el-table-column prop="requestNo" label="申请单号" width="180" fixed="left" />
      <el-table-column prop="requestDate" label="申请日期" width="120" />
      <el-table-column prop="applicantName" label="申请人" width="100" />
      <el-table-column prop="deptName" label="部门" width="100" />
      <el-table-column prop="requireDate" label="需求日期" width="120" />
      <el-table-column label="明细" width="80" align="center">
        <template #default="{ row }">
          <span>{{ row.activeItemCount }} 行</span>
        </template>
      </el-table-column>
      <el-table-column label="合计金额" width="120" align="right">
        <template #default="{ row }">
          <span>¥{{ formatAmount(row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="生命周期" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="lifecycleTagType(row.lifecycleStatus)" size="small">
            {{ row.lifecycleStatusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="auditTagType(row.auditStatus)" size="small">
            {{ row.auditStatusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m2:request:view')" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button
            v-if="store.hasPerm('m2:request:update') && row.lifecycleStatus !== 2 && row.lifecycleStatus !== 3"
            link
            type="primary"
            @click="openEdit(row)"
          >
            维护明细
          </el-button>
          <el-button
            v-if="store.hasPerm('m2:request:submit') && (row.lifecycleStatus === 1 || row.auditStatus === 3)"
            link
            type="success"
            @click="submitBill(row)"
          >
            提交
          </el-button>
          <el-button
            v-if="store.hasPerm('m2:request:withdraw') && row.lifecycleStatus === 2"
            link
            type="warning"
            @click="withdrawBill(row)"
          >
            撤回
          </el-button>
          <el-button
            v-if="store.hasPerm('m2:request:copy')"
            link
            type="primary"
            @click="copyBill(row)"
          >
            复制
          </el-button>
          <el-button
            v-if="store.hasPerm('m2:request:export')"
            link
            type="primary"
            @click="downloadRequestCsv(row.id)"
          >
            导出
          </el-button>
          <el-button
            v-if="store.hasPerm('m2:request:delete') && row.lifecycleStatus === 1"
            link
            type="danger"
            @click="deleteBill(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="query.pageNo"
      :page-size="query.pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="reload"
      @size-change="onSizeChange"
    />

    <!-- 详情抽屉：主表 + 明细 + 审批时间线（M2-3-6） -->
    <el-drawer v-model="detailVisible" title="申请单详情" size="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="申请日期">{{ detail.requestDate }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detail.deptName }}</el-descriptions-item>
          <el-descriptions-item label="需求日期">{{ detail.requireDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="合计金额">¥{{ formatAmount(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="生命周期">
            <el-tag :type="lifecycleTagType(detail.lifecycleStatus)" size="small">
              {{ detail.lifecycleStatusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="auditTagType(detail.auditStatus)" size="small">
              {{ detail.auditStatusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.closeReason" label="关闭原因" :span="2">
            {{ detail.closeReason }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="block-title">申请明细</h4>
        <el-table :data="detail.items" border size="small">
          <el-table-column prop="itemNo" label="#" width="44" />
          <el-table-column prop="materialNo" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">
              {{ row.quantity }} {{ row.unitName }}
            </template>
          </el-table-column>
          <el-table-column label="预估单价" width="90" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.estPrice) }}</template>
          </el-table-column>
          <el-table-column label="预估金额" width="100" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.estAmount) }}</template>
          </el-table-column>
          <el-table-column label="建议供应商" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.suggestSupplierName || '—' }}</template>
          </el-table-column>
          <el-table-column label="归属对象" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ownerName || '—' }}</template>
          </el-table-column>
          <el-table-column label="已转/剩余" width="110" align="right">
            <template #default="{ row }">
              {{ row.transferredQty }} / {{ row.remainingQty }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="itemTagType(row.itemStatus)" size="small">{{ row.itemStatusName }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="block-title">审批记录</h4>
        <el-timeline v-if="detail.approvalHistory.length">
          <el-timeline-item
            v-for="h in detail.approvalHistory"
            :key="h.taskId"
            :type="historyNodeType(h.taskStatus)"
            :timestamp="formatTime(h.handleTime)"
            placement="top"
          >
            <div class="history-item">
              <strong>节点 {{ h.nodeSeq }}｜{{ h.nodeName }}</strong>
              <span class="history-meta">{{ h.signTypeName }}｜{{ h.approverName }}</span>
              <el-tag :type="historyNodeType(h.taskStatus)" size="small">{{ h.taskStatusName }}</el-tag>
              <p v-if="h.opinion" class="history-opinion">意见：{{ h.opinion }}</p>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审批记录" :image-size="60" />
      </template>
    </el-drawer>

    <!-- 新建申请：主表 + 明细行编辑器（M2-1-2 / M2-2-1） -->
    <el-dialog v-model="createVisible" title="新建采购申请" width="980px" @close="resetCreateForm">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="先建草稿单，再逐行保存明细；明细行的物料、建议供应商必须在其供货范围内（M2-2-3）。"
        class="dialog-alert"
      />
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>

      <h4 class="block-title">
        申请明细
        <el-button class="add-row-btn" :icon="Plus" size="small" @click="addDraftRow">添加一行</el-button>
      </h4>
      <el-table :data="createForm.items" border size="small" empty-text="尚未添加明细行">
        <el-table-column label="物料" min-width="200">
          <template #default="{ row }">
            <el-select
              v-model="row.materialId"
              placeholder="选择物料"
              filterable
              size="small"
              style="width: 100%"
              @change="onDraftMaterialChange(row)"
            >
              <el-option
                v-for="m in materialSelectOptions"
                :key="m.id"
                :label="`${m.materialNo} ${m.materialName}`"
                :value="m.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0.01" :precision="2" size="small" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="预估单价" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.estPrice" :min="0" :precision="2" size="small" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="需求日期" width="150">
          <template #default="{ row }">
            <el-date-picker v-model="row.requireDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="归属对象" min-width="170">
          <template #default="{ row }">
            <el-select v-model="row.ownerId" placeholder="部门领用（可不选）" clearable filterable size="small" style="width: 100%">
              <el-option
                v-for="o in ownerSelectOptions"
                :key="o.id"
                :label="`${o.ownerTypeName}｜${o.ownerName}`"
                :value="o.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="建议供应商" min-width="190">
          <template #default="{ row }">
            <el-select
              v-model="row.suggestSupplierId"
              placeholder="不指定则不可转订单"
              clearable
              filterable
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="s in supplierSelectOptions"
                :key="s.id"
                :label="`${s.supplierNo} ${s.supplierName}`"
                :value="s.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            ¥{{ formatAmount((row.quantity || 0) * (row.estPrice || 0)) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" :icon="Delete" @click="createForm.items.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>
      <div class="dialog-total">合计：¥{{ formatAmount(createTotal) }}</div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 维护明细：草稿 / 已驳回单的明细行增删改（M2-1-3 / M2-2-2） -->
    <el-dialog v-model="editVisible" title="维护申请明细" width="980px">
      <template v-if="editBill">
        <el-descriptions :column="3" border size="small" class="edit-head">
          <el-descriptions-item label="申请单号">{{ editBill.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ editBill.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ editBill.deptName }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px" class="edit-remark">
          <el-form-item label="备注">
            <el-input v-model="editRemark" type="textarea" :rows="2" maxlength="200" />
          </el-form-item>
        </el-form>
        <div class="edit-actions">
          <el-button size="small" :icon="Plus" @click="addEditRow">添加一行</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="saveEditRemark">保存备注</el-button>
        </div>
        <el-table :data="editBill.items" border size="small">
          <el-table-column prop="itemNo" label="#" width="44" />
          <el-table-column prop="materialNo" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="数量" width="120">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0.01"
                :precision="2"
                size="small"
                controls-position="right"
                style="width: 100%"
                @change="saveEditRow(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="预估单价" width="110">
            <template #default="{ row }">
              <el-input-number
                v-model="row.estPrice"
                :min="0"
                :precision="2"
                size="small"
                controls-position="right"
                style="width: 100%"
                @change="saveEditRow(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="需求日期" width="140">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.requireDate"
                type="date"
                value-format="YYYY-MM-DD"
                size="small"
                style="width: 100%"
                @change="saveEditRow(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="建议供应商" min-width="180">
            <template #default="{ row }">
              <el-select
                v-model="row.suggestSupplierId"
                placeholder="不指定则不可转订单"
                clearable
                filterable
                size="small"
                style="width: 100%"
                @change="saveEditRow(row)"
              >
                <el-option
                  v-for="s in supplierSelectOptions"
                  :key="s.id"
                  :label="`${s.supplierNo} ${s.supplierName}`"
                  :value="s.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="归属对象" min-width="160">
            <template #default="{ row }">
              <el-select
                v-model="row.ownerId"
                placeholder="部门领用"
                clearable
                filterable
                size="small"
                style="width: 100%"
                @change="saveEditRow(row)"
              >
                <el-option
                  v-for="o in ownerSelectOptions"
                  :key="o.id"
                  :label="`${o.ownerTypeName}｜${o.ownerName}`"
                  :value="o.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.estAmount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="itemTagType(row.itemStatus)" size="small">{{ row.itemStatusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.transferredQty === 0 || row.transferredQty == null"
                link
                type="danger"
                :icon="Delete"
                @click="deleteEditRow(row)"
              />
            </template>
          </el-table-column>
        </el-table>
        <div class="dialog-total">合计：¥{{ formatAmount(editBill.totalAmount) }}</div>
      </template>
    </el-dialog>

    <!-- 转订单：勾选已审核单与明细行（M2-4-1 / M2-4-2） -->
    <el-dialog v-model="transferVisible" title="申请转订单" width="900px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="仅已审核且指定了建议供应商的明细行可转订单；同一供应商的多行会合并为一张订单（M2-4-2）。"
        class="dialog-alert"
      />
      <div v-for="src in transferSources" :key="src.requestId" class="transfer-source">
        <div class="transfer-source-head">
          <el-checkbox v-model="src.selected" @change="onTransferSourceToggle(src)">
            {{ src.requestNo }}（{{ src.applicantName }}｜合计 ¥{{ formatAmount(src.totalAmount) }}）
          </el-checkbox>
        </div>
        <el-table :data="src.items" border size="small" @selection-change="(rows: RequestItemRow[]) => onTransferItemsChange(src, rows)">
          <el-table-column type="selection" width="42" :selectable="transferRowSelectable" />
          <el-table-column prop="materialNo" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">{{ row.remainingQty }} {{ row.unitName }}</template>
          </el-table-column>
          <el-table-column label="建议供应商" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.suggestSupplierName">{{ row.suggestSupplierName }}</span>
              <el-tag v-else type="danger" size="small">未指定，不可转</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="行状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="itemTagType(row.itemStatus)" size="small">{{ row.itemStatusName }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmTransfer">确认转订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Search, Switch } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as m2Api from '@/api/m2'
import type { RequestItemRow, RequestRow, TodoRow } from '@/api/m2'
import { materialOptions, supplierOptions } from '@/api/m1'
import type { MaterialRow, SupplierRow } from '@/api/m1'

interface MaterialOption {
  id: number
  materialNo: string
  materialName: string
  unitName: string
}

interface SupplierOption {
  id: number
  supplierNo: string
  supplierName: string
}

interface OwnerOption {
  id: number
  ownerType: number
  ownerTypeName: string
  ownerName: string
}

interface DraftItem {
  materialId?: number
  quantity: number
  estPrice: number
  requireDate: string
  ownerType: number
  ownerId?: number
  suggestSupplierId?: number
}

interface TransferSource {
  requestId: number
  requestNo: string
  applicantName: string
  totalAmount: number
  items: RequestItemRow[]
  selected: boolean
  selectedItemIds: number[]
}

const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<RequestRow[]>([])
const total = ref(0)
const selectedApproved = ref<RequestRow[]>([])

const query = reactive({
  keyword: '',
  lifecycleStatus: undefined as number | undefined,
  auditStatus: undefined as number | undefined,
  dateRange: [] as string[],
  pageNo: 1,
  pageSize: 10
})

// ---------------- 选项数据 ----------------

const materialSelectOptions = ref<MaterialOption[]>([])
const supplierSelectOptions = ref<SupplierOption[]>([])
const ownerSelectOptions = ref<OwnerOption[]>([])

async function loadOptions() {
  // 归属对象下拉挂 m2:request_item:owner（采购员口径）：主管等无该权限的角色
  // 打开申请页时不发请求，避免 403 冒泡成 pageerror 与错误 toast（同 M3 订单页修法）
  const [materials, suppliers, owners] = await Promise.all([
    materialOptions() as Promise<MaterialRow[]>,
    supplierOptions() as Promise<SupplierRow[]>,
    store.hasPerm('m2:request_item:owner')
      ? m2Api.ownerOptions()
      : Promise.resolve({ options: [] as { id: number; ownerType: number; ownerTypeName: string; ownerNo: string; ownerName: string }[] })
  ])
  materialSelectOptions.value = materials.map((m) => ({
    id: m.id,
    materialNo: m.materialNo,
    materialName: m.materialName,
    unitName: m.unitName
  }))
  supplierSelectOptions.value = suppliers.map((s) => ({
    id: s.id,
    supplierNo: s.supplierNo,
    supplierName: s.supplierName
  }))
  ownerSelectOptions.value = owners.options.map((o) => ({
    id: o.id,
    ownerType: o.ownerType,
    ownerTypeName: o.ownerTypeName,
    ownerName: o.ownerName
  }))
}

// ---------------- 列表 ----------------

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      lifecycleStatus: query.lifecycleStatus,
      auditStatus: query.auditStatus,
      dateFrom: query.dateRange?.[0],
      dateTo: query.dateRange?.[1]
    }
    const result = await m2Api.pageRequests(params)
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function reload(pageNo = query.pageNo) {
  query.pageNo = pageNo
  load()
}

function onSizeChange(size: number) {
  query.pageSize = size
  reload(1)
}

function resetQuery() {
  query.keyword = ''
  query.lifecycleStatus = undefined
  query.auditStatus = undefined
  query.dateRange = []
  reload(1)
}

function onSelectionChange(selected: RequestRow[]) {
  selectedApproved.value = selected
}

// ---------------- 展示辅助 ----------------

function formatAmount(value: number | undefined | null) {
  if (value == null) return '0.00'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(value: string | null | undefined) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function lifecycleTagType(status: number) {
  if (status === 1) return 'info'
  if (status === 2) return 'warning'
  if (status === 3) return 'success'
  return 'danger'
}

function auditTagType(status: number) {
  if (status === 1) return 'info'
  if (status === 2) return 'success'
  return 'danger'
}

function itemTagType(status: number) {
  if (status === 1) return 'info'
  if (status === 2) return 'warning'
  if (status === 3) return 'success'
  if (status === 4) return 'danger'
  return 'info'
}

function historyNodeType(taskStatus: number) {
  if (taskStatus === 2) return 'success'
  if (taskStatus === 3) return 'danger'
  if (taskStatus === 4) return 'info'
  return 'warning'
}

// ---------------- 详情 ----------------

const detailVisible = ref(false)
const detail = ref<RequestRow | null>(null)

async function openDetail(row: RequestRow) {
  detail.value = await m2Api.getRequest(row.id)
  detailVisible.value = true
}

// ---------------- 新建 ----------------

const createVisible = ref(false)
const createForm = reactive({
  remark: '',
  items: [] as DraftItem[]
})
const createTotal = computed(() =>
  createForm.items.reduce((sum, item) => sum + (item.quantity || 0) * (item.estPrice || 0), 0)
)

function openCreate() {
  createForm.remark = ''
  createForm.items = []
  addDraftRow()
  createVisible.value = true
}

function resetCreateForm() {
  createForm.remark = ''
  createForm.items = []
}

function addDraftRow() {
  createForm.items.push({
    quantity: 1,
    estPrice: 0,
    requireDate: '',
    ownerType: 1
  })
}

function onDraftMaterialChange(row: DraftItem) {
  row.suggestSupplierId = undefined
  row.ownerId = undefined
}

async function saveCreate() {
  const valid = createForm.items.filter((item) => item.materialId && item.quantity > 0)
  if (!valid.length) {
    ElMessage.warning('请至少填写一行完整明细（物料与数量必填）')
    return
  }
  saving.value = true
  try {
    const requestId = await m2Api.createRequest({ remark: createForm.remark })
    for (const item of valid) {
      const owner = ownerSelectOptions.value.find((o) => o.id === item.ownerId)
      await m2Api.addRequestItem(requestId, {
        materialId: item.materialId,
        quantity: item.quantity,
        estPrice: item.estPrice,
        requireDate: item.requireDate || undefined,
        ownerType: owner ? owner.ownerType : 1,
        ownerId: owner ? owner.id : undefined,
        suggestSupplierId: item.suggestSupplierId || undefined,
        remark: ''
      })
    }
    ElMessage.success(`申请单已创建，共 ${valid.length} 行明细`)
    createVisible.value = false
    reload(1)
  } finally {
    saving.value = false
  }
}

// ---------------- 维护明细 ----------------

const editVisible = ref(false)
const editBill = ref<RequestRow | null>(null)
const editRemark = ref('')

async function openEdit(row: RequestRow) {
  editBill.value = await m2Api.getRequest(row.id)
  editRemark.value = editBill.value.remark || ''
  editVisible.value = true
}

function addEditRow() {
  const bill = editBill.value
  if (!bill) return
  const usedMaterials = new Set(bill.items.map((i) => i.materialId))
  const first = materialSelectOptions.value.find((m) => !usedMaterials.has(m.id))
  if (!first) {
    ElMessage.warning('没有可添加的物料了')
    return
  }
  saving.value = true
  m2Api
    .addRequestItem(bill.id, {
      materialId: first.id,
      quantity: 1,
      estPrice: 0,
      requireDate: '',
      ownerType: 1,
      remark: ''
    })
    .then(async () => {
      editBill.value = await m2Api.getRequest(bill.id)
      ElMessage.success('已添加一行，请补充数量与单价')
    })
    .finally(() => {
      saving.value = false
    })
}

async function saveEditRow(row: RequestItemRow) {
  if (!editBill.value) return
  const owner = ownerSelectOptions.value.find((o) => o.id === row.ownerId)
  saving.value = true
  try {
    await m2Api.updateRequestItem(editBill.value.id, row.id, {
      materialId: row.materialId,
      quantity: row.quantity,
      estPrice: row.estPrice,
      requireDate: row.requireDate || undefined,
      ownerType: owner ? owner.ownerType : row.ownerType,
      ownerId: owner ? owner.id : undefined,
      suggestSupplierId: row.suggestSupplierId || undefined,
      remark: row.remark || ''
    })
    editBill.value = await m2Api.getRequest(editBill.value.id)
  } finally {
    saving.value = false
  }
}

async function deleteEditRow(row: RequestItemRow) {
  if (!editBill.value) return
  await ElMessageBox.confirm(`确认删除明细行「${row.materialName}」？`, '删除确认', { type: 'warning' })
  await m2Api.deleteRequestItem(editBill.value.id, row.id)
  editBill.value = await m2Api.getRequest(editBill.value.id)
  ElMessage.success('明细行已删除')
}

async function saveEditRemark() {
  if (!editBill.value) return
  await m2Api.updateRequest(editBill.value.id, {
    deptId: editBill.value.deptId,
    remark: editRemark.value
  })
  editBill.value = await m2Api.getRequest(editBill.value.id)
  ElMessage.success('备注已保存')
  load()
}

// ---------------- 行内动作 ----------------

async function submitBill(row: RequestRow) {
  await ElMessageBox.confirm(`确认提交申请单 ${row.requestNo} 进入审批？`, '提交确认', { type: 'info' })
  await m2Api.submitRequest(row.id)
  ElMessage.success('已提交审批')
  load()
}

async function withdrawBill(row: RequestRow) {
  await ElMessageBox.confirm('撤回后单据回到草稿，可修改后重新提交。确认撤回？', '撤回确认', { type: 'warning' })
  await m2Api.withdrawRequest(row.id)
  ElMessage.success('已撤回')
  load()
}

async function copyBill(row: RequestRow) {
  await m2Api.copyRequest(row.id)
  ElMessage.success('已复制为新草稿')
  load()
}

async function deleteBill(row: RequestRow) {
  await ElMessageBox.confirm(`确认删除草稿 ${row.requestNo}？删除后不可恢复。`, '删除确认', { type: 'warning' })
  await m2Api.deleteRequest(row.id)
  ElMessage.success('已删除')
  load()
}

// ---------------- 转订单 ----------------

const transferVisible = ref(false)
const transferSources = ref<TransferSource[]>([])

async function openTransfer() {
  if (!selectedApproved.value.length) return
  transferSources.value = await Promise.all(
    selectedApproved.value.map(async (bill) => {
      const detail = await m2Api.getRequest(bill.id)
      return {
        requestId: detail.id,
        requestNo: detail.requestNo,
        applicantName: detail.applicantName,
        totalAmount: detail.totalAmount,
        items: detail.items,
        selected: true,
        selectedItemIds: detail.items
          .filter((i) => i.suggestSupplierId && i.remainingQty > 0)
          .map((i) => i.id)
      } as TransferSource
    })
  )
  transferVisible.value = true
}

function transferRowSelectable(row: RequestItemRow) {
  return !!row.suggestSupplierId && row.remainingQty > 0
}

function onTransferSourceToggle(src: TransferSource) {
  src.selectedItemIds = src.selected
    ? src.items.filter((i) => transferRowSelectable(i)).map((i) => i.id)
    : []
}

function onTransferItemsChange(src: TransferSource, selected: RequestItemRow[]) {
  src.selectedItemIds = selected.map((i) => i.id)
}

async function confirmTransfer() {
  const sources = transferSources.value
    .filter((s) => s.selected && s.selectedItemIds.length)
    .map((s) => ({ requestId: s.requestId, itemIds: s.selectedItemIds }))
  if (!sources.length) {
    ElMessage.warning('请先勾选要转订单的明细行')
    return
  }
  saving.value = true
  try {
    const result = await m2Api.transferRequests(sources)
    transferVisible.value = false
    const lines = result.orders.map(
      (o) => `${o.orderNo}｜${o.supplierName}｜${o.itemCount} 行｜¥${formatAmount(o.totalAmount)}`
    )
    ElMessageBox.alert(lines.join('\n'), `已生成 ${result.orders.length} 张采购订单`, {
      type: 'success',
      confirmButtonText: '知道了'
    })
    load()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadOptions()
  load()
})
</script>

<style scoped>
.scope-alert {
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.block-title {
  margin: 18px 0 10px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.add-row-btn {
  margin-left: 10px;
}

.dialog-alert {
  margin-bottom: 12px;
}

.dialog-total {
  margin-top: 10px;
  text-align: right;
  font-weight: 600;
}

.edit-head {
  margin-bottom: 12px;
}

.edit-remark {
  margin-top: 4px;
}

.edit-actions {
  margin-bottom: 10px;
  display: flex;
  gap: 8px;
}

.transfer-source {
  margin-bottom: 16px;
}

.transfer-source-head {
  margin-bottom: 6px;
  font-weight: 600;
}

.history-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.history-meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.history-opinion {
  flex-basis: 100%;
  margin: 4px 0 0;
  color: var(--el-text-color-regular);
}
</style>

<template>
  <div class="page-card todo-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">我的待办</h2>
        <p class="text-sub">P6｜M2-3 审批待办：或签/会签任务处理、通过、驳回与按行退回</p>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="reload(1)">刷新</el-button>
      </div>
    </header>

    <el-alert
      class="scope-alert"
      type="info"
      :closable="false"
      show-icon
      title="待办按审批人生成：或签节点任一人处理即推进，其余任务自动失效；会签节点需全部处理（M2-3-7）。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="billNo" label="申请单号" width="180" fixed="left" />
      <el-table-column prop="applicantName" label="申请人" width="100" />
      <el-table-column prop="requestDate" label="申请日期" width="120" />
      <el-table-column label="合计金额" width="120" align="right">
        <template #default="{ row }">
          <span>¥{{ formatAmount(row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="明细" width="70" align="center">
        <template #default="{ row }">
          <span>{{ row.itemCount }} 行</span>
        </template>
      </el-table-column>
      <el-table-column label="审批节点" min-width="140">
        <template #default="{ row }">
          <span>节点 {{ row.nodeSeq }}｜{{ row.nodeName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="签批方式" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.signType === 1 ? 'warning' : 'danger'" size="small">
            {{ row.signTypeName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" label="任务时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.createdTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看单据</el-button>
          <el-button link type="success" @click="openApprove(row)">通过</el-button>
          <el-button link type="danger" @click="openReject(row)">驳回</el-button>
          <el-button link type="warning" @click="openReturn(row)">按行退回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.pageNo"
      :page-size="query.pageSize"
      @current-change="reload"
    />

    <!-- 单据详情：主表 + 明细 + 审批时间线（M2-3-6） -->
    <el-drawer v-model="detailVisible" title="申请单详情" size="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="申请日期">{{ detail.requestDate }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detail.deptName }}</el-descriptions-item>
          <el-descriptions-item label="需求日期">{{ detail.requireDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="合计金额">¥{{ formatAmount(detail.totalAmount) }}</el-descriptions-item>
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
          <el-table-column label="预估金额" width="100" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.estAmount) }}</template>
          </el-table-column>
          <el-table-column label="建议供应商" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.suggestSupplierName || '—' }}</template>
          </el-table-column>
          <el-table-column label="归属对象" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ownerName || '—' }}</template>
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

    <!-- 审批通过（M2-3-2） -->
    <el-dialog v-model="approveVisible" title="审批通过" width="520px">
      <p>确认通过申请单 <strong>{{ activeTodo?.billNo }}</strong> ？</p>
      <p class="dialog-hint">
        当前节点为{{ activeTodo?.signTypeName }}：或签任一人通过即推进，其余任务自动失效；会签需全部通过。
      </p>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="success" :loading="saving" @click="confirmApprove">确认通过</el-button>
      </template>
    </el-dialog>

    <!-- 审批驳回（M2-3-3）：意见必填 -->
    <el-dialog v-model="rejectVisible" title="审批驳回" width="520px">
      <el-form label-width="70px">
        <el-form-item label="驳回意见" required>
          <el-input
            v-model="rejectOpinion"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写驳回原因，申请人修改后重新提交"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 按行退回（M2-2-4）：勾选行拆分为退回草稿单 -->
    <el-dialog v-model="returnVisible" title="按行退回" width="760px">
      <template v-if="returnBill">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="勾选需要退回的明细行：这些行拆分为驳回态草稿单交申请人修改，其余行继续审批；全部勾选等同于整单驳回。"
          class="dialog-alert"
        />
        <el-table
          :data="returnBill.items"
          border
          size="small"
          @selection-change="(rows: RequestItemRow[]) => (returnItemIds = rows.map((r) => r.id))"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="itemNo" label="#" width="44" />
          <el-table-column prop="materialNo" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">
              {{ row.quantity }} {{ row.unitName }}
            </template>
          </el-table-column>
          <el-table-column label="预估金额" width="100" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.estAmount) }}</template>
          </el-table-column>
        </el-table>
        <el-form label-width="70px" class="return-opinion">
          <el-form-item label="退回意见" required>
            <el-input
              v-model="returnOpinion"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="请填写退回原因，申请人修改后重新提交"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="confirmReturn">确认退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as m2Api from '@/api/m2'
import type { RequestItemRow, RequestRow, TodoRow } from '@/api/m2'

const loading = ref(false)
const saving = ref(false)
const rows = ref<TodoRow[]>([])
const total = ref(0)

const query = reactive({
  pageNo: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const result = await m2Api.todoRequests({ pageNo: query.pageNo, pageSize: query.pageSize })
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

// ---------------- 展示辅助 ----------------

function formatAmount(value: number | undefined | null) {
  if (value == null) return '0.00'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(value: string | null | undefined) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
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

async function openDetail(row: TodoRow) {
  detail.value = await m2Api.getRequest(row.billId)
  detailVisible.value = true
}

// ---------------- 审批操作 ----------------

const activeTodo = ref<TodoRow | null>(null)

const approveVisible = ref(false)

function openApprove(row: TodoRow) {
  activeTodo.value = row
  approveVisible.value = true
}

async function confirmApprove() {
  if (!activeTodo.value) return
  saving.value = true
  try {
    await m2Api.approveRequest(activeTodo.value.billId)
    ElMessage.success('已通过')
    approveVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const rejectVisible = ref(false)
const rejectOpinion = ref('')

function openReject(row: TodoRow) {
  activeTodo.value = row
  rejectOpinion.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!activeTodo.value) return
  if (!rejectOpinion.value.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  saving.value = true
  try {
    await m2Api.rejectRequest(activeTodo.value.billId, rejectOpinion.value.trim())
    ElMessage.success('已驳回')
    rejectVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const returnVisible = ref(false)
const returnBill = ref<RequestRow | null>(null)
const returnOpinion = ref('')
const returnItemIds = ref<number[]>([])

async function openReturn(row: TodoRow) {
  returnBill.value = await m2Api.getRequest(row.billId)
  returnItemIds.value = []
  returnOpinion.value = ''
  returnVisible.value = true
}

async function confirmReturn() {
  if (!activeTodo.value) return
  if (!returnItemIds.value.length) {
    ElMessage.warning('请先勾选要退回的明细行')
    return
  }
  if (!returnOpinion.value.trim()) {
    ElMessage.warning('请填写退回意见')
    return
  }
  await ElMessageBox.confirm(
    `确认退回 ${returnItemIds.value.length} 行明细？退回行将拆分为草稿单交申请人修改。`,
    '按行退回确认',
    { type: 'warning' }
  )
  saving.value = true
  try {
    await m2Api.returnItems(
      activeTodo.value.billId,
      returnItemIds.value,
      returnOpinion.value.trim()
    )
    ElMessage.success('已按行退回，拆分单已生成')
    returnVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
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

.dialog-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.dialog-alert {
  margin-bottom: 12px;
}

.return-opinion {
  margin-top: 12px;
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

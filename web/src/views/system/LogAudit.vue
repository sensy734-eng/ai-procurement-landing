<template>
  <div class="page-card log-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">日志审计</h2>
        <p class="text-sub">
          M11-4-4 操作日志检索 · M11-4-5 智能查询审计 · M11-4-6 审计详情与导出
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="操作日志" name="operation">
        <el-form :inline="true" class="filter-bar" @submit.prevent>
          <el-form-item label="操作人">
            <el-input v-model="logQuery.username" placeholder="账号" clearable style="width: 150px" />
          </el-form-item>
          <el-form-item label="模块">
            <el-select v-model="logQuery.moduleCode" placeholder="全部" clearable style="width: 120px">
              <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="动作">
            <el-input v-model="logQuery.action" placeholder="如 grant" clearable style="width: 140px" />
          </el-form-item>
          <el-form-item label="时间">
            <el-date-picker
              v-model="logRange"
              type="datetimerange"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 340px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadLogs(1)">查询</el-button>
            <el-button v-if="store.hasPerm('m11:log:export')" :icon="Download" @click="exportLogs">导出</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="logLoading" :data="logRows" border stripe>
          <el-table-column prop="createdAt" label="时间" width="165">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作人" width="150">
            <template #default="{ row }">{{ row.realName || '-' }}（{{ row.username || '-' }}）</template>
          </el-table-column>
          <el-table-column prop="moduleCode" label="模块" width="80" align="center" />
          <el-table-column prop="action" label="动作" width="120" />
          <el-table-column label="对象" width="180">
            <template #default="{ row }">
              <span>{{ row.targetNo || row.targetType || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" width="130" />
        </el-table>

        <footer class="pager">
          <el-pagination
            v-model:current-page="logQuery.pageNo"
            v-model:page-size="logQuery.pageSize"
            :total="logTotal"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadLogs"
            @size-change="loadLogs(1)"
          />
        </footer>
      </el-tab-pane>

      <el-tab-pane label="智能查询审计" name="audit">
        <el-form :inline="true" class="filter-bar" @submit.prevent>
          <el-form-item label="用户">
            <el-input v-model="auditQuery.username" placeholder="账号" clearable style="width: 150px" />
          </el-form-item>
          <el-form-item label="意图">
            <el-input v-model="auditQuery.intentCode" placeholder="如 order_status" clearable style="width: 170px" />
          </el-form-item>
          <el-form-item label="结果码">
            <el-select v-model="auditQuery.resultCode" placeholder="全部" clearable style="width: 160px">
              <el-option v-for="code in resultCodes" :key="code" :label="code" :value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="时间">
            <el-date-picker
              v-model="auditRange"
              type="datetimerange"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 340px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadAudits(1)">查询</el-button>
            <el-button v-if="store.hasPerm('m11:log:export')" :icon="Download" @click="exportAudits">导出</el-button>
          </el-form-item>
        </el-form>

        <el-alert class="tip" type="info" :closable="false" show-icon
          title="每条智能请求都留审计，含原始问句、解析参数、被拒参数与注入的数据范围；审计只读，后台不能改写。" />

        <el-table v-loading="auditLoading" :data="auditRows" border stripe>
          <el-table-column prop="createdAt" label="时间" width="165">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="username" label="用户" width="120" />
          <el-table-column prop="question" label="原始问句" min-width="240" show-overflow-tooltip />
          <el-table-column prop="intentCode" label="意图" width="150" />
          <el-table-column label="结果码" width="140">
            <template #default="{ row }">
              <el-tag :type="row.resultCode === 'Success' ? 'success' : 'warning'" size="small">
                {{ row.resultCode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rowCount" label="行数" width="80" align="center" />
          <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" align="center" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openAudit(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <footer class="pager">
          <el-pagination
            v-model:current-page="auditQuery.pageNo"
            v-model:page-size="auditQuery.pageSize"
            :total="auditTotal"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadAudits"
            @size-change="loadAudits(1)"
          />
        </footer>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="drawerVisible" title="审计详情" size="520px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="审计id">{{ current.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(current.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ current.username }}</el-descriptions-item>
        <el-descriptions-item label="原始问句">{{ current.question }}</el-descriptions-item>
        <el-descriptions-item label="意图编码">{{ current.intentCode || '未识别' }}</el-descriptions-item>
        <el-descriptions-item label="解析参数">
          <pre class="json">{{ pretty(current.paramsJson) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="被拒参数">
          <pre class="json rejected">{{ pretty(current.rejectedParamsJson) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="注入的数据范围">
          <pre class="json">{{ pretty(current.dataScopeJson) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="结果码">{{ current.resultCode }}</el-descriptions-item>
        <el-descriptions-item label="返回行数 / 耗时">
          {{ current.rowCount }} 行 / {{ current.elapsedMs }} 毫秒
        </el-descriptions-item>
        <el-descriptions-item label="来源IP">{{ current.ip || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Search } from '@element-plus/icons-vue'
import {
  downloadCsv,
  operationLogExportUrl,
  pageOperationLogs,
  pageQueryAudits,
  queryAuditExportUrl,
  type OperationLogRow,
  type QueryAuditRow
} from '@/api/m11'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const activeTab = ref('operation')
const modules = ['M1', 'M2', 'M3', 'M4', 'M5', 'M6', 'M7', 'M8', 'M9', 'M10', 'M11', 'M12']
const resultCodes = ['Success', 'NotFound', 'Ambiguous', 'NoPermission', 'NoData', 'Unsupported', 'InvalidParameter']

const logLoading = ref(false)
const logRows = ref<OperationLogRow[]>([])
const logTotal = ref(0)
const logRange = ref<[string, string] | null>(null)
const logQuery = reactive<{ pageNo: number; pageSize: number; username: string; moduleCode?: string; action: string }>({
  pageNo: 1,
  pageSize: 20,
  username: '',
  action: ''
})

const auditLoading = ref(false)
const auditRows = ref<QueryAuditRow[]>([])
const auditTotal = ref(0)
const auditRange = ref<[string, string] | null>(null)
const auditQuery = reactive<{ pageNo: number; pageSize: number; username: string; intentCode: string; resultCode?: string }>({
  pageNo: 1,
  pageSize: 20,
  username: '',
  intentCode: ''
})

const drawerVisible = ref(false)
const current = ref<QueryAuditRow | null>(null)

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function pretty(value?: string) {
  if (!value) {
    return '（空）'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function logParams() {
  return {
    username: logQuery.username.trim() || undefined,
    moduleCode: logQuery.moduleCode,
    action: logQuery.action.trim() || undefined,
    startTime: logRange.value?.[0],
    endTime: logRange.value?.[1]
  }
}

function auditParams() {
  return {
    username: auditQuery.username.trim() || undefined,
    intentCode: auditQuery.intentCode.trim() || undefined,
    resultCode: auditQuery.resultCode,
    startTime: auditRange.value?.[0],
    endTime: auditRange.value?.[1]
  }
}

async function loadLogs(page = logQuery.pageNo) {
  logLoading.value = true
  try {
    const result = await pageOperationLogs({ ...logParams(), pageNo: page, pageSize: logQuery.pageSize })
    logRows.value = result.records
    logTotal.value = result.total
    logQuery.pageNo = result.pageNo
  } finally {
    logLoading.value = false
  }
}

async function loadAudits(page = auditQuery.pageNo) {
  auditLoading.value = true
  try {
    const result = await pageQueryAudits({ ...auditParams(), pageNo: page, pageSize: auditQuery.pageSize })
    auditRows.value = result.records
    auditTotal.value = result.total
    auditQuery.pageNo = result.pageNo
  } finally {
    auditLoading.value = false
  }
}

async function exportLogs() {
  await downloadCsv(operationLogExportUrl(logParams()), `操作日志_${Date.now()}.csv`)
  ElMessage.success('已导出 CSV，Excel 可直接打开')
}

async function exportAudits() {
  await downloadCsv(queryAuditExportUrl(auditParams()), `智能查询审计_${Date.now()}.csv`)
  ElMessage.success('已导出 CSV，Excel 可直接打开')
}

function openAudit(row: QueryAuditRow) {
  current.value = row
  drawerVisible.value = true
}

onMounted(() => {
  loadLogs(1)
  loadAudits(1)
})
</script>

<style scoped>
.log-page {
  padding: 20px 24px;
}

.toolbar {
  margin-bottom: 6px;
}

.toolbar .text-sub {
  margin: 6px 0 0;
}

.filter-bar {
  margin-bottom: 4px;
}

.tip {
  margin-bottom: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.json {
  margin: 0;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.json.rejected {
  background: #fdf6ec;
  color: #b88230;
}
</style>

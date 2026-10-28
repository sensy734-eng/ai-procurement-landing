<template>
  <div class="page-card audit-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">查询审计</h2>
        <p class="text-sub">
          M9-5-5 查询审计 · M8-3-4 引用覆盖率 · M8-5-3 拒答统计（受控可验证的凭据）
        </p>
      </div>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
    </header>

    <el-alert class="tip" type="info" :closable="false" show-icon
              title="每次问答无论成功、拒绝还是拒答都写一条审计：问句、意图、通过校验的参数、被拒参数与原因、注入的数据范围、结果码与耗时。这里是「受控」二字的可验证凭据。" />

    <!-- 概览卡 -->
    <el-row :gutter="12" class="stats">
      <el-col :span="4">
        <div class="stat">
          <div class="stat-value">{{ stats?.total ?? '—' }}</div>
          <div class="stat-label">查询总数</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat">
          <div class="stat-value">{{ percent(stats?.citationCoverage) }}</div>
          <div class="stat-label">引用覆盖率</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat">
          <div class="stat-value">{{ percent(stats?.refusalRate) }}</div>
          <div class="stat-label">拒答率（{{ stats?.refusalCount ?? 0 }} 条）</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat">
          <div class="stat-value">{{ stats?.noPermissionCount ?? 0 }}</div>
          <div class="stat-label">越权被拒</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat">
          <div class="stat-value">{{ stats?.invalidParamCount ?? 0 }}</div>
          <div class="stat-label">白名单拦截</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat">
          <div class="stat-value">{{ stats?.effectiveAnswers ?? 0 }}</div>
          <div class="stat-label">知识类有效回答</div>
        </div>
      </el-col>
    </el-row>

    <!-- 分布 -->
    <el-row :gutter="12">
      <el-col :span="12">
        <div class="panel">
          <div class="panel-title">结果码分布（七类）</div>
          <div v-for="row in resultCodeRows" :key="row.code" class="bar-row">
            <span class="bar-label">
              <el-tag size="small" :type="tagTypeOf(row.code)">{{ row.code }}</el-tag>
              <span class="muted">{{ resultCodeText[row.code] }}</span>
            </span>
            <el-progress :percentage="row.percent" :stroke-width="12" :show-text="false"
                         :color="barColor(row.code)" />
            <span class="bar-value">{{ row.count }}</span>
          </div>
          <el-empty v-if="!resultCodeRows.length" description="暂无数据" :image-size="50" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <div class="panel-title">意图分布（含未路由与知识类）</div>
          <div v-for="row in intentRows" :key="row.code" class="bar-row">
            <span class="bar-label">
              <el-tag size="small" effect="plain">{{ intentLabel(row.code) }}</el-tag>
            </span>
            <el-progress :percentage="row.percent" :stroke-width="12" :show-text="false" color="#409eff" />
            <span class="bar-value">{{ row.count }}</span>
          </div>
          <el-empty v-if="!intentRows.length" description="暂无数据" :image-size="50" />
        </div>
      </el-col>
    </el-row>

    <!-- 明细 -->
    <div class="panel">
      <div class="panel-title">审计明细</div>
      <el-form :inline="true" class="filters">
        <el-form-item label="账号">
          <el-input v-model="filters.username" placeholder="如 caigou01" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="意图">
          <el-select v-model="filters.intentCode" placeholder="全部" clearable style="width: 200px">
            <el-option v-for="item in intentOptions" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果码">
          <el-select v-model="filters.resultCode" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="(text, code) in resultCodeText" :key="code" :label="code + ' · ' + text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" size="small" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <div><b>通过校验的参数</b>：{{ pretty(row.paramsJson) }}</div>
              <div><b>被拒参数与原因</b>：{{ pretty(row.rejectedParamsJson) }}</div>
              <div><b>注入的数据范围</b>：{{ pretty(row.dataScopeJson) }}</div>
              <div><b>会话 / 请求</b>：会话 {{ row.sessionId ?? '—' }} · {{ row.ip || '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" min-width="150">
          <template #default="{ row }">{{ shortTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="账号" width="110" />
        <el-table-column prop="question" label="问句" min-width="240" show-overflow-tooltip />
        <el-table-column prop="intentCode" label="意图" width="170">
          <template #default="{ row }">{{ row.intentCode ? intentLabel(row.intentCode) : '未路由' }}</template>
        </el-table-column>
        <el-table-column prop="resultCode" label="结果码" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeOf(row.resultCode)">{{ row.resultCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="70" />
        <el-table-column prop="elapsedMs" label="耗时(ms)" width="90" />
      </el-table>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next"
                     :total="total" :current-page="page.pageNo" :page-size="page.pageSize"
                     :page-sizes="[10, 20, 50]" @current-change="onPageChange" @size-change="onSizeChange" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getAiAuditStats, resultCodeTag, resultCodeText } from '@/api/ai'
import type { AiAuditStats } from '@/api/ai'
import { pageQueryAudits } from '@/api/m11'
import type { QueryAuditRow } from '@/api/m11'

const stats = ref<AiAuditStats | null>(null)
const rows = ref<QueryAuditRow[]>([])
const total = ref(0)
const loading = ref(false)
const filters = reactive({ username: '', intentCode: '', resultCode: '' })
const page = reactive({ pageNo: 1, pageSize: 20 })

const RESULT_CODES = ['Success', 'NoData', 'NotFound', 'Ambiguous', 'InvalidParameter', 'Unsupported', 'NoPermission']

const INTENT_LABELS: Record<string, string> = {
  order_status: '订单状态查询',
  delivery_progress: '交付进度查询',
  purchase_price_history: '历史采购价格查询',
  stock_available: '库存余量与可用量查询',
  supplier_delivery: '供应商历史交付情况查询',
  kb_document_qa: '制度条款问答',
  kb_tutorial_qa: '操作教程问答',
  unrouted: '未路由（超范围/只读拒绝）'
}

const intentOptions = computed(() =>
  Object.keys(stats.value?.byIntent || {}).map((code) => ({ code, label: intentLabel(code) }))
)

const resultCodeRows = computed(() => {
  const source = stats.value?.byResultCode || {}
  const max = Math.max(1, ...Object.values(source))
  return RESULT_CODES.filter((code) => source[code] !== undefined).map((code) => ({
    code,
    count: source[code],
    percent: Math.round((source[code] / max) * 100)
  }))
})

const intentRows = computed(() => {
  const source = stats.value?.byIntent || {}
  const max = Math.max(1, ...Object.values(source))
  return Object.keys(source)
    .sort((a, b) => source[b] - source[a])
    .map((code) => ({ code, count: source[code], percent: Math.round((source[code] / max) * 100) }))
})

function intentLabel(code: string) {
  return INTENT_LABELS[code] || code
}

function tagTypeOf(code: string) {
  return (resultCodeTag[code] || 'info') as 'success' | 'warning' | 'danger' | 'info'
}

function barColor(code: string) {
  const map: Record<string, string> = {
    Success: '#67c23a',
    Ambiguous: '#e6a23c',
    InvalidParameter: '#e6a23c',
    NoPermission: '#f56c6c',
    Unsupported: '#f56c6c',
    NoData: '#909399',
    NotFound: '#909399'
  }
  return map[code] || '#409eff'
}

function percent(value?: number) {
  if (value === null || value === undefined) {
    return '—'
  }
  return (Number(value) * 100).toFixed(1) + '%'
}

function shortTime(value: string) {
  return (value || '').replace('T', ' ').slice(0, 19)
}

function pretty(value?: string | null) {
  if (!value) {
    return '—'
  }
  return value
}

async function loadStats() {
  try {
    stats.value = await getAiAuditStats()
  } catch (error) {
    stats.value = null
  }
}

async function loadRows() {
  loading.value = true
  try {
    const result = await pageQueryAudits({
      pageNo: page.pageNo,
      pageSize: page.pageSize,
      username: filters.username || undefined,
      intentCode: filters.intentCode || undefined,
      resultCode: filters.resultCode || undefined
    })
    rows.value = result.records || []
    total.value = result.total || 0
  } catch (error) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.pageNo = 1
  loadRows()
}

function reset() {
  filters.username = ''
  filters.intentCode = ''
  filters.resultCode = ''
  page.pageNo = 1
  loadRows()
}

function onPageChange(pageNo: number) {
  page.pageNo = pageNo
  loadRows()
}

function onSizeChange(size: number) {
  page.pageSize = size
  page.pageNo = 1
  loadRows()
}

async function reload() {
  await Promise.all([loadStats(), loadRows()])
}

onMounted(reload)
</script>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}
.tip {
  margin: 0;
}
.stats {
  margin-bottom: 0;
}
.stat {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  text-align: center;
  background: #fafbfc;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.panel {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.bar-label {
  width: 190px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.bar-row :deep(.el-progress) {
  flex: 1;
}
.bar-value {
  width: 44px;
  text-align: right;
  font-size: 12px;
  color: #606266;
}
.filters {
  margin-bottom: 4px;
}
.detail {
  padding: 8px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>

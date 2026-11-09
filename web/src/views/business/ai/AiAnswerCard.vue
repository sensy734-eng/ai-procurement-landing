<template>
  <div class="answer-card" :class="'code-' + code">
    <div class="head">
      <el-tag v-if="result.routeName" size="small" effect="plain">{{ result.routeName }}</el-tag>
      <el-tag v-if="result.intentName" size="small" type="info" effect="plain">
        {{ result.intentName }}
      </el-tag>
      <span v-if="result.confidence && !result.answerMode" class="muted">
        意图置信度 {{ percent(result.confidence) }}
      </span>
      <el-tag v-if="result.table" size="small" effect="plain">数据查询结果</el-tag>
      <el-tag size="small" :type="tagTypeOf(code)">{{ result.answerMode === 'references_only' ? '未生成可靠答案' : result.answerMode === 'refused' ? '依据不足' : resultCodeText[code] || code }}</el-tag>
      <el-tag v-if="result.answerMode" size="small" :type="answerModeType">
        {{ answerModeText }}
      </el-tag>
      <el-tag v-if="result.retrievalConfigId" size="small" effect="plain">
        {{ retrievalLabel }}
      </el-tag>
      <span class="muted">{{ result.elapsedMs || 0 }} ms</span>
    </div>

    <!-- M7-3-1 解析结果卡片 -->
    <div v-if="card" class="card">
      <div class="card-head">
        <span class="card-title">识别结果（改参数可重跑）</span>
        <el-button v-if="card.editable && retryEnabled" link type="primary" size="small"
                   @click="emit('retry', { question, card })">
          修正参数重跑
        </el-button>
      </div>
      <div class="line">
        <span class="label">意图</span>
        <span>{{ card.intentName }}（{{ card.intentCode }}）</span>
        <span class="muted">→ {{ card.serviceCode }}</span>
      </div>
      <div v-if="(card.entities || []).length" class="line">
        <span class="label">实体</span>
        <el-tag v-for="entity in card.entities" :key="entity.type + '-' + entity.id" size="small" effect="plain">
          {{ entity.typeName }} {{ entity.name }}（{{ entity.no }}）
        </el-tag>
      </div>
      <div v-if="hasTimeRange" class="line">
        <span class="label">时间范围</span>
        <span>{{ card.timeRange?.label || '' }} {{ card.timeRange?.from || '不限' }} ~ {{ card.timeRange?.to || '不限' }}</span>
      </div>
      <div v-if="(card.filters || []).length" class="line">
        <span class="label">筛选条件</span>
        <el-tag v-for="filter in card.filters" :key="filter.code" size="small" type="success" effect="plain">
          {{ filter.name }} {{ opText(filter.op) }} {{ filterText(filter) }}
          <template v-if="filter.source === 'override'">（已修正）</template>
        </el-tag>
      </div>
      <div v-if="(card.missing || []).length" class="line">
        <span class="label">待补充</span>
        <el-tag v-for="item in card.missing" :key="item.code" size="small" type="warning" effect="plain">
          {{ item.name }}
        </el-tag>
      </div>
      <div v-if="(card.rejected || []).length" class="line rejected">
        <span class="label">被拒参数</span>
        <div class="rejected-list">
          <div v-for="item in card.rejected" :key="item.code">
            {{ item.name }}＝{{ display(item.value) }}：{{ item.reason }}
          </div>
        </div>
      </div>
    </div>

    <!-- 答案正文 -->
    <div v-if="result.answer" class="text">{{ result.answer }}</div>

    <!-- 拒答原因 -->
    <el-alert v-if="result.answerMode === 'references_only'" class="block" type="warning" :closable="false" show-icon
              title="暂时无法生成可靠答案，以下内容仅供参考"
              :description="degradedReasonText" />
    <el-alert v-else-if="result.refusalReason" class="block" type="warning" :closable="false" show-icon
              title="检索依据不足，已拒答" :description="result.refusalReason" />

    <!-- 澄清选项 -->
    <div v-if="clarify" class="block clarify">
      <div class="clarify-q">{{ clarify.question }}</div>
      <div class="options">
        <el-button v-for="(option, index) in clarify.options" :key="index" size="small"
                   @click="emit('pick', { option, clarify: clarify!.type })">
          {{ option.label }}
          <span v-if="option.detail" class="muted"> · {{ option.detail }}</span>
        </el-button>
      </div>
    </div>

    <!-- 结果表格 -->
    <div v-if="result.table && (result.table.rows || []).length" class="block">
      <el-table :data="result.table.rows" size="small" border max-height="300">
        <el-table-column v-for="column in result.table.columns" :key="column.key"
                         :prop="column.key" :label="column.label" min-width="110" />
      </el-table>
      <div class="muted table-caption">
        共 {{ result.table.total }} 行 · {{ result.table.caption }}
      </div>
    </div>

    <!-- 来源引用 -->
    <div v-if="(result.refs || []).length" class="block">
      <div class="section-title">
        {{ result.answerMode === 'generated' ? '回答依据与相关参考' : '相关参考' }}（{{ result.refs!.length }}）
      </div>
      <el-collapse>
        <el-collapse-item v-for="(ref, index) in result.refs" :key="index"
                          :name="'ref-' + index">
          <template #title>
            <span class="ref-title">
              《{{ ref.docName }}》{{ ref.sectionNo || '' }}
              <el-tag v-if="citationLabel(ref)" size="small" effect="plain"
                      :type="citationLabel(ref) === '回答依据' ? 'success' : 'info'">
                {{ citationLabel(ref) }}
              </el-tag>
              <el-tag v-if="ref.path === 'both'" size="small" type="success" effect="plain">两路同源</el-tag>
              <el-tag v-else-if="ref.path" size="small" effect="plain">{{ pathText(ref.path) }}</el-tag>
              <el-tag v-if="ref.valid === false" size="small" type="danger" effect="plain">
                引用失效：{{ ref.invalidReason }}
              </el-tag>
            </span>
          </template>
          <p class="snippet">{{ ref.snippet }}</p>
          <div class="ref-foot">
            <span class="muted">
              文档版本 {{ ref.docVersion || '—' }}
              <template v-if="ref.effectiveDate"> · 生效 {{ ref.effectiveDate }}</template>
              <template v-if="ref.score !== null && ref.score !== undefined"> · 相关度 {{ ref.score }}</template>
              · 切块 {{ ref.chunkId }}
            </span>
            <el-button link type="primary" size="small" :loading="checking === ref.chunkId"
                       @click="checkCitation(ref)">
              校验引用有效性
            </el-button>
          </div>
          <div v-if="checkResults[ref.chunkId]" class="check-result">
            {{ checkResults[ref.chunkId] }}
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 双路召回对照 -->
    <div v-if="(result.dualPath || []).length" class="block">
      <el-collapse>
        <el-collapse-item :title="'双路召回对照（稀疏 ' + sparseCount + ' 条 / 向量 ' + vectorCount + ' 条）'">
          <div class="dual">
            <div class="dual-col">
              <div class="dual-title">稀疏路（BM25，字符二元组）</div>
              <div v-for="item in sparseItems" :key="'s' + item.chunkId" class="dual-item">
                <span class="muted">#{{ item.rank }}</span>
                《{{ item.docName }}》{{ item.sectionNo }} · {{ item.score }}
              </div>
            </div>
            <div class="dual-col">
              <div class="dual-title">{{ result.retrievalConfigId?.includes('semantic') ? '向量路（云端语义嵌入）' : '向量路（二元组哈希向量）' }}</div>
              <div v-for="item in vectorItems" :key="'v' + item.chunkId" class="dual-item">
                <span class="muted">#{{ item.rank }}</span>
                《{{ item.docName }}》{{ item.sectionNo }} · {{ item.score }}
              </div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 版本冲突提示 -->
    <el-alert v-if="result.versionConflict" class="block" type="info" :closable="false" show-icon
              title="命中多个版本文档"
              :description="result.versionConflict.message || JSON.stringify(result.versionConflict)" />

    <!-- 重跑对比 -->
    <div v-if="result.compare" class="block compare">
      <span class="label">重跑对比</span>
      <div>
        上一轮：{{ resultCodeText[result.compare.previousResultCode || ''] || result.compare.previousResultCode }}
        · {{ result.compare.previousSummary }}
        <template v-if="result.compare.previousRowCount !== null && result.compare.previousRowCount !== undefined">
          （{{ result.compare.previousRowCount }} 行）
        </template>
      </div>
      <div v-if="(result.compare.changedParams || []).length" class="muted">
        变更参数：{{ changedParamsText }}
      </div>
    </div>

    <!-- 结果码提示 -->
    <div v-if="result.resultCodeHint && code !== 'Success'" class="block hint">
      {{ result.answerMode === 'references_only' ? 'NoData：本次未生成可靠结论，请人工核对参考原文。' : result.resultCodeHint }}
    </div>

    <div v-if="(result.notices || []).length" class="notices">
      <span v-for="(notice, index) in result.notices" :key="index">· {{ notice }}</span>
    </div>

    <div class="foot">
      <el-button v-for="link in businessLinks" :key="link.path" type="primary" plain size="small" @click="router.push(link.path)">{{ link.title }}</el-button>
      <el-button link size="small" @click="emit('feedback', { question })">解析有误？反馈</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { resultCodeTag, resultCodeText, verifyCitation } from '@/api/ai'
import type { AiChatResult, AiCitation, AiIntentCard, AiOption, AiParam } from '@/api/ai'

const props = defineProps<{
  result: AiChatResult
  code: string
  question: string
  retryEnabled: boolean
}>()

const emit = defineEmits<{
  (event: 'retry', payload: { question: string; card: AiIntentCard }): void
  (event: 'pick', payload: { option: AiOption; clarify: string }): void
  (event: 'feedback', payload: { question: string }): void
}>()

const checking = ref<number | null>(null)
const router = useRouter()
const user = useUserStore()
const businessLinks = computed(() => {
  if (!props.result.table) return []
  const routes: Record<string, { path: string; title: string; perm: string }> = {
    order_status: { path: '/order', title: '进入采购订单', perm: 'm3:order:list' },
    delivery_progress: { path: '/order', title: '处理订单交付', perm: 'm3:order:list' },
    supplier_delivery: { path: '/order', title: '查看相关订单', perm: 'm3:order:list' },
    stock_available: { path: '/stock', title: '进入库存台账', perm: 'm5:stock:list' },
    purchase_price_history: { path: '/price', title: '查看价格参考', perm: 'm6:price:list' }
  }
  const link = routes[props.result.intentCode || '']
  return link && user.hasPerm(link.perm) ? [link] : []
})
const checkResults = ref<Record<number, string>>({})

const card = computed(() => props.result.card || null)
const clarify = computed(() => props.result.clarify || null)
const hasTimeRange = computed(() => {
  const range = card.value?.timeRange
  return Boolean(range && (range.from || range.to || range.label))
})
const sparseItems = computed(() => (props.result.dualPath || []).filter((item) => item.path === 'sparse'))
const vectorItems = computed(() => (props.result.dualPath || []).filter((item) => item.path === 'vector'))
const sparseCount = computed(() => sparseItems.value.length)
const vectorCount = computed(() => vectorItems.value.length)
const answerModeText = computed(() => ({
  generated: '生成回答',
  references_only: '仅供参考',
  refused: '无法回答'
}[props.result.answerMode || 'refused'] || ''))
const answerModeType = computed(() => props.result.answerMode === 'generated' ? 'success' : 'warning')
const retrievalLabel = computed(() => {
  const strategy = props.result.retrievalConfigId?.split(':')[0]
  const labels: Record<string, string> = { bm25: 'BM25', bm25_hash: 'BM25 + 哈希向量',
    semantic: '语义向量', bm25_semantic: 'BM25 + 语义向量' }
  return labels[strategy || ''] || strategy || '检索配置未知'
})
const degradedReasonText = computed(() => ({
  model_disabled: '未启用生成模型。',
  model_timeout: '生成模型响应超时。',
  model_rate_limit: '生成模型服务限流，请稍后重试。',
  model_server: '生成模型服务异常。',
  model_bad_json: '生成模型返回了无法解析的内容。',
  model_circuit_open: '生成模型连续失败，当前暂停调用。',
  model_unavailable: '生成模型当前不可用。',
  model_output_invalid: '模型输出未通过格式或引用校验。',
  citations_not_array: '模型返回的引用格式无效。',
  citation_not_integer: '模型引用编号不是整数。',
  citation_out_of_range: '模型引用编号不属于本轮检索结果。',
  duplicate_citation: '模型返回了重复引用。',
  missing_citation: '模型回答缺少有效引用。',
  empty_answer: '模型未返回回答内容。',
  answer_guard_rejected: '模型回答中的数值或内容未通过校验。',
  tutorial_steps_unverified: '检索内容没有可核对的有序步骤。'
}[props.result.degradedReason || ''] || '系统无法验证模型回答。'))

/** 变更参数里的字段编码换成中文名，方便核对（后端 diff 输出固定为「编码：旧 → 新」） */
const changedParamsText = computed(() =>
  (props.result.compare?.changedParams || [])
    .map((item) => item
      .replace('material_id', '物料')
      .replace('supplier_id', '供应商')
      .replace('warehouse_id', '仓库')
      .replace('date_from', '开始日期')
      .replace('date_to', '结束日期')
      .replace('amount_gte', '金额下限')
      .replace('amount_lte', '金额上限'))
    .join('、')
)

function percent(value: number) {
  return (Number(value) * 100).toFixed(1) + '%'
}

function tagTypeOf(code: string) {
  return (resultCodeTag[code] || 'info') as 'success' | 'warning' | 'danger' | 'info'
}

function opText(op?: string) {
  const map: Record<string, string> = {
    eq: '=', like: '包含', gte: '≥', lte: '≤', in: '属于', between: '介于'
  }
  return op ? map[op] || op : '='
}

function display(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  return String(value)
}

/**
 * 实体型筛选条件显示实体名而不是主键 id：
 * 「物料 = 1」对用户没有意义，参数可核对的粒度是业务对象不是主键。
 */
function filterText(filter: AiParam) {
  const entityType: Record<string, number> = { material_id: 1, supplier_id: 2, warehouse_id: 3 }
  const type = entityType[filter.code]
  if (type !== undefined) {
    const entity = (card.value?.entities || []).find(
      (item) => item.type === type && item.id === filter.value
    )
    if (entity) {
      return entity.name
    }
  }
  return display(filter.value)
}

function pathText(path: string) {
  return path === 'sparse' ? '稀疏路' : path === 'vector' ? '向量路' : path
}

function citationLabel(ref: AiCitation) {
  const evidence = props.result.evidenceChunkIds
  if (!evidence) return ''
  return evidence.includes(ref.chunkId) ? '回答依据' : '相关参考'
}

async function checkCitation(ref: AiCitation) {
  checking.value = ref.chunkId
  try {
    const check = await verifyCitation(ref.docId, ref.chunkId)
    checkResults.value = {
      ...checkResults.value,
      [ref.chunkId]: check.valid
        ? `校验通过：切块确实属于《${check.docName}》${check.docVersion || ''}`
        : `校验失败：${check.reason}`
    }
  } catch (error) {
    checkResults.value = { ...checkResults.value, [ref.chunkId]: '校验请求失败' }
  } finally {
    checking.value = null
  }
}
</script>

<style scoped>
.answer-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.answer-card.code-Success {
  border-left: 3px solid #67c23a;
}
.answer-card.code-NoData,
.answer-card.code-NotFound {
  border-left: 3px solid #909399;
}
.answer-card.code-Ambiguous,
.answer-card.code-InvalidParameter {
  border-left: 3px solid #e6a23c;
}
.answer-card.code-NoPermission,
.answer-card.code-Unsupported {
  border-left: 3px solid #f56c6c;
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.card {
  background: #f7f8fa;
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 600;
  font-size: 13px;
}
.line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
}
.line .label {
  color: #606266;
  min-width: 60px;
}
.rejected .rejected-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #f56c6c;
  font-size: 12px;
}
.text {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 14px;
  color: #303133;
}
.block {
  width: 100%;
}
.clarify {
  background: #fdf6ec;
  border-radius: 6px;
  padding: 10px;
}
.clarify-q {
  font-size: 13px;
  color: #b88230;
  margin-bottom: 8px;
}
.options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}
.ref-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.snippet {
  margin: 0 0 6px;
  line-height: 1.7;
  font-size: 13px;
  color: #303133;
  background: #f7f8fa;
  padding: 8px;
  border-radius: 4px;
}
.ref-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.check-result {
  margin-top: 4px;
  font-size: 12px;
  color: #409eff;
}
.dual {
  display: flex;
  gap: 16px;
}
.dual-col {
  flex: 1;
  min-width: 0;
}
.dual-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 4px;
}
.dual-item {
  font-size: 12px;
  color: #303133;
  padding: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.compare {
  background: #f0f9eb;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
}
.compare .label {
  color: #606266;
  margin-right: 6px;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.table-caption {
  margin-top: 4px;
}
.notices {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: #909399;
}
.foot {
  display: flex;
  justify-content: flex-end;
}
</style>

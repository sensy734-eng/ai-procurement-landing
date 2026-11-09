<template>
  <div class="kb-panel">
    <el-alert class="tip" type="info" :closable="false" show-icon
      title="四组检索对照：使用相同语料与 Top-K；语义策略需先显式构建云端索引。默认严格模式，失败会报错，不会悄悄切换策略。统计仅汇总所选策略，正式实验使用冻结评测命令。" />

    <header class="toolbar">
      <div class="filters">
        <el-select v-model="strategy" style="width: 220px" aria-label="检索策略" @change="loadStat">
          <el-option v-for="item in strategies" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-checkbox v-model="strict">严格模式</el-checkbox>
        <el-input v-model="question" placeholder="输入问句，如：采购申请提交后几个工作日内完成审批？" style="width: 380px" @keyup.enter="run" />
        <el-select v-model="expectDocId" placeholder="期望文档（可选）" clearable filterable style="width: 260px">
          <el-option
            v-for="doc in docOptions"
            :key="doc.id"
            :label="`${doc.docName}（v${doc.version}）`"
            :value="doc.id"
          />
        </el-select>
        <el-tooltip content="每路召回条数，默认取 kb_recall_topk（5）">
          <el-input-number v-model="topK" :min="1" :max="20" style="width: 110px" />
        </el-tooltip>
        <el-button v-if="store.hasPerm('m10:test:run')" type="primary" :icon="Search" :loading="running" @click="run">运行</el-button>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m10:test:stat')" :icon="DataAnalysis" :loading="statLoading" @click="loadStat">刷新统计</el-button>
      </div>
    </header>

    <div v-if="stat" class="stat-row">
      <el-statistic title="测试总数" :value="stat.total" />
      <el-statistic title="综合命中" :value="stat.hitCount">
        <template #suffix>/ {{ stat.total }}（{{ percent(stat.hitRate) }}）</template>
      </el-statistic>
      <el-statistic title="稀疏命中率" :value="percent(stat.bm25Rate)" />
      <el-statistic title="向量命中率" :value="percent(stat.vectorRate)" />
      <el-statistic title="两路均命中" :value="stat.bothHit" />
      <el-statistic title="平均命中排名" :value="stat.avgHitRank == null ? '-' : stat.avgHitRank" />
    </div>

    <div v-if="result" class="result">
      <el-alert v-if="result.degradedReason" type="warning" :closable="false" title="本次检索已降级，不能作为严格对照实验结果。" />
      <p class="text-sub">实际策略：{{ strategies.find(s => s.value === result?.actualStrategy)?.label || result.actualStrategy }} · 配置：{{ result.configId }} · 嵌入 tokens：{{ result.embeddingTokens ?? '未提供' }}</p>
      <div class="result-head">
        <span class="text-sub">问句：{{ result.question }}</span>
        <span v-if="result.expectDocName" class="text-sub">期望文档：{{ result.expectDocName }}</span>
        <el-tag v-if="result.hitRouteName" :type="result.hitFlag === 1 ? 'success' : 'info'" size="small">
          {{ result.hitRouteName }}<template v-if="result.hitRank"> · 排名 {{ result.hitRank }}</template>
        </el-tag>
        <el-tag v-else type="info" size="small">{{ result.expectDocId ? '未命中期望' : '未填期望，仅展示' }}</el-tag>
      </div>

      <h4>最终排名（命中判定依据）</h4>
      <el-table :data="result.ranked" border size="small">
        <el-table-column prop="rank" label="排名" width="70" />
        <el-table-column prop="chunkId" label="切块 ID" width="100" />
        <el-table-column prop="docName" label="文档" min-width="180" />
        <el-table-column prop="snippet" label="原文摘要" min-width="260" />
      </el-table>
      <el-row :gutter="12">
        <el-col :span="12">
          <h4 class="route-title">稀疏检索 · BM25</h4>
          <el-table :data="result.bm25" border size="small" max-height="420">
            <el-table-column prop="rank" label="#" width="44" align="center" />
            <el-table-column label="文档 / 章节" min-width="180">
              <template #default="{ row }">
                <div>{{ row.docName }}</div>
                <div class="text-sub">{{ row.sectionNo }} {{ row.sectionTitle }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="得分" width="80" align="center">
              <template #default="{ row }">{{ row.score.toFixed(4) }}</template>
            </el-table-column>
            <el-table-column label="摘要" min-width="200">
              <template #default="{ row }"><span class="snippet">{{ row.snippet }}</span></template>
            </el-table-column>
          </el-table>
          <el-empty v-if="result.bm25.length === 0" description="无召回" :image-size="60" />
        </el-col>
        <el-col :span="12">
          <h4 class="route-title">{{ result.actualStrategy.includes('semantic') ? '语义向量' : '哈希向量' }} · 余弦</h4>
          <el-table :data="result.vector" border size="small" max-height="420">
            <el-table-column prop="rank" label="#" width="44" align="center" />
            <el-table-column label="文档 / 章节" min-width="180">
              <template #default="{ row }">
                <div>{{ row.docName }}</div>
                <div class="text-sub">{{ row.sectionNo }} {{ row.sectionTitle }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="得分" width="80" align="center">
              <template #default="{ row }">{{ row.score.toFixed(4) }}</template>
            </el-table-column>
            <el-table-column label="摘要" min-width="200">
              <template #default="{ row }"><span class="snippet">{{ row.snippet }}</span></template>
            </el-table-column>
          </el-table>
          <el-empty v-if="result.vector.length === 0" description="无召回" :image-size="60" />
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Search } from '@element-plus/icons-vue'
import {
  getTestStat,
  listDocuments,
  runRetrievalTest,
  type KbDocumentRow,
  type RetrievalTestResult,
  type TestStat
} from '@/api/m10'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const strategies = [
  { value: 'bm25', label: 'BM25' },
  { value: 'bm25_hash', label: 'BM25 + 哈希向量' },
  { value: 'semantic', label: '语义向量' },
  { value: 'bm25_semantic', label: 'BM25 + 语义向量 RRF' }
]
const strategy = ref('bm25_hash')
const strict = ref(true)

const docOptions = ref<KbDocumentRow[]>([])
const question = ref('')
const expectDocId = ref<number>()
const topK = ref<number>()
const running = ref(false)
const result = ref<RetrievalTestResult | null>(null)

const stat = ref<TestStat | null>(null)
const statLoading = ref(false)

function percent(value: number) {
  return (value * 100).toFixed(1) + '%'
}

async function loadDocs() {
  const page = await listDocuments({ pageNo: 1, pageSize: 100 })
  docOptions.value = page.records
}

async function loadStat() {
  if (!store.hasPerm('m10:test:stat')) return
  statLoading.value = true
  try {
    stat.value = await getTestStat({ strategy: strategy.value })
  } finally {
    statLoading.value = false
  }
}

async function run() {
  if (!question.value.trim()) {
    ElMessage.warning('请先输入问句')
    return
  }
  running.value = true
  try {
    const body: {
      question: string
      expectDocId?: number
      topK?: number
      strategy: string
      strict: boolean
    } = { question: question.value.trim(), strategy: strategy.value, strict: strict.value }
    if (expectDocId.value) body.expectDocId = expectDocId.value
    if (topK.value) body.topK = topK.value
    result.value = await runRetrievalTest(body)
    await loadStat()
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadDocs(), loadStat()])
  } catch (error) {
    ElMessage.error((error as Error)?.message || '加载失败')
  }
})
</script>

<style scoped>
.kb-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.filters,
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tip {
  margin: 0;
}
.stat-row {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.route-title {
  margin: 8px 0;
}
.snippet {
  display: block;
  max-height: 72px;
  overflow-y: auto;
  line-height: 1.5;
}
</style>

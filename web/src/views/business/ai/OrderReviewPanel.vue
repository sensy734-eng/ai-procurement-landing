<template>
  <section class="review-panel">
    <h3>订单审查助手</h3>
    <p class="text-sub">汇集本单事实、价格依据与交付检查，生成可核对的解读。审查不会修改订单或代替审批。</p>
    <el-form label-position="top" @submit.prevent="run">
      <el-form-item label="关注的问题">
        <el-input v-model="question" type="textarea" :rows="2" maxlength="600" :disabled="loading" />
      </el-form-item>
      <el-radio-group v-model="focus" :disabled="loading">
        <el-radio-button value="all">全面检查</el-radio-button>
        <el-radio-button value="price">价格</el-radio-button>
        <el-radio-button value="delivery">交付</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="run">开始审查当前订单</el-button>
    </el-form>
    <p v-if="loading" role="status">正在核对授权数据、检查规则并请求模型解读…</p>
    <template v-if="result">
      <el-alert :type="result.mode === 'generated' ? 'success' : 'warning'" :closable="false" show-icon
        :title="result.mode === 'generated' ? 'AI 解读已生成，请核对引用和适用条件' : result.mode === 'stale' ? '数据已变化，请重新审查；下方为旧快照' : '本次仅展示业务事实与规则检查，未生成 AI 解读'"
        :description="taskReason(result.degradedReason)" />
      <p class="text-sub">{{ result.orderNo }} · 读取时间 {{ result.asOf.replace('T', ' ') }} · {{ result.elapsedMs }} ms
        <span v-if="result.model"> · {{ result.model }}</span><span v-if="result.totalTokens != null"> · {{ result.totalTokens }} tokens</span>
      </p>
      <h4>业务检查 <small>由业务代码计算</small></h4>
      <div v-for="check in result.checks" :key="check.code" class="check">
        <el-tag :type="statusType(check.status)">{{ statusName(check.status) }}</el-tag>
        <strong>{{ check.title }}</strong><p>{{ check.detail }}</p>
        <el-button v-for="id in check.evidenceIds" :key="id" link type="primary" @click="showEvidence(id)">依据 [{{ id }}]</el-button>
      </div>
      <h4>AI 解读</h4>
      <p v-if="!result.observations.length" class="text-sub">暂无可靠 AI 解读。规则结果中“未检查”不代表正常。</p>
      <div v-for="(item, i) in result.observations" :key="i" class="check">
        <p>{{ item.text }}</p>
        <el-button v-for="id in item.evidenceIds" :key="id" link type="primary" @click="showEvidence(id)">引用 [{{ id }}]</el-button>
      </div>
      <el-alert v-if="result.missingEvidence.length" title="尚缺依据 / 未检查项" type="info" :closable="false">
        <ul><li v-for="item in result.missingEvidence" :key="item">{{ item }}</li></ul>
      </el-alert>
      <h4>证据与候选制度</h4>
      <el-collapse v-model="expanded">
        <el-collapse-item v-for="e in result.evidence" :key="e.id" :name="e.id" :title="`[${e.id}] ${e.title}${e.kind === 'policy_reference' ? '（相关参考，须核对适用条件）' : ''}`">
          <p class="source-text">{{ e.text }}</p>
          <el-button v-if="e.path" link type="primary" @click="router.push(e.path)">打开来源单据</el-button>
        </el-collapse-item>
      </el-collapse>
      <p class="text-sub">任务 {{ result.runId }} · {{ result.promptVersion }}。引用与数字检查不等于语义正确性保证。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { reviewOrder, taskReason, type OrderReview } from '@/api/aiTasks'
const props = defineProps<{ orderId: number }>()
const router = useRouter()
const question = ref('检查这张订单的价格、交付和需要核实的事项')
const focus = ref('all')
const loading = ref(false)
const result = ref<OrderReview>()
const expanded = ref<number[]>([])
let generation = 0
watch(() => props.orderId, () => { generation++; result.value = undefined; loading.value = false; expanded.value = [] })
onBeforeUnmount(() => generation++)
async function run() {
  const current = ++generation
  loading.value = true
  result.value = undefined
  try {
    const response = await reviewOrder(props.orderId, question.value, focus.value)
    if (current === generation) result.value = response
  } catch { /* API interceptor displays the failure. */ }
  finally { if (current === generation) loading.value = false }
}
function showEvidence(id: number) { if (!expanded.value.includes(id)) expanded.value.push(id) }
function statusType(status: string) { return status === 'attention' ? 'warning' : status === 'pass' ? 'success' : 'info' }
function statusName(status: string) { return ({ attention: '待关注', pass: '规则未见异常', unknown: '未检查', information: '事实' } as Record<string, string>)[status] || status }
</script>

<style scoped>
.review-panel { padding: 4px 0 20px; }
.review-panel :deep(.el-alert) { margin-top: 18px; }
.el-radio-group { margin-right: 16px; }
.check { padding: 12px; margin: 10px 0; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.check strong { margin-left: 10px; }
.check p, .source-text { line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
small { font-weight: normal; color: var(--el-text-color-secondary); }
</style>

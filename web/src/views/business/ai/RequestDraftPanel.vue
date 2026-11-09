<template>
  <section class="draft-panel">
    <h3>采购需求起草助手</h3>
    <p class="text-sub">描述物料、数量、日期和归属，核对候选后填入申请表。价格与供应商由你另行填写。</p>
    <div class="examples">
      <el-button size="small" :disabled="busy || validating" @click="text = naturalExample">填入自然语言示例</el-button>
      <el-button size="small" :disabled="busy || validating" @click="text = templateExample">填入离线模板</el-button>
    </div>
    <el-input v-model="text" aria-label="采购需求描述" type="textarea" :rows="3" maxlength="2400" show-word-limit :disabled="busy || validating"
      placeholder="例如：下周五需要二百个 AI演示螺栓，用于公共领用。规格不明确时会请你选择。" />
    <el-button type="primary" :loading="busy" :disabled="!text.trim() || validating" @click="generate">提取候选明细</el-button>
    <template v-if="result">
      <el-alert :title="modeLabel" :type="result.mode === 'generated' ? 'success' : 'warning'" :closable="false"
        :description="taskReason(result.degradedReason)" show-icon />
      <p class="text-sub">日期按 {{ result.today }} 解析 · {{ result.elapsedMs }} ms<span v-if="result.model"> · {{ result.model }}</span></p>
      <el-alert v-if="text !== result.text" title="原始需求已修改，请重新提取后再填表" type="warning" :closable="false" />
      <div v-for="row in selections" :key="row.line" class="candidate">
        <p class="source"><strong>原文 {{ row.line + 1 }}：</strong>{{ result.items[row.line]?.source }}</p>
        <p v-if="result.items[row.line]?.issues.length" class="issues">{{ result.items[row.line]?.issues.join('；') }}</p>
        <el-form label-position="top" :disabled="validating || applied" class="candidate-fields">
          <el-form-item label="物料（核对规格）">
            <el-select v-model="row.materialId" filterable placeholder="选择启用物料" @change="row.unitConfirmed = false">
              <el-option-group v-if="result.items[row.line]?.materialCandidates.length" label="原文匹配候选">
                <el-option v-for="m in result.items[row.line]?.materialCandidates" :key="m.id" :value="m.id" :label="materialLabel(m)" />
              </el-option-group>
              <el-option-group label="全部启用物料（可人工纠正）">
                <el-option v-for="m in result.materials.filter(m => !result?.items[row.line]?.materialCandidates.some(c => c.id === m.id))" :key="m.id" :value="m.id" :label="materialLabel(m)" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item :label="`数量 · 档案单位：${unit(row.materialId)}（原文：${result.items[row.line]?.unit || '未提及'}）`">
            <el-input-number v-model="row.quantity" :min="0.0001" :max="10000000" :precision="4" :controls="false" @change="row.unitConfirmed = false" />
          </el-form-item>
          <el-form-item label="确认具体需求日期">
            <el-date-picker v-model="row.requireDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
          <el-form-item label="归属（明确选择）">
            <el-select v-model="row.ownerKey" placeholder="选择公共或授权对象" @change="chooseOwner(row)">
              <el-option label="公共领用" value="public" />
              <el-option v-for="o in result.owners" :key="o.id" :value="String(o.id)" :label="`${o.name} · ${o.no}`" />
            </el-select>
          </el-form-item>
          <el-checkbox v-model="row.unitConfirmed">已核对数量及档案单位；不进行自动换算</el-checkbox>
        </el-form>
      </div>
      <ul class="text-sub"><li v-for="notice in result.notices" :key="notice">{{ notice }}</li></ul>
      <el-button v-if="selections.length" type="primary" :loading="validating" :disabled="!canApply" @click="apply">
        {{ applied ? '已填入，继续在原表单编辑' : '确认并追加到下方申请表（不保存）' }}
      </el-button>
      <p class="text-sub">任务 {{ result.runId }}</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { draftRequest, validateDraft, taskReason, type RequestDraft, type DraftSelection, type DraftMaterial, type ValidatedDraft } from '@/api/aiTasks'
const emit = defineEmits<{ apply: [draft: ValidatedDraft] }>()
const naturalExample = '下周五需要二百个AI演示螺栓，用于公共领用，请帮我起草申请。'
const templateExample = '物料：AI演示螺栓；数量：二百；单位：件；需求日期：下周五；归属：公共'
const text = ref('')
const busy = ref(false)
const validating = ref(false)
const applied = ref(false)
const result = ref<RequestDraft>()
type EditableLine = DraftSelection & { ownerKey?: string }
const selections = ref<EditableLine[]>([])
let generation = 0
onBeforeUnmount(() => generation++)
const modeLabel = computed(() => result.value?.mode === 'generated' ? 'AI 提取候选，仍需逐项人工确认' : result.value?.mode === 'rule_template' ? '离线模板解析结果（本次未使用 AI 生成）' : '未生成候选，请使用离线模板或手工填写')
const canApply = computed(() => !busy.value && !validating.value && !applied.value && text.value === result.value?.text && selections.value.every(row => row.materialId && row.quantity && row.quantity > 0 && row.requireDate && row.ownerType && row.unitConfirmed))
const materialLabel = (m: DraftMaterial) => `${m.name} ${m.spec || ''} · ${m.no}`
const unit = (id?: number) => result.value?.materials.find(m => m.id === id)?.unit || '待选择'
function chooseOwner(row: EditableLine) {
  const owner = result.value?.owners.find(o => String(o.id) === row.ownerKey)
  row.ownerType = row.ownerKey === 'public' ? 1 : owner?.type
  row.ownerId = owner?.id
}
async function generate() {
  const current = ++generation
  text.value = text.value.trim()
  busy.value = true; result.value = undefined; selections.value = []; applied.value = false
  try {
    const response = await draftRequest(text.value)
    if (current !== generation) return
    result.value = response
    selections.value = response.items.map(row => ({ line: row.line, materialId: row.materialId ?? undefined,
      quantity: row.quantity ?? undefined, requireDate: row.requireDate ?? undefined, ownerType: row.ownerType ?? undefined,
      ownerId: row.ownerId ?? undefined, ownerKey: row.ownerType === 1 ? 'public' : row.ownerId ? String(row.ownerId) : undefined, unitConfirmed: false }))
  } catch { /* API interceptor displays the failure. */ }
  finally { if (current === generation) busy.value = false }
}
async function apply() {
  if (!canApply.value || !result.value) return
  const current = generation
  const snapshot = JSON.stringify({ text: text.value, rows: selections.value })
  validating.value = true
  try {
    const response = await validateDraft(result.value.runId, selections.value.map(({ ownerKey: _key, ...row }) => row))
    if (current !== generation) return
    if (snapshot !== JSON.stringify({ text: text.value, rows: selections.value })) { ElMessage.warning('输入已变化，请重新确认'); return }
    emit('apply', response); applied.value = true
  } catch { /* API interceptor displays the failure. */ }
  finally { if (current === generation) validating.value = false }
}
</script>

<style scoped>
.draft-panel { background: var(--el-fill-color-light); padding: 18px; border-radius: 10px; margin-bottom: 20px; }
.examples { margin-bottom: 10px; }
.draft-panel > .el-button, .draft-panel > .el-alert { margin-top: 12px; }
.candidate { background: var(--el-bg-color); padding: 14px; margin-top: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.candidate-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.candidate-fields :deep(.el-select), .candidate-fields :deep(.el-input-number), .candidate-fields :deep(.el-date-editor) { width: 100%; }
.source { white-space: pre-wrap; overflow-wrap: anywhere; }
.issues { color: var(--el-color-warning-dark-2); line-height: 1.6; }
@media (max-width: 700px) { .candidate-fields { grid-template-columns: 1fr; } }
</style>

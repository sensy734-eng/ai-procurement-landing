<template>
  <div class="page-card assistant-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">助手对话</h2>
        <p class="text-sub">
          制度与教程问答、受控数据查询都在这里；只读，不改动任何业务单据
        </p>
      </div>
      <div class="toolbar-actions">
        <el-tag v-if="profile" size="small" effect="plain">{{ profile.scope.all ? '全部数据范围' : profile.scope.deptNames.join('、') || '受限范围' }}</el-tag>
        <el-button :icon="Plus" @click="startNew">新建对话</el-button>
      </div>
    </header>

    <el-alert v-if="profile" class="tip" type="info" :closable="false" show-icon
              :title="profile.scope.hint" />

    <div class="body">
      <!-- 历史会话 -->
      <aside class="side">
        <div class="side-title">历史会话</div>
        <el-empty v-if="!sessions.length" description="暂无会话" :image-size="60" />
        <div v-for="session in sessions" :key="session.id" class="session"
             :class="{ active: session.id === currentSession }" @click="openSession(session.id)">
          <div class="session-title">{{ session.title }}</div>
          <!-- 只留时间与条数：标题已经说明问了什么，再叠一个意图名是重复信息；
               而意图编码（kb_tutorial_qa 这类）是内部标识，不该出现在界面上。 -->
          <div class="session-meta">
            <span>{{ session.messageCount }} 条 · {{ shortTime(session.lastTime) }}</span>
          </div>
        </div>
      </aside>

      <!-- 对话区 -->
      <section class="chat">
        <div ref="scrollRef" class="stream">
          <div v-if="!items.length" class="empty">
            <p class="empty-title">可以这样问我：</p>
            <div class="samples">
              <el-button v-for="sample in samples" :key="sample.question" size="small"
                         @click="askSample(sample.question)">
                <el-tag size="small" effect="plain" :type="sample.route === 1 ? 'success' : sample.route === 2 ? 'warning' : 'primary'">
                  {{ routeLabel(sample.route) }}
                </el-tag>
                {{ sample.question }}
              </el-button>
            </div>
            <p class="muted">
              三类问题：制度类给条款原文与来源引用、教程类给有序步骤与功能入口、数据类给表格与转述。
              问句超出这三类、或要求修改数据时，助手会明确拒绝而不是猜测。
            </p>
          </div>

          <template v-for="item in items" :key="item.key">
            <div v-if="item.role === 'user'" class="bubble user">
              <div class="bubble-text">{{ item.content }}</div>
            </div>
            <div v-else class="answer-wrap">
              <AiAnswerCard :result="item.result!" :code="item.code || 'Success'" :question="item.question"
                            :retry-enabled="profile?.retryEnabled !== false"
                            @retry="openRetry" @pick="pickOption" @feedback="feedback" />
            </div>
          </template>

          <div v-if="sending" class="muted thinking">助手正在检索与查询…</div>
        </div>

        <div class="composer">
          <el-input v-model="input" type="textarea" :rows="2" resize="none" maxlength="300" show-word-limit
                    placeholder="例如：内六角螺栓还有多少库存 / 怎么新增采购申请 / 采购申请提交后多久内要审批完"
                    @keydown="onKeydown" />
          <div class="composer-actions">
            <span class="muted">Enter 发送 · Shift+Enter 换行</span>
            <el-button type="primary" :loading="sending" :disabled="!input.trim()" @click="send()">发送</el-button>
          </div>
        </div>
      </section>
    </div>

    <!-- M7-3-2 参数修正重跑 -->
    <el-dialog v-model="retryVisible" title="修正参数后重跑" width="560px">
      <el-alert type="info" :closable="false" show-icon class="retry-tip"
                title="只改本次执行的参数，不改白名单：改完仍按受控校验重新判定，被拒的条件不会因为手工修改而放行。" />
      <div class="retry-question">问句：{{ retryQuestion }}</div>
      <el-form label-width="110px">
        <el-form-item v-for="param in retryParams" :key="param.code" :label="param.name + '（' + param.code + '）'">
          <el-input v-model="param.value" :placeholder="'当前值 ' + param.original" />
        </el-form-item>
        <el-form-item label="新增参数">
          <div class="add-param">
            <el-input v-model="newParam.code" placeholder="参数编码，如 material_id" style="width: 200px" />
            <el-input v-model="newParam.value" placeholder="参数值" style="width: 160px" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retryVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="submitRetry">重跑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import AiAnswerCard from './AiAnswerCard.vue'
import { chatAi, getAiProfile, listAiMessages, listAiSessions, sendAiFeedback } from '@/api/ai'
import type { AiChatResult, AiIntentCard, AiMessage, AiOption, AiProfile, AiSession } from '@/api/ai'

interface ChatItem {
  key: string
  role: 'user' | 'assistant'
  content: string
  question: string
  code?: string
  result?: AiChatResult
}

interface RetryParam {
  code: string
  name: string
  value: string
  original: string
  dataType: string
}

const profile = ref<AiProfile | null>(null)
const sessions = ref<AiSession[]>([])
const currentSession = ref<number | null>(null)
const items = ref<ChatItem[]>([])
const input = ref('')
const sending = ref(false)
const scrollRef = ref<HTMLElement | null>(null)

const retryVisible = ref(false)
const retryQuestion = ref('')
const retryIntent = ref('')
const retryParams = ref<RetryParam[]>([])
const newParam = ref({ code: '', value: '' })

const samples = computed(() =>
  (profile.value?.samples || []).filter((sample, index, list) =>
    list.findIndex((item) => item.question === sample.question) === index)
)

let keySeed = 0
function nextKey(prefix: string) {
  keySeed += 1
  return prefix + '-' + keySeed
}

function routeLabel(route: number) {
  return route === 1 ? '制度' : route === 2 ? '教程' : '数据'
}

function shortTime(value: string) {
  return (value || '').replace('T', ' ').slice(5, 16)
}

async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}

async function loadProfile() {
  try {
    profile.value = await getAiProfile()
  } catch (error) {
    profile.value = null
  }
}

async function loadSessions() {
  try {
    sessions.value = await listAiSessions()
  } catch (error) {
    sessions.value = []
  }
}

function startNew() {
  currentSession.value = null
  items.value = []
  input.value = ''
}

async function openSession(sessionId: number) {
  currentSession.value = sessionId
  try {
    const messages = await listAiMessages(sessionId)
    items.value = toItems(messages)
    await scrollToBottom()
  } catch (error) {
    items.value = []
  }
}

/** 历史消息还原成对话流；结构化结果从 result_json 快照恢复 */
function toItems(messages: AiMessage[]): ChatItem[] {
  const list: ChatItem[] = []
  let lastQuestion = ''
  for (const message of messages) {
    if (message.role === 1) {
      lastQuestion = message.content
      list.push({ key: nextKey('u'), role: 'user', content: message.content, question: message.content })
      continue
    }
    const snapshot = parseSnapshot(message.resultJson)
    list.push({
      key: nextKey('a'),
      role: 'assistant',
      content: message.content,
      question: lastQuestion,
      code: message.resultCode || 'Success',
      result: {
        sessionId: currentSession.value || 0,
        intentCode: message.intentCode,
        intentName: message.intentName,
        answer: message.content,
        elapsedMs: message.elapsedMs,
        table: snapshot.table,
        refs: snapshot.refs,
        dualPath: snapshot.dualPath,
        card: snapshot.card,
        clarify: snapshot.clarify,
        refusalReason: snapshot.refusalReason,
        versionConflict: snapshot.versionConflict
      } as AiChatResult
    })
  }
  return list
}

function parseSnapshot(resultJson?: string | null): Record<string, never> {
  if (!resultJson) {
    return {}
  }
  try {
    return JSON.parse(resultJson)
  } catch (error) {
    return {}
  }
}

async function askSample(question: string) {
  input.value = question
  await send()
}

function onKeydown(event: Event | KeyboardEvent) {
  const keyboard = event as KeyboardEvent
  if (keyboard.key === 'Enter' && !keyboard.shiftKey) {
    keyboard.preventDefault()
    send()
  }
}

async function send(override?: { question?: string; intentCode?: string; params?: Record<string, unknown> }) {
  const question = (override?.question || input.value).trim()
  if (!question || sending.value) {
    return
  }
  items.value.push({ key: nextKey('u'), role: 'user', content: question, question })
  input.value = ''
  sending.value = true
  await scrollToBottom()
  try {
    const response = await chatAi({
      question,
      sessionId: currentSession.value,
      overrideIntentCode: override?.intentCode || null,
      overrideParams: override?.params || null
    })
    const data = response.data || ({} as AiChatResult)
    items.value.push({
      key: nextKey('a'), role: 'assistant', content: data.answer || '', question,
      code: response.code, result: data
    })
    if (data.sessionId) {
      currentSession.value = data.sessionId
    }
    await loadSessions()
  } catch (error) {
    ElMessage.error('提问失败，请稍后重试')
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

/** 澄清选项点击：意图歧义带 intentCode，实体歧义与参数缺失带 params */
async function pickOption(payload: { option: AiOption; clarify: string }) {
  const option = payload.option
  const params = (option.params || {}) as Record<string, unknown>
  await send({
    question: option.label,
    intentCode: option.intentCode || undefined,
    params: Object.keys(params).length ? params : undefined
  })
}

function openRetry(payload: { question: string; card: AiIntentCard }) {
  retryQuestion.value = payload.question
  retryIntent.value = payload.card.intentCode
  retryParams.value = (payload.card.filters || []).map((filter) => ({
    code: filter.code,
    name: filter.name,
    dataType: filter.dataType || 'string',
    value: filter.value === null || filter.value === undefined ? '' : String(filter.value),
    original: filter.value === null || filter.value === undefined ? '空' : String(filter.value)
  }))
  newParam.value = { code: '', value: '' }
  retryVisible.value = true
}

async function submitRetry() {
  const params: Record<string, unknown> = {}
  for (const param of retryParams.value) {
    params[param.code] = castValue(param.dataType, param.value)
  }
  if (newParam.value.code.trim()) {
    params[newParam.value.code.trim()] = castValue('string', newParam.value.value)
  }
  retryVisible.value = false
  await send({ question: retryQuestion.value, intentCode: retryIntent.value, params })
}

function castValue(dataType: string, value: string) {
  const text = (value ?? '').trim()
  if (dataType === 'long' || dataType === 'int') {
    return text === '' ? null : Number(text)
  }
  if (dataType === 'decimal') {
    return text === '' ? null : Number(text)
  }
  if (dataType === 'boolean') {
    return text === 'true' || text === '是'
  }
  return text
}

async function feedback(payload: { question: string }) {
  try {
    await sendAiFeedback({
      sessionId: currentSession.value || undefined,
      reason: '用户标记解析有误：' + payload.question
    })
    ElMessage.success('已记录为测试集候选，感谢反馈')
  } catch (error) {
    ElMessage.error('反馈提交失败')
  }
}

onMounted(async () => {
  await loadProfile()
  await loadSessions()
})
</script>

<style scoped>
.assistant-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 120px);
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tip {
  margin: 0;
}
.body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}
.side {
  width: 240px;
  flex-shrink: 0;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 8px;
  overflow-y: auto;
  background: #fafafa;
}
.side-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  padding: 4px 6px 8px;
}
.session {
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.session:hover {
  background: #eef2f7;
}
.session.active {
  background: #e6f0ff;
}
.session-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
}
.chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}
.stream {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fafbfc;
}
.empty {
  margin: auto;
  max-width: 640px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}
.samples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.bubble {
  display: flex;
  justify-content: flex-end;
}
.bubble-text {
  max-width: 70%;
  background: #409eff;
  color: #fff;
  padding: 8px 12px;
  border-radius: 8px 8px 0 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.answer-wrap {
  max-width: 88%;
}
.thinking {
  font-size: 12px;
}
.composer {
  border-top: 1px solid #e4e7ed;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
}
.composer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.retry-tip {
  margin-bottom: 10px;
}
.retry-question {
  font-size: 13px;
  color: #606266;
  margin-bottom: 10px;
}
.add-param {
  display: flex;
  gap: 8px;
}
</style>

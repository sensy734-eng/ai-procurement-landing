import request from './request'

/**
 * M7 / M8 / M9 智能助手接口封装。
 * 功能点编号用于关联接口清单、权限定义与回归场景。
 * 页面编号对应功能模块清单 P16 助手对话、P17 查询审计。
 *
 * 注意：chat 接口的七类结果码是业务语义而非错误，统一用 skipCodeCheck 拿完整返回体，
 * 由页面按 code 渲染提示样式、按 payload 渲染澄清与引用。
 */

// ---------------- 公共类型 ----------------

/** 结构化结果表格（M7-4-1） */
export interface AiColumn {
  key: string
  label: string
  type: string
}

export type AiRow = Record<string, unknown>

export interface AiTable {
  columns: AiColumn[]
  rows: AiRow[]
  total: number
  caption?: string | null
}

/** 归一后的实体（M9-3-1） */
export interface AiEntity {
  type: number
  typeName: string
  id: number
  no: string
  name: string
}

/** 意图参数（白名单校验后被接受/被拒/缺失三种状态） */
export interface AiParam {
  code: string
  name: string
  value?: unknown
  dataType?: string
  op?: string
  source?: string
  editable?: boolean
  enumValues?: string[] | null
  reason?: string | null
}

export interface AiTimeRange {
  label?: string | null
  from?: string | null
  to?: string | null
}

/** 意图与参数可视化卡片（M7-3-1） */
export interface AiIntentCard {
  intentCode: string
  intentName: string
  serviceCode?: string
  entities?: AiEntity[]
  timeRange?: AiTimeRange | null
  filters?: AiParam[]
  missing?: AiParam[]
  rejected?: AiParam[]
  editable?: boolean
}

/** 来源引用块（M7-4-2 / M8-3-1） */
export interface AiCitation {
  docId: number
  docName: string
  docVersion?: string
  effectiveDate?: string | null
  sectionNo?: string | null
  sectionTitle?: string | null
  chunkId: number
  snippet?: string
  score?: number | null
  path?: string | null
  valid?: boolean | null
  invalidReason?: string | null
}

/** 澄清选项（意图歧义 / 实体歧义 / 参数缺失共用） */
export interface AiOption {
  label: string
  detail?: string | null
  intentCode?: string | null
  params?: Record<string, unknown> | null
}

export interface AiClarify {
  type: string
  question: string
  options: AiOption[]
}

/** 双路召回对照项（M8-1-3） */
export interface AiDualPathItem {
  path: string
  rank: number
  chunkId: number
  docId: number
  docName: string
  sectionNo?: string | null
  score: number
  snippet?: string
}

/** 重跑对比（M7-3-4） */
export interface AiCompare {
  previousResultCode?: string | null
  previousSummary?: string | null
  previousRowCount?: number | null
  changedParams?: string[]
}

/** 版本冲突提示（M8-4-2） */
export interface AiVersionConflict {
  docName?: string
  versions?: Array<{ version: string; effectiveDate?: string | null; active?: boolean }>
  message?: string
}

/** 问答返回（ChatVO） */
export interface AiChatResult {
  sessionId: number
  questionMessageId?: number
  answerMessageId?: number
  route?: number | null
  routeName?: string | null
  intentCode?: string | null
  intentName?: string | null
  confidence?: number | null
  card?: AiIntentCard | null
  entities?: AiEntity[]
  answer?: string | null
  table?: AiTable | null
  refs?: AiCitation[]
  clarify?: AiClarify | null
  refusalReason?: string | null
  answerMode?: 'generated' | 'references_only' | 'refused' | null
  degradedReason?: string | null
  retrievalConfigId?: string | null
  evidenceChunkIds?: number[]
  versionConflict?: AiVersionConflict | null
  dualPath?: AiDualPathItem[]
  resultCodeHint?: string | null
  elapsedMs?: number
  compare?: AiCompare | null
  notices?: string[]
  resultCode?: string
  missing?: AiParam[]
}

/** chat 返回体外层（含结果码，供页面判定提示样式） */
export interface AiChatResponse {
  code: string
  message: string
  data: AiChatResult
}

export interface AiChatRequest {
  question: string
  sessionId?: number | null
  overrideRoute?: number | null
  overrideIntentCode?: string | null
  overrideParams?: Record<string, unknown> | null
  confirmedEntityId?: number | null
}

export interface AiSession {
  id: number
  title: string
  messageCount: number
  lastIntentCode?: string | null
  lastIntentName?: string | null
  lastTime: string
  createdAt: string
}

export interface AiMessage {
  id: number
  role: number
  content: string
  msgType: number
  intentCode?: string | null
  intentName?: string | null
  paramsJson?: string | null
  resultJson?: string | null
  refDocIds?: string | null
  resultCode?: string | null
  elapsedMs: number
  createdAt: string
}

/** 入口提示（M7-1-4） */
export interface AiProfile {
  realName: string
  roleNames: string[]
  knowledgeEnabled: boolean
  dataEnabled: boolean
  retryEnabled: boolean
  scope: {
    all: boolean
    deptNames: string[]
    warehouseNames: string[]
    hint: string
  }
  samples: Array<{ route: number; question: string }>
}

/** 审计统计（M8-3-4 / M8-5-3 / M9-5-5） */
export interface AiAuditStats {
  total: number
  byResultCode: Record<string, number>
  byIntent: Record<string, number>
  refusalCount: number
  refusalRate: number
  noPermissionCount: number
  invalidParamCount: number
  effectiveAnswers: number
  citedAnswers: number
  citationCoverage: number
}

/** 引用有效性校验结果（M8-3-3 / M10-5-3） */
export interface CitationCheck {
  valid: boolean
  reason: string
  docName?: string | null
  docVersion?: string | null
}

// ---------------- M7-1-1 提问与 M7-3-2 参数修正重跑 ----------------

export function chatAi(payload: AiChatRequest) {
  return request.post<never, AiChatResponse>('/assistant/chat', payload, {
    skipCodeCheck: true
  } as never)
}

/** M7-1-4 入口提示 */
export function getAiProfile() {
  return request.get<never, AiProfile>('/assistant/profile')
}

// ---------------- M7-1-2 对话历史 ----------------

export function listAiSessions() {
  return request.get<never, AiSession[]>('/assistant/sessions')
}

export function listAiMessages(sessionId: number) {
  return request.get<never, AiMessage[]>(`/assistant/sessions/${sessionId}/messages`)
}

/** M7-3-3 解析错误反馈 */
export function sendAiFeedback(body: { sessionId?: number; messageId?: number; reason: string }) {
  return request.post<never, void>('/assistant/feedback', body)
}

// ---------------- M8-3-3 引用有效性校验 / M8-3-4 覆盖率 ----------------

export function verifyCitation(docId: number, chunkId: number) {
  return request.post<never, CitationCheck>('/assistant/citations/verify', { docId, chunkId })
}

export function getCitationCoverage() {
  return request.get<never, Record<string, unknown>>('/assistant/citations/coverage')
}

// ---------------- M9-5-5 审计统计 ----------------

export function getAiAuditStats() {
  return request.get<never, AiAuditStats>('/assistant/audits/stats')
}

/** 结果码中文名（页面提示与审计列表共用） */
export const resultCodeText: Record<string, string> = {
  Success: '查询成功',
  NotFound: '单据不存在',
  NoData: '无匹配数据',
  Ambiguous: '存在歧义待澄清',
  NoPermission: '越权被拒',
  Unsupported: '超出能力范围',
  InvalidParameter: '参数不在白名单'
}

/** 结果码对应的 Element Plus 标签色 */
export const resultCodeTag: Record<string, string> = {
  Success: 'success',
  NotFound: 'info',
  NoData: 'info',
  Ambiguous: 'warning',
  NoPermission: 'danger',
  Unsupported: 'danger',
  InvalidParameter: 'warning'
}

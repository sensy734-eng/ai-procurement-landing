import request from './request'

export interface TaskEvidence { id: number; kind: string; title: string; text: string; path?: string }
export interface OrderReview {
  runId: string; orderId: number; orderNo: string; asOf: string; snapshotId: string
  mode: 'generated' | 'facts_only' | 'stale'; degradedReason?: string; model?: string
  totalTokens?: number; elapsedMs: number; promptVersion: string
  checks: { code: string; status: string; title: string; detail: string; evidenceIds: number[] }[]
  observations: { text: string; evidenceIds: number[] }[]
  evidence: TaskEvidence[]; missingEvidence: string[]; stages: string[]
}
export interface DraftMaterial { id: number; no: string; name: string; spec?: string; unit: string }
export interface DraftOwner { id: number; type: number; no: string; name: string }
export interface DraftLine {
  line: number; source: string; materialText: string; quantity?: number; unit: string
  requireDate?: string; ownerText: string; materialId?: number; ownerType?: number; ownerId?: number
  materialCandidates: DraftMaterial[]; ownerCandidates: DraftOwner[]; issues: string[]
}
export interface RequestDraft {
  runId: string; text: string; today: string; mode: 'generated' | 'rule_template' | 'manual_required'
  degradedReason?: string; model?: string; totalTokens?: number; elapsedMs: number
  items: DraftLine[]; materials: DraftMaterial[]; owners: DraftOwner[]; notices: string[]
}
export interface DraftSelection {
  line: number; materialId?: number; quantity?: number; requireDate?: string
  ownerType?: number; ownerId?: number; unitConfirmed: boolean
}
export interface ValidatedDraft {
  runId: string
  items: { materialId: number; materialName: string; unit: string; quantity: number; requireDate: string; ownerType: number; ownerId?: number }[]
}
export const reviewOrder = (id: number, question: string, focus: string) =>
  request.post<never, OrderReview>(`/assistant/tasks/orders/${id}/review`, { question, focus }, { timeout: 20000 })
export const draftRequest = (text: string) => request.post<never, RequestDraft>('/assistant/tasks/request-draft', { text }, { timeout: 20000 })
export const validateDraft = (runId: string, items: DraftSelection[]) =>
  request.post<never, ValidatedDraft>('/assistant/tasks/request-draft/validate', { runId, items })

export function taskReason(reason?: string) {
  const reasons: Record<string, string> = {
    MODEL_UNAVAILABLE: '模型未配置或已关闭', TIMEOUT: '模型响应超时', HTTP_429: '模型请求受限', RATE_LIMIT: '模型请求受限',
    NETWORK: '模型连接失败', OUTPUT_VALIDATION_FAILED: '模型输出未通过核验',
    EVIDENCE_CHANGED: '生成期间数据或制度已变化', EMPTY: '模型未返回有效内容'
  }
  return reason ? reasons[reason] || '模型服务暂不可用' : ''
}

import request from './request'

/**
 * M10 知识库管理接口封装。
 * 每个函数标注对应功能点编号，便于论文第 5 章按编号引用。
 * 页面编号对应功能模块清单 P18 文档管理、P19 切块查看、P20 检索测试。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

// ---------------- M10-4-1 文档列表 ----------------

/** 文档行（列表/详情共用） */
export interface KbDocumentRow {
  id: number
  docName: string
  docType: number
  docTypeName: string
  version: string
  effectiveDate: string
  expireDate: string | null
  status: number
  statusName: string
  fileType: string | null
  fileSize: number | null
  hasFile: boolean
  parseStatus: number
  parseStatusName: string
  chunkCount: number
  tags: string | null
  remark: string | null
  uploadAt: string
}

// ---------------- M10-4-4 切块 ----------------

/** 切块行 */
export interface KbChunkRow {
  id: number
  chunkIndex: number
  sectionNo: string | null
  sectionTitle: string | null
  charCount: number
  chunkText: string
  hasVector: boolean
}

// ---------------- M10-3 索引 ----------------

/** 索引构建统计（单文档/全量共用） */
export interface IndexBuildStat {
  docCount: number
  chunkCount: number
  tokenCount: number
  elapsedMs: number
}

// ---------------- M10-5-1/5-2 检索测试 ----------------

/** 单条命中 */
export interface RetrievalHit {
  rank: number
  chunkId: number
  docId: number
  docName: string
  sectionNo: string | null
  sectionTitle: string | null
  score: number
  snippet: string
}

/** 一次检索测试结果（双路对照） */
export interface RetrievalTestResult {
  id: number
  question: string
  expectDocId: number | null
  expectDocName: string | null
  expectChunkId: number | null
  topK: number
  bm25: RetrievalHit[]
  vector: RetrievalHit[]
  hitFlag: number
  hitRank: number | null
  hitRoute: number | null
  hitRouteName: string | null
  testBatch: string
  createdAt: string
}

/** 命中率统计 */
export interface TestStat {
  total: number
  hitCount: number
  hitRate: number
  bm25Hit: number
  vectorHit: number
  bothHit: number
  bm25Rate: number
  vectorRate: number
  avgHitRank: number | null
}

// ---------------- 接口 ----------------

/** M10-4-1 文档分页 */
export function listDocuments(params: {
  docName?: string
  docType?: number
  status?: number
  parseStatus?: number
  pageNo?: number
  pageSize?: number
}) {
  return request.get<never, PageResult<KbDocumentRow>>('/m10/documents', { params })
}

/** M10-4-1 文档详情 */
export function getDocument(id: number) {
  return request.get<never, KbDocumentRow>(`/m10/documents/${id}`)
}

/** M10-1-1 上传文档（自动解析建索引）；升版时 file 可缺省（复制来源版本切块） */
export function uploadDocument(form: FormData) {
  return request.post<never, number>('/m10/documents', form)
}

/** M10-4-2 元数据维护 */
export function updateDocument(id: number, body: {
  docName: string
  docType: number
  tags?: string
  version?: string
  effectiveDate?: string
  expireDate?: string
  remark?: string
}) {
  return request.put<never, void>(`/m10/documents/${id}`, body)
}

/** M10-1-2 重新解析（md/docx 走文件；pdf 抛 Unsupported） */
export function reparseDocument(id: number) {
  return request.post<never, void>(`/m10/documents/${id}/parse`)
}

/** M10-1-3 正文补录（pdf 粘贴文本入口） */
export function saveDocumentText(id: number, text: string) {
  return request.put<never, void>(`/m10/documents/${id}/text`, { text })
}

/** M10-4-2 创建新版本（同文档名并存；带文件则解析新文件，否则复制来源切块） */
export function createDocumentVersion(id: number, form: FormData) {
  return request.post<never, number>(`/m10/documents/${id}/versions`, form)
}

/** M10-4-3 启用/停用 */
export function toggleDocument(id: number) {
  return request.post<never, void>(`/m10/documents/${id}/toggle`)
}

/** M10-4-5 删除文档（物理删，同步清索引） */
export function deleteDocument(id: number) {
  return request.delete<never, void>(`/m10/documents/${id}`)
}

/** M10-4-4 切块列表 */
export function listChunks(docId: number) {
  return request.get<never, KbChunkRow[]>(`/m10/documents/${docId}/chunks`)
}

/** M10-2-3 相邻切块合并（合并后重建该文档索引） */
export function mergeChunks(docId: number, chunkIds: number[]) {
  return request.post<never, IndexBuildStat>(`/m10/documents/${docId}/chunks/merge`, { chunkIds })
}

/** M10-3-3 单文档索引重建 */
export function rebuildDocIndex(docId: number) {
  return request.post<never, IndexBuildStat>(`/m10/documents/${docId}/index/rebuild`)
}

/** M10-3-4 全量索引一键重建 */
export function rebuildAllIndex() {
  return request.post<never, IndexBuildStat>('/m10/index/rebuild')
}

/** M10-5-1 跑一次检索测试（双路 Top-K 对照） */
export function runRetrievalTest(body: {
  question: string
  expectDocId?: number
  expectChunkId?: number
  topK?: number
  testBatch?: string
}) {
  return request.post<never, RetrievalTestResult>('/m10/retrieval-tests', body)
}

/** M10-5-2 命中率统计 */
export function getTestStat(params: { testBatch?: string; from?: string; to?: string }) {
  return request.get<never, TestStat>('/m10/retrieval-tests/stats', { params })
}

<template>
  <div class="kb-panel">
    <header class="toolbar">
      <div class="filters">
        <el-input v-model="query.docName" placeholder="文档名称" clearable style="width: 200px" @keyup.enter="load" />
        <el-select v-model="query.docType" placeholder="文档分类" clearable style="width: 140px">
          <el-option v-for="item in docTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="2" />
        </el-select>
        <el-select v-model="query.parseStatus" placeholder="解析状态" clearable style="width: 130px">
          <el-option label="待解析" :value="1" />
          <el-option label="解析中" :value="2" />
          <el-option label="已解析" :value="3" />
          <el-option label="待补录" :value="4" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m10:index:rebuild')" :icon="RefreshRight" @click="rebuildAll">
          全量重建索引
        </el-button>
        <el-button v-if="store.hasPerm('m10:document:upload')" type="primary" :icon="Upload" @click="uploadVisible = true">
          上传文档
        </el-button>
      </div>
    </header>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="docName" label="文档名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="docTypeName" label="分类" width="90" align="center" />
      <el-table-column prop="version" label="版本" width="70" align="center" />
      <el-table-column prop="effectiveDate" label="生效日期" width="120" align="center" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="解析状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="parseTagType(row.parseStatus)" size="small">{{ row.parseStatusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="切块数" width="80" align="center" />
      <el-table-column label="文件" width="130" align="center">
        <template #default="{ row }">
          <span v-if="row.hasFile">{{ row.fileType }} · {{ formatSize(row.fileSize) }}</span>
          <span v-else class="text-sub">正文补录</span>
        </template>
      </el-table-column>
      <el-table-column prop="uploadAt" label="上传时间" width="165">
        <template #default="{ row }">{{ formatTime(row.uploadAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="store.hasPerm('m10:document:parse')" link type="primary" @click="reparse(row)">重新解析</el-button>
          <el-button v-if="store.hasPerm('m10:index:rebuild')" link type="primary" @click="rebuildDoc(row)">重建索引</el-button>
          <el-button v-if="store.hasPerm('m10:document:toggle')" link :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="store.hasPerm('m10:document:delete')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="load"
    />

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传文档" width="560px">
      <el-alert type="info" :closable="false" show-icon class="tip"
        title="支持 md / docx / pdf，单文件不超过 10MB。上传后自动解析切块并建立索引；pdf 暂不支持自动解析，需上传后在详情里做正文补录。" />
      <el-form label-width="88px" class="upload-form">
        <el-form-item label="文件" required>
          <input ref="fileInput" type="file" accept=".md,.docx,.pdf" @change="onFileChange" />
          <span v-if="pendingFile" class="text-sub">{{ pendingFile.name }}（{{ formatSize(pendingFile.size) }}）</span>
        </el-form-item>
        <el-form-item label="文档名称" required>
          <el-input v-model="uploadForm.docName" placeholder="与正文标题保持一致" />
        </el-form-item>
        <el-form-item label="文档分类" required>
          <el-select v-model="uploadForm.docType" style="width: 100%">
            <el-option v-for="item in docTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="uploadForm.version" placeholder="默认 1.0" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="uploadForm.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="uploadForm.tags" placeholder="逗号分隔，如：制度,采购,审批" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detail.docName || '文档详情'" size="640px">
      <template v-if="detailVisible">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="分类">{{ detail.docTypeName }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ detail.effectiveDate }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.status === 1 ? 'success' : 'info'" size="small">{{ detail.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="解析状态">
            <el-tag :type="parseTagType(detail.parseStatus)" size="small">{{ detail.parseStatusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="切块数">{{ detail.chunkCount }}</el-descriptions-item>
          <el-descriptions-item label="文件">
            <span v-if="detail.hasFile">{{ detail.fileType }} · {{ formatSize(detail.fileSize) }}</span>
            <span v-else class="text-sub">正文补录</span>
          </el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatTime(detail.uploadAt) }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">{{ detail.tags || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-actions">
          <el-button v-if="store.hasPerm('m10:document:list') && detail.hasFile" :icon="Download" @click="download(detail.id)">
            下载原件
          </el-button>
          <el-button v-if="store.hasPerm('m10:document:parse')" :icon="Refresh" @click="reparse(detail)">
            重新解析
          </el-button>
          <el-button v-if="store.hasPerm('m10:document:parse') && detail.parseStatus === 4" type="warning" :icon="EditPen" @click="openText">
            正文补录
          </el-button>
          <el-button v-if="store.hasPerm('m10:document:upload')" :icon="Plus" @click="openVersionDialog">创建新版本</el-button>
          <el-button v-if="store.hasPerm('m10:document:update')" type="primary" :icon="Edit" @click="openEdit">编辑元数据</el-button>
        </div>

        <!-- 元数据编辑 -->
        <el-dialog v-model="editVisible" title="编辑元数据" width="480px" append-to-body>
          <el-form label-width="88px">
            <el-form-item label="文档名称"><el-input v-model="editForm.docName" /></el-form-item>
            <el-form-item label="文档分类">
              <el-select v-model="editForm.docType" style="width: 100%">
                <el-option v-for="item in docTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="版本"><el-input v-model="editForm.version" /></el-form-item>
            <el-form-item label="生效日期">
              <el-date-picker v-model="editForm.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="失效日期">
              <el-date-picker v-model="editForm.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="标签"><el-input v-model="editForm.tags" /></el-form-item>
            <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="editVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
          </template>
        </el-dialog>

        <!-- 正文补录 -->
        <el-dialog v-model="textVisible" title="正文补录" width="640px" append-to-body>
          <el-alert type="info" :closable="false" show-icon class="tip"
            title="pdf 暂不支持自动解析，请粘贴正文。补录后按条款边界重新切块并重建索引。" />
          <el-input v-model="patchText" type="textarea" :rows="12" placeholder="粘贴制度正文" />
          <template #footer>
            <el-button @click="textVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submitText">保存并重新解析</el-button>
          </template>
        </el-dialog>

        <!-- 创建新版本 -->
        <el-dialog v-model="versionVisible" title="创建新版本" width="480px" append-to-body>
          <el-alert type="info" :closable="false" show-icon class="tip"
            title="同文档名新建一行。带文件则解析新文件；不带文件则复制来源版本的切块（适合仅调整元数据的场景）。" />
          <el-form label-width="88px">
            <el-form-item label="新版本号"><el-input v-model="versionForm.version" placeholder="如 2.0" /></el-form-item>
            <el-form-item label="生效日期">
              <el-date-picker v-model="versionForm.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="文件">
              <input ref="versionFileInput" type="file" accept=".md,.docx,.pdf" @change="onVersionFileChange" />
              <span class="text-sub">（可选）不选则复制来源版本切块</span>
            </el-form-item>
            <el-form-item label="备注"><el-input v-model="versionForm.remark" type="textarea" :rows="2" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="versionVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submitVersion">创建</el-button>
          </template>
        </el-dialog>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Edit, EditPen, Plus, Refresh, RefreshRight, Search, Upload } from '@element-plus/icons-vue'
import {
  createDocumentVersion,
  deleteDocument,
  getDocument,
  listDocuments,
  rebuildAllIndex,
  rebuildDocIndex,
  reparseDocument,
  saveDocumentText,
  toggleDocument,
  updateDocument,
  uploadDocument,
  type KbDocumentRow
} from '@/api/m10'
import { downloadCsv } from '@/api/request'
import { useUserStore } from '@/stores/user'

const store = useUserStore()

const docTypeOptions = [
  { value: 1, label: '制度' },
  { value: 2, label: '合同' },
  { value: 3, label: '手册' },
  { value: 4, label: '规范' },
  { value: 5, label: '其他' }
]

const rows = ref<KbDocumentRow[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  docName: '',
  docType: undefined as number | undefined,
  status: undefined as number | undefined,
  parseStatus: undefined as number | undefined,
  pageNo: 1,
  pageSize: 10
})

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function formatSize(size?: number | null) {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}

function parseTagType(parseStatus: number) {
  if (parseStatus === 3) return 'success'
  if (parseStatus === 4) return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      pageNo: query.pageNo,
      pageSize: query.pageSize
    }
    if (query.docName) params.docName = query.docName
    if (query.docType) params.docType = query.docType
    if (query.status) params.status = query.status
    if (query.parseStatus) params.parseStatus = query.parseStatus
    const page = await listDocuments(params)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function reset() {
  query.docName = ''
  query.docType = undefined
  query.status = undefined
  query.parseStatus = undefined
  query.pageNo = 1
  load()
}

// ---------------- 上传 ----------------

const uploadVisible = ref(false)
const uploading = ref(false)
const pendingFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement>()
const uploadForm = reactive({
  docName: '',
  docType: 1,
  version: '',
  effectiveDate: '',
  tags: '',
  remark: ''
})

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) return
  if (!/\.(md|docx|pdf)$/i.test(file.name)) {
    ElMessage.error('仅支持 md / docx / pdf 文件')
    input.value = ''
    pendingFile.value = null
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('单文件不能超过 10MB')
    input.value = ''
    pendingFile.value = null
    return
  }
  pendingFile.value = file
}

async function submitUpload() {
  if (!pendingFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!uploadForm.docName.trim()) {
    ElMessage.warning('请填写文档名称')
    return
  }
  const form = new FormData()
  form.append('file', pendingFile.value)
  form.append('docName', uploadForm.docName.trim())
  form.append('docType', String(uploadForm.docType))
  if (uploadForm.version) form.append('version', uploadForm.version)
  if (uploadForm.effectiveDate) form.append('effectiveDate', uploadForm.effectiveDate)
  if (uploadForm.tags) form.append('tags', uploadForm.tags)
  if (uploadForm.remark) form.append('remark', uploadForm.remark)
  uploading.value = true
  try {
    await uploadDocument(form)
    ElMessage.success('上传成功，已自动解析建索引')
    uploadVisible.value = false
    if (fileInput.value) fileInput.value.value = ''
    pendingFile.value = null
    load()
  } finally {
    uploading.value = false
  }
}

// ---------------- 详情与行内动作 ----------------

const detailVisible = ref(false)
const detail = ref<KbDocumentRow>({} as KbDocumentRow)

async function openDetail(row: KbDocumentRow) {
  detail.value = await getDocument(row.id)
  detailVisible.value = true
}

async function reparse(row: KbDocumentRow) {
  await ElMessageBox.confirm('重新解析将按当前正文重新切块并重建索引，确认继续？', '重新解析', { type: 'warning' })
  await reparseDocument(row.id)
  ElMessage.success('已重新解析')
  load()
  if (detailVisible.value && detail.value.id === row.id) {
    detail.value = await getDocument(row.id)
  }
}

async function rebuildDoc(row: KbDocumentRow) {
  const stat = await rebuildDocIndex(row.id)
  ElMessage.success(`索引已重建：${stat.chunkCount} 个切块、${stat.tokenCount} 个词条，耗时 ${stat.elapsedMs}ms`)
}

async function rebuildAll() {
  const stat = await rebuildAllIndex()
  ElMessage.success(`全量重建完成：${stat.docCount} 篇文档、${stat.chunkCount} 个切块，耗时 ${stat.elapsedMs}ms`)
  load()
}

async function toggle(row: KbDocumentRow) {
  await toggleDocument(row.id)
  ElMessage.success(row.status === 1 ? '已停用，不再参与召回' : '已启用，恢复参与召回')
  load()
}

async function remove(row: KbDocumentRow) {
  await ElMessageBox.confirm(`删除「${row.docName}」将同步清除切块与索引，且不可恢复，确认删除？`, '删除文档', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('已删除')
  load()
}

function download(id: number) {
  downloadCsv(`/api/m10/documents/${id}/download`, `kb-doc-${id}`)
}

// ---------------- 元数据编辑 ----------------

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({
  docName: '',
  docType: 1,
  version: '',
  effectiveDate: '',
  expireDate: '',
  tags: '',
  remark: ''
})

function openEdit() {
  editForm.docName = detail.value.docName
  editForm.docType = detail.value.docType
  editForm.version = detail.value.version
  editForm.effectiveDate = detail.value.effectiveDate
  editForm.expireDate = detail.value.expireDate ?? ''
  editForm.tags = detail.value.tags ?? ''
  editForm.remark = detail.value.remark ?? ''
  editVisible.value = true
}

async function submitEdit() {
  if (!editForm.docName.trim()) {
    ElMessage.warning('请填写文档名称')
    return
  }
  saving.value = true
  try {
    await updateDocument(detail.value.id, {
      docName: editForm.docName.trim(),
      docType: editForm.docType,
      tags: editForm.tags,
      version: editForm.version,
      effectiveDate: editForm.effectiveDate,
      expireDate: editForm.expireDate || undefined,
      remark: editForm.remark
    })
    ElMessage.success('已保存')
    editVisible.value = false
    detail.value = await getDocument(detail.value.id)
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 正文补录 ----------------

const textVisible = ref(false)
const patchText = ref('')

function openText() {
  patchText.value = ''
  textVisible.value = true
}

async function submitText() {
  if (!patchText.value.trim()) {
    ElMessage.warning('请粘贴正文')
    return
  }
  saving.value = true
  try {
    await saveDocumentText(detail.value.id, patchText.value.trim())
    ElMessage.success('已保存并重新切块')
    textVisible.value = false
    detail.value = await getDocument(detail.value.id)
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 创建新版本 ----------------

const versionVisible = ref(false)
const versionFileInput = ref<HTMLInputElement>()
const versionFile = ref<File | null>(null)
const versionForm = reactive({ version: '', effectiveDate: '', remark: '' })

function openVersionDialog() {
  versionForm.version = ''
  versionForm.effectiveDate = ''
  versionForm.remark = ''
  versionFile.value = null
  versionVisible.value = true
}

function onVersionFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  versionFile.value = input.files?.[0] ?? null
}

async function submitVersion() {
  if (!versionForm.version.trim()) {
    ElMessage.warning('请填写新版本号')
    return
  }
  const form = new FormData()
  form.append('version', versionForm.version.trim())
  if (versionForm.effectiveDate) form.append('effectiveDate', versionForm.effectiveDate)
  if (versionForm.remark) form.append('remark', versionForm.remark)
  if (versionFile.value) form.append('file', versionFile.value)
  saving.value = true
  try {
    const newId = await createDocumentVersion(detail.value.id, form)
    ElMessage.success(`新版本已创建（id=${newId}）`)
    versionVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
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
.pager {
  align-self: flex-end;
}
.tip {
  margin-bottom: 12px;
}
.upload-form {
  margin-top: 12px;
}
.drawer-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 16px 0;
}
</style>

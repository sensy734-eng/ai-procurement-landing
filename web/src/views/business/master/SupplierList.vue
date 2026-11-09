<template>
  <div class="page-card supplier-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">供应商档案<span class="page-code">M1</span></h2>
        <p class="text-sub">档案、联系人、供货范围与资质附件</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m1:supplier:create')" type="primary" :icon="Plus" @click="openCreate">
          新增供应商
        </el-button>
      </div>
    </header>

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="编码或名称"
          clearable
          style="width: 200px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.supplierType" placeholder="全部类型" clearable style="width: 140px">
          <el-option v-for="item in supplierTypes" :key="item.id" :label="item.itemName" :value="Number(item.itemCode)" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="reload(1)">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      class="scope-alert"
      type="info"
      :closable="false"
      show-icon
      title="供应商档案为共享主数据；停用后不可用于新订单，存量单据不受影响。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="supplierNo" label="供应商编码" width="150" fixed="left" />
      <el-table-column prop="supplierName" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="supplierTypeName" label="类型" width="100" />
      <el-table-column prop="taxNo" label="统一社会信用代码" width="190" show-overflow-tooltip />
      <el-table-column prop="defaultContactName" label="主联系人" width="110" />
      <el-table-column label="供货范围" width="90" align="center">
        <template #default="{ row }">
          <span>{{ row.scopeCount }} 项</span>
        </template>
      </el-table-column>
      <el-table-column label="资质附件" width="90" align="center">
        <template #default="{ row }">
          <span>{{ row.attachmentCount }} 个</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m1:supplier:update')" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button
            v-if="store.hasPerm('m1:supplier:toggle')"
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggle(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <footer class="pager">
      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="reload"
        @size-change="reload(1)"
      />
    </footer>

    <!-- 新增 / 编辑供应商，四个页签 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑供应商' : '新增供应商'" width="760px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="base">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
            <el-form-item label="供应商编码" prop="supplierNo">
              <el-input v-model="form.supplierNo" :disabled="!!form.id" placeholder="全系统唯一，创建后不可改" />
            </el-form-item>
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="form.supplierName" />
            </el-form-item>
            <el-form-item label="供应商类型" prop="supplierType">
              <el-select v-model="form.supplierType" placeholder="选择类型" style="width: 100%">
                <el-option v-for="item in supplierTypes" :key="item.id" :label="item.itemName" :value="Number(item.itemCode)" />
              </el-select>
            </el-form-item>
            <el-form-item label="统一社会信用代码" prop="taxNo">
              <el-input v-model="form.taxNo" placeholder="15 至 20 位数字或大写字母" />
            </el-form-item>
            <el-form-item label="注册地址">
              <el-input v-model="form.address" />
            </el-form-item>
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" />
            </el-form-item>
            <el-form-item label="电子邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
            <el-form-item label="开户银行">
              <el-input v-model="form.bankName" />
            </el-form-item>
            <el-form-item label="银行账号">
              <el-input v-model="form.bankAccount" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <div class="dialog-footer">
            <el-button @click="formVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submitForm">保存基本信息</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="联系人" name="contacts">
          <p class="text-sub">一对多联系人整体保存，主联系人同一供应商至多一个。</p>
          <el-table :data="contacts" border size="small">
            <el-table-column label="主联系人" width="90" align="center">
              <template #default="{ row }">
                <el-radio v-model="defaultContactIndex" :label="row.__index" />
              </template>
            </el-table-column>
            <el-table-column label="姓名" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.contactName" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="职务" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.position" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="电话" min-width="130">
              <template #default="{ row }">
                <el-input v-model="row.phone" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="邮箱" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.email" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" :icon="Delete" @click="contacts.splice($index, 1)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="dialog-footer">
            <el-button link type="primary" :icon="Plus" @click="contacts.push({ contactName: '', position: '', phone: '', email: '', isDefault: 0, status: 1, remark: '', __index: contacts.length })">
              添加联系人
            </el-button>
            <el-button type="primary" :loading="saving" :disabled="!form.id" @click="submitContacts">保存联系人</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="供货范围" name="scopes">
          <p class="text-sub">按物料或物料分类授权供货范围，订单选择物料时据此提示，不做强制拦截。</p>
          <el-table :data="scopes" border size="small">
            <el-table-column label="范围类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.scopeType" size="small" @change="row.scopeId = 0">
                  <el-option label="按物料" :value="1" />
                  <el-option label="按物料分类" :value="2" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="范围对象" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.scopeId" size="small" filterable style="width: 100%">
                  <el-option
                    v-for="item in row.scopeType === 1 ? materialSelectOptions : categoryOptions"
                    :key="item.id"
                    :label="item.label"
                    :value="item.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="140">
              <template #default="{ row }">
                <el-input v-model="row.remark" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" :icon="Delete" @click="scopes.splice($index, 1)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="dialog-footer">
            <el-button link type="primary" :icon="Plus" @click="scopes.push({ scopeType: 1, scopeId: 0, scopeName: '', remark: '' })">
              添加范围
            </el-button>
            <el-button type="primary" :loading="saving" :disabled="!form.id" @click="submitScopes">保存供货范围</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="资质附件" name="attachments">
          <p class="text-sub">仅支持 pdf、jpg、jpeg、png、docx，单个不超过 10MB；删除记录不删文件，保留审计依据。</p>
          <div class="upload-bar">
            <input ref="fileInput" type="file" accept=".pdf,.jpg,.jpeg,.png,.docx" hidden @change="onFilePicked" />
            <el-button :icon="Upload" :disabled="!form.id" @click="fileInput?.click()">选择文件</el-button>
            <el-date-picker
              v-model="attachmentExpire"
              type="date"
              placeholder="资质有效期（可选）"
              value-format="YYYY-MM-DD"
              style="width: 200px"
            />
            <el-button type="primary" :loading="uploading" :disabled="!attachmentFile" @click="submitAttachment">
              上传
            </el-button>
          </div>
          <el-table v-loading="attachmentsLoading" :data="attachments" border size="small">
            <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="80" align="center" />
            <el-table-column label="大小" width="100" align="right">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="有效期" width="130" align="center">
              <template #default="{ row }">{{ row.expireDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="downloadAttachment(row)">下载</el-button>
                <el-button
                  v-if="store.hasPerm('m1:supplier_attachment:delete')"
                  link
                  type="danger"
                  @click="removeAttachment(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Download, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  attachmentDownloadUrl,
  categoryTree,
  createSupplier,
  deleteSupplierAttachment,
  dictItems,
  materialOptions,
  pageSuppliers,
  saveSupplierContacts,
  saveSupplierScopes,
  supplierAttachments,
  supplierContacts,
  supplierScopes,
  toggleSupplierStatus,
  updateSupplier,
  uploadSupplierAttachment,
  type AttachmentRow,
  type CategoryNode,
  type ContactRow,
  type DictItemRow,
  type MaterialRow,
  type ScopeRow,
  type SupplierRow
} from '@/api/m1'
import { downloadCsv } from '@/api/m11'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SupplierRow[]>([])
const total = ref(0)
const supplierTypes = ref<DictItemRow[]>([])

const query = reactive<{
  pageNo: number
  pageSize: number
  keyword: string
  supplierType?: number
  status?: number
}>({ pageNo: 1, pageSize: 10, keyword: '' })

async function reload(page = query.pageNo) {
  loading.value = true
  try {
    const result = await pageSuppliers({
      pageNo: page,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
      supplierType: query.supplierType,
      status: query.status
    })
    rows.value = result.records
    total.value = result.total
    query.pageNo = result.pageNo
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.supplierType = undefined
  query.status = undefined
  reload(1)
}

// ---------------- 基本信息 ----------------

const formVisible = ref(false)
const activeTab = ref('base')
const formRef = ref<FormInstance>()
const form = reactive<{
  id?: number
  supplierNo: string
  supplierName: string
  supplierType?: number
  taxNo: string
  address: string
  phone: string
  email: string
  bankName: string
  bankAccount: string
  remark: string
}>({
  supplierNo: '',
  supplierName: '',
  taxNo: '',
  address: '',
  phone: '',
  email: '',
  bankName: '',
  bankAccount: '',
  remark: ''
})

const rules: FormRules = {
  supplierNo: [{ required: true, message: '请输入供应商编码', trigger: 'blur' }],
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  supplierType: [{ required: true, message: '请选择供应商类型', trigger: 'change' }],
  taxNo: [
    {
      validator: (_rule, value, callback) => {
        if (value && !/^[0-9A-Z]{15,20}$/.test(value)) {
          callback(new Error('15 至 20 位数字或大写字母'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  form.id = undefined
  form.supplierNo = ''
  form.supplierName = ''
  form.supplierType = undefined
  form.taxNo = ''
  form.address = ''
  form.phone = ''
  form.email = ''
  form.bankName = ''
  form.bankAccount = ''
  form.remark = ''
  contacts.value = []
  scopes.value = []
  attachments.value = []
  activeTab.value = 'base'
  formVisible.value = true
}

async function openEdit(row: SupplierRow) {
  form.id = row.id
  form.supplierNo = row.supplierNo
  form.supplierName = row.supplierName
  form.supplierType = row.supplierType
  form.taxNo = row.taxNo || ''
  form.address = row.address || ''
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.bankName = row.bankName || ''
  form.bankAccount = row.bankAccount || ''
  form.remark = row.remark || ''
  activeTab.value = 'base'
  formVisible.value = true
  await Promise.all([loadContacts(row.id), loadScopes(row.id), loadAttachments(row.id)])
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = {
      supplierNo: form.supplierNo,
      supplierName: form.supplierName,
      supplierType: form.supplierType,
      taxNo: form.taxNo,
      address: form.address,
      phone: form.phone,
      email: form.email,
      bankName: form.bankName,
      bankAccount: form.bankAccount,
      remark: form.remark
    }
    if (form.id) {
      await updateSupplier(form.id, payload)
      ElMessage.success('供应商已更新')
    } else {
      await createSupplier(payload)
      ElMessage.success('供应商已创建，可继续维护联系人与供货范围')
    }
    formVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

async function toggle(row: SupplierRow) {
  const disabling = row.status === 1
  await ElMessageBox.confirm(
    disabling ? '停用后该供应商不可用于新订单，存量单据不受影响。' : '确认启用该供应商？',
    disabling ? '停用确认' : '启用确认',
    { type: 'warning' }
  )
  await toggleSupplierStatus(row.id)
  ElMessage.success(disabling ? '已停用' : '已启用')
  await reload()
}

// ---------------- 联系人 ----------------

const contacts = ref<(ContactRow & { __index: number })[]>([])
const defaultContactIndex = ref(-1)

function reindexContacts() {
  contacts.value.forEach((item, index) => {
    item.__index = index
    item.isDefault = index === defaultContactIndex.value ? 1 : 0
  })
}

async function loadContacts(id: number) {
  const list = await supplierContacts(id)
  contacts.value = list.map((item) => ({ ...item, __index: 0 }))
  defaultContactIndex.value = contacts.value.findIndex((item) => item.isDefault === 1)
  reindexContacts()
}

async function submitContacts() {
  if (!form.id) {
    return
  }
  const named = contacts.value.filter((item) => item.contactName.trim())
  if (named.length !== contacts.value.length) {
    ElMessage.warning('联系人姓名不能为空')
    return
  }
  saving.value = true
  try {
    await saveSupplierContacts(
      form.id,
      contacts.value.map((item) => ({
        contactName: item.contactName,
        position: item.position,
        phone: item.phone,
        email: item.email,
        isDefault: item.isDefault,
        status: item.status || 1,
        remark: item.remark
      }))
    )
    ElMessage.success('联系人已保存')
    await reload()
  } finally {
    saving.value = false
  }
}

// ---------------- 供货范围 ----------------

const scopes = ref<ScopeRow[]>([])
const categories = ref<CategoryNode[]>([])
const materialList = ref<MaterialRow[]>([])

const materialSelectOptions = computed(() =>
  materialList.value.map((item) => ({ id: item.id, label: `${item.materialNo} ${item.materialName}` }))
)

const categoryOptions = computed(() =>
  flatten(categories.value).map((item) => ({ id: item.id, label: item.categoryName }))
)

function flatten(nodes: CategoryNode[], depth = 0): { id: number; categoryName: string }[] {
  const result: { id: number; categoryName: string }[] = []
  for (const node of nodes) {
    result.push({ id: node.id, categoryName: '　'.repeat(depth) + node.categoryName })
    result.push(...flatten(node.children, depth + 1))
  }
  return result
}

async function loadScopes(id: number) {
  scopes.value = await supplierScopes(id)
}

async function submitScopes() {
  if (!form.id) {
    return
  }
  const filled = scopes.value.filter((item) => item.scopeId)
  if (filled.length !== scopes.value.length) {
    ElMessage.warning('请选择供货范围对象')
    return
  }
  saving.value = true
  try {
    await saveSupplierScopes(
      form.id,
      scopes.value.map((item) => ({
        scopeType: item.scopeType,
        scopeId: item.scopeId,
        remark: item.remark || ''
      }))
    )
    ElMessage.success('供货范围已保存')
    await reload()
  } finally {
    saving.value = false
  }
}

// ---------------- 资质附件 ----------------

const attachments = ref<AttachmentRow[]>([])
const attachmentsLoading = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()
const attachmentFile = ref<File | null>(null)
const attachmentExpire = ref<string>()

function onFilePicked(event: Event) {
  const target = event.target as HTMLInputElement
  attachmentFile.value = target.files && target.files[0] ? target.files[0] : null
}

function formatSize(size: number) {
  if (!size) {
    return '-'
  }
  if (size < 1024) {
    return size + ' B'
  }
  if (size < 1024 * 1024) {
    return (size / 1024).toFixed(1) + ' KB'
  }
  return (size / 1024 / 1024).toFixed(2) + ' MB'
}

async function loadAttachments(id: number) {
  attachmentsLoading.value = true
  try {
    attachments.value = await supplierAttachments(id)
  } finally {
    attachmentsLoading.value = false
  }
}

async function submitAttachment() {
  if (!form.id || !attachmentFile.value) {
    return
  }
  const formData = new FormData()
  formData.append('file', attachmentFile.value)
  if (attachmentExpire.value) {
    formData.append('expireDate', attachmentExpire.value)
  }
  uploading.value = true
  try {
    await uploadSupplierAttachment(form.id, formData)
    ElMessage.success('附件已上传')
    attachmentFile.value = null
    await loadAttachments(form.id)
    await reload()
  } finally {
    uploading.value = false
  }
}

async function downloadAttachment(row: AttachmentRow) {
  await downloadCsv(attachmentDownloadUrl(row.id), row.fileName)
}

async function removeAttachment(row: AttachmentRow) {
  await ElMessageBox.confirm(`删除附件记录 ${row.fileName}？文件保留在服务端作为审计依据。`, '删除确认', {
    type: 'warning'
  })
  await deleteSupplierAttachment(row.id)
  ElMessage.success('附件记录已删除')
  if (form.id) {
    await loadAttachments(form.id)
  }
  await reload()
}

onMounted(async () => {
  const [typeList, tree, materials] = await Promise.all([
    dictItems('supplier_type'),
    categoryTree(),
    materialOptions()
  ])
  supplierTypes.value = typeList
  categories.value = tree
  materialList.value = materials
  await reload(1)
})

// 联系人行索引与默认主联系人联动：切换单选即重排 is_default
watch(defaultContactIndex, () => {
  reindexContacts()
})
</script>

<style scoped>
.supplier-page {
  /* 与全站页面留白同一个值：表格通栏依赖它算负边距 */
  padding: var(--page-pad-y) var(--page-pad-x);
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.toolbar .text-sub {
  margin: 6px 0 0;
}

.filter-bar {
  margin-bottom: 4px;
}

.scope-alert {
  margin-bottom: 14px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.upload-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
</style>

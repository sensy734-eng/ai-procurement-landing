<template>
  <div class="page-card material-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">物料档案<span class="page-code">M1</span></h2>
        <p class="text-sub">档案维护、启停、批量导入导出与分类树</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m1:material:import')" :icon="Upload" @click="openImport">
          批量导入
        </el-button>
        <el-button v-if="store.hasPerm('m1:material:export')" :icon="Download" @click="exportCsv">
          导出
        </el-button>
        <el-button v-if="store.hasPerm('m1:material_category:list')" :icon="FolderOpened" @click="openCategories">
          分类维护
        </el-button>
        <el-button
          v-if="store.hasPerm('m1:material:create')"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新增物料
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
      <el-form-item label="分类">
        <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 200px">
          <el-option v-for="item in flatCategories" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.materialType" placeholder="全部类型" clearable style="width: 140px">
          <el-option v-for="item in materialTypes" :key="item.id" :label="item.itemName" :value="Number(item.itemCode)" />
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
      title="物料档案是全系统共享主数据，不按数据范围过滤；单据与库存类查询才由后端注入范围条件。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="materialNo" label="物料编码" width="150" fixed="left" />
      <el-table-column prop="materialName" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" width="120" />
      <el-table-column prop="spec" label="规格" width="120" show-overflow-tooltip />
      <el-table-column prop="brand" label="品牌" width="100" show-overflow-tooltip />
      <el-table-column prop="unitName" label="单位" width="70" />
      <el-table-column prop="materialTypeName" label="类型" width="90" />
      <el-table-column prop="safetyStock" label="预警下限" width="100" align="right" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="扩展属性" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.extAttrs && row.extAttrs.length" size="small" type="warning">
            {{ row.extAttrs.length }} 项
          </el-tag>
          <span v-else class="text-sub">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m1:material:update')" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button
            v-if="store.hasPerm('m1:material:toggle')"
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

    <!-- 新增 / 编辑物料 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑物料' : '新增物料'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="物料编码" prop="materialNo">
          <el-input v-model="form.materialNo" :disabled="!!form.id" placeholder="全系统唯一，创建后不可改" />
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input v-model="form.materialName" />
        </el-form-item>
        <el-form-item label="物料分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="item in flatCategories" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="form.spec" />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.brand" />
        </el-form-item>
        <el-form-item label="计量单位" prop="unitId">
          <el-select v-model="form.unitId" placeholder="选择单位" style="width: 100%">
            <el-option v-for="item in units" :key="item.id" :label="`${item.itemName}（${item.itemCode}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料类型" prop="materialType">
          <el-select v-model="form.materialType" placeholder="选择类型" style="width: 100%">
            <el-option v-for="item in materialTypes" :key="item.id" :label="item.itemName" :value="Number(item.itemCode)" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警下限">
          <el-input-number v-model="form.safetyStock" :min="0" :precision="4" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="扩展属性">
          <div class="ext-attrs">
            <div v-for="(attr, index) in form.extAttrs" :key="index" class="ext-row">
              <el-input v-model="attr.attrName" placeholder="属性名，如材质" style="flex: 1" />
              <el-input v-model="attr.attrValue" placeholder="属性值" style="flex: 1" />
              <el-button link type="danger" :icon="Delete" @click="removeAttr(index)" />
            </div>
            <el-button link type="primary" :icon="Plus" @click="addAttr">添加属性</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入物料" width="720px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="M1-1-5：按 CSV 模板逐行校验，返回成功失败明细；已存在的编码按行更新，失败行不影响成功行。"
      />
      <div class="import-bar">
        <el-button :icon="Download" link type="primary" @click="downloadMaterialTemplate">下载模板</el-button>
        <input ref="fileInput" type="file" accept=".csv" hidden @change="onFilePicked" />
        <el-button :icon="Upload" @click="fileInput?.click()">选择 CSV 文件</el-button>
        <span v-if="importFile" class="text-sub">{{ importFile.name }}</span>
      </div>
      <el-table v-if="importResult" :data="importResult.rows" border size="small" max-height="320">
        <el-table-column prop="lineNo" label="行号" width="70" align="center" />
        <el-table-column prop="materialNo" label="物料编码" width="180" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? row.action : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="160" show-overflow-tooltip />
      </el-table>
      <div v-if="importResult" class="import-summary">
        共 {{ importResult.totalCount }} 行，成功 {{ importResult.successCount }} 行，失败 {{ importResult.failCount }} 行
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 分类维护 -->
    <el-dialog v-model="categoryVisible" title="物料分类维护" width="560px">
      <p class="text-sub">分类树供物料档案引用；上级不能是自身或子孙节点。计量单位在业务字典页按 unit 类型维护。</p>
      <div class="category-body">
        <el-tree
          :data="categories"
          node-key="id"
          :props="{ label: 'categoryName', children: 'children' }"
          highlight-current
          default-expand-all
          @current-change="onCategoryNodeChange"
        />
        <el-form v-if="categoryFormVisible" ref="categoryFormRef" :model="categoryForm" :rules="categoryRules" label-width="88px">
          <el-form-item label="分类编码" prop="categoryNo">
            <el-input v-model="categoryForm.categoryNo" :disabled="!!categoryForm.id" />
          </el-form-item>
          <el-form-item label="分类名称" prop="categoryName">
            <el-input v-model="categoryForm.categoryName" />
          </el-form-item>
          <el-form-item label="上级分类">
            <el-select v-model="categoryForm.parentId" style="width: 100%">
              <el-option label="（顶级分类）" :value="0" />
              <el-option v-for="item in categoryParentOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number v-model="categoryForm.sortNo" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="categoryForm.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="2">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="categoryForm.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button :icon="Plus" @click="openCategoryCreate(null)">新增顶级</el-button>
        <el-button :icon="Plus" :disabled="!currentCategory" @click="openCategoryCreate(currentCategory)">
          新增下级
        </el-button>
        <el-button :disabled="!currentCategory" @click="openCategoryEdit">编辑选中</el-button>
        <el-button type="primary" :loading="saving" :disabled="!categoryFormVisible" @click="submitCategory">
          保存分类
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Download, FolderOpened, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  categoryTree,
  createCategory,
  createMaterial,
  dictItems,
  downloadMaterialTemplate,
  importMaterials,
  materialExportUrl,
  pageMaterials,
  toggleMaterialStatus,
  updateCategory,
  updateMaterial,
  type CategoryNode,
  type DictItemRow,
  type ImportResult,
  type MaterialRow
} from '@/api/m1'
import { downloadCsv } from '@/api/m11'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<MaterialRow[]>([])
const total = ref(0)
const categories = ref<CategoryNode[]>([])
const units = ref<DictItemRow[]>([])
const materialTypes = ref<DictItemRow[]>([])

const query = reactive<{
  pageNo: number
  pageSize: number
  keyword: string
  categoryId?: number
  status?: number
  materialType?: number
}>({ pageNo: 1, pageSize: 10, keyword: '' })

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

/** 分类树拍平为下拉项，用全角空格表示层级 */
const flatCategories = computed(() => flattenTree(categories.value))

function flattenTree(nodes: CategoryNode[], depth = 0): { id: number; label: string }[] {
  const result: { id: number; label: string }[] = []
  for (const node of nodes) {
    result.push({ id: node.id, label: '　'.repeat(depth) + node.categoryName })
    result.push(...flattenTree(node.children, depth + 1))
  }
  return result
}

const categoryParentOptions = computed(() =>
  flatCategories.value.filter((item) => item.id !== categoryForm.id)
)

async function reload(page = query.pageNo) {
  loading.value = true
  try {
    const result = await pageMaterials({
      pageNo: page,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
      categoryId: query.categoryId,
      status: query.status,
      materialType: query.materialType
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
  query.categoryId = undefined
  query.status = undefined
  query.materialType = undefined
  reload(1)
}

async function exportCsv() {
  await downloadCsv(
    materialExportUrl({
      keyword: query.keyword.trim() || undefined,
      categoryId: query.categoryId,
      status: query.status,
      materialType: query.materialType
    }),
    '物料档案.csv'
  )
  ElMessage.success('导出已开始')
}

// ---------------- 表单 ----------------

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{
  id?: number
  materialNo: string
  materialName: string
  categoryId?: number
  spec: string
  brand: string
  unitId?: number
  materialType?: number
  safetyStock: number
  remark: string
  extAttrs: { attrName: string; attrValue: string; sortNo?: number }[]
}>({
  materialNo: '',
  materialName: '',
  spec: '',
  brand: '',
  safetyStock: 0,
  remark: '',
  extAttrs: []
})

const rules: FormRules = {
  materialNo: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择物料分类', trigger: 'change' }],
  unitId: [{ required: true, message: '请选择计量单位', trigger: 'change' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }]
}

function openCreate() {
  form.id = undefined
  form.materialNo = ''
  form.materialName = ''
  form.categoryId = undefined
  form.spec = ''
  form.brand = ''
  form.unitId = undefined
  form.materialType = undefined
  form.safetyStock = 0
  form.remark = ''
  form.extAttrs = []
  formVisible.value = true
}

function openEdit(row: MaterialRow) {
  form.id = row.id
  form.materialNo = row.materialNo
  form.materialName = row.materialName
  form.categoryId = row.categoryId
  form.spec = row.spec || ''
  form.brand = row.brand || ''
  form.unitId = row.unitId
  form.materialType = row.materialType
  form.safetyStock = Number(row.safetyStock || 0)
  form.remark = row.remark || ''
  form.extAttrs = (row.extAttrs || []).map((attr) => ({
    attrName: attr.attrName,
    attrValue: attr.attrValue || '',
    sortNo: attr.sortNo
  }))
  formVisible.value = true
}

function addAttr() {
  form.extAttrs.push({ attrName: '', attrValue: '' })
}

function removeAttr(index: number) {
  form.extAttrs.splice(index, 1)
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  const namedAttrs = form.extAttrs.filter((attr) => attr.attrName.trim())
  if (namedAttrs.length !== form.extAttrs.length) {
    ElMessage.warning('扩展属性名不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      materialNo: form.materialNo,
      materialName: form.materialName,
      categoryId: form.categoryId,
      spec: form.spec,
      brand: form.brand,
      unitId: form.unitId,
      materialType: form.materialType,
      safetyStock: form.safetyStock,
      remark: form.remark,
      extAttrs: namedAttrs
    }
    if (form.id) {
      await updateMaterial(form.id, payload)
      ElMessage.success('物料已更新')
    } else {
      await createMaterial(payload)
      ElMessage.success('物料已创建')
    }
    formVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

async function toggle(row: MaterialRow) {
  const disabling = row.status === 1
  await ElMessageBox.confirm(
    disabling ? '停用后该物料不可在新单据中选择，存量单据不受影响。' : '确认启用该物料？',
    disabling ? '停用确认' : '启用确认',
    { type: 'warning' }
  )
  await toggleMaterialStatus(row.id)
  ElMessage.success(disabling ? '已停用' : '已启用')
  await reload()
}

// ---------------- 导入 ----------------

const importVisible = ref(false)
const importing = ref(false)
const fileInput = ref<HTMLInputElement>()
const importFile = ref<File | null>(null)
const importResult = ref<ImportResult | null>(null)

function openImport() {
  importFile.value = null
  importResult.value = null
  importVisible.value = true
}

function onFilePicked(event: Event) {
  const target = event.target as HTMLInputElement
  importFile.value = target.files && target.files[0] ? target.files[0] : null
  importResult.value = null
}

async function submitImport() {
  if (!importFile.value) {
    return
  }
  importing.value = true
  try {
    importResult.value = await importMaterials(importFile.value)
    ElMessage.success(
      `导入完成：成功 ${importResult.value.successCount} 行，失败 ${importResult.value.failCount} 行`
    )
    await reload()
  } finally {
    importing.value = false
  }
}

// ---------------- 分类维护 ----------------

const categoryVisible = ref(false)
const categoryFormVisible = ref(false)
const categoryFormRef = ref<FormInstance>()
const currentCategory = ref<CategoryNode | null>(null)
const categoryForm = reactive<{
  id?: number
  categoryNo: string
  categoryName: string
  parentId: number
  sortNo: number
  status: number
  remark: string
}>({ categoryNo: '', categoryName: '', parentId: 0, sortNo: 0, status: 1, remark: '' })

const categoryRules: FormRules = {
  categoryNo: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

function onCategoryNodeChange(node: CategoryNode | null) {
  currentCategory.value = node
}

function openCategories() {
  categoryVisible.value = true
  categoryFormVisible.value = false
  currentCategory.value = null
}

function openCategoryCreate(parent: CategoryNode | null) {
  categoryForm.id = undefined
  categoryForm.categoryNo = ''
  categoryForm.categoryName = ''
  categoryForm.parentId = parent ? parent.id : 0
  categoryForm.sortNo = 0
  categoryForm.status = 1
  categoryForm.remark = ''
  categoryFormVisible.value = true
}

function openCategoryEdit() {
  if (!currentCategory.value) {
    return
  }
  categoryForm.id = currentCategory.value.id
  categoryForm.categoryNo = currentCategory.value.categoryNo
  categoryForm.categoryName = currentCategory.value.categoryName
  categoryForm.parentId = currentCategory.value.parentId
  categoryForm.sortNo = currentCategory.value.sortNo
  categoryForm.status = currentCategory.value.status
  categoryForm.remark = currentCategory.value.remark || ''
  categoryFormVisible.value = true
}

async function submitCategory() {
  if (!categoryFormRef.value) {
    return
  }
  const valid = await categoryFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = {
      categoryNo: categoryForm.categoryNo,
      categoryName: categoryForm.categoryName,
      parentId: categoryForm.parentId,
      sortNo: categoryForm.sortNo,
      status: categoryForm.status,
      remark: categoryForm.remark
    }
    if (categoryForm.id) {
      await updateCategory(categoryForm.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    }
    categoryFormVisible.value = false
    await loadCategories()
  } finally {
    saving.value = false
  }
}

async function loadCategories() {
  categories.value = await categoryTree()
}

onMounted(async () => {
  const [tree, unitList, typeList] = await Promise.all([
    categoryTree(),
    dictItems('unit'),
    dictItems('material_type')
  ])
  categories.value = tree
  units.value = unitList
  materialTypes.value = typeList
  await reload(1)
})
</script>

<style scoped>
.material-page {
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

.ext-attrs {
  width: 100%;
}

.ext-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.import-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}

.import-summary {
  margin-top: 10px;
  font-size: 13px;
  color: #4b5563;
}

.category-body {
  display: flex;
  gap: 20px;
  margin-top: 12px;
}

.category-body .el-tree {
  width: 220px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.category-body .el-form {
  flex: 1;
}
</style>

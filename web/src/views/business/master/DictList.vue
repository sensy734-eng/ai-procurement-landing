<template>
  <div class="page-card dict-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">业务字典<span class="page-code">M1</span></h2>
        <p class="text-sub">字典类型与字典项；计量单位与单据类型在此维护</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m1:dict:create')" type="primary" :icon="Plus" @click="openTypeCreate">
          新增类型
        </el-button>
      </div>
    </header>

    <div class="dict-body">
      <section class="type-panel">
        <el-table
          v-loading="typeLoading"
          :data="types"
          border
          size="small"
          highlight-current-row
          @current-change="onTypeChange"
        >
          <el-table-column prop="typeCode" label="类型编码" width="140" />
          <el-table-column prop="typeName" label="类型名称" min-width="100" />
          <el-table-column label="项数" width="56" align="center">
            <template #default="{ row }">{{ row.itemCount }}</template>
          </el-table-column>
          <el-table-column label="状态" width="62" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="62" align="center">
            <template #default="{ row }">
              <el-button
                v-if="store.hasPerm('m1:dict:update')"
                link
                type="primary"
                @click="openTypeEdit(row)"
              >
                编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="item-panel">
        <div class="item-toolbar">
          <span class="item-title">{{ currentType ? `${currentType.typeName}（${currentType.typeCode}）` : '请选择字典类型' }}</span>
          <el-button
            v-if="store.hasPerm('m1:dict:create') && currentType"
            type="primary"
            :icon="Plus"
            size="small"
            @click="openItemCreate"
          >
            新增字典项
          </el-button>
        </div>

        <el-alert v-if="typeHint" class="type-hint" :title="typeHint" type="warning" :closable="false" show-icon />
        <el-alert
          v-else
          class="type-hint"
          title="编码创建后不可修改；停用替代删除，存量单据引用不受影响。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-table v-loading="itemLoading" :data="items" border size="small">
          <el-table-column prop="itemCode" label="项编码" width="110" />
          <el-table-column prop="itemName" label="项名称" min-width="110" />
          <el-table-column prop="parentName" label="上级" width="90">
            <template #default="{ row }">{{ row.parentName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="60" align="center" />
          <el-table-column prop="extValue" label="扩展值" width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ row.extValue || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="62" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="62" align="center">
            <template #default="{ row }">
              <el-button
                v-if="store.hasPerm('m1:dict:update')"
                link
                type="primary"
                @click="openItemEdit(row)"
              >
                编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <!-- 字典类型表单 -->
    <el-dialog v-model="typeFormVisible" :title="typeForm.id ? '编辑字典类型' : '新增字典类型'" width="460px">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="96px">
        <el-form-item label="类型编码" prop="typeCode">
          <el-input v-model="typeForm.typeCode" :disabled="!!typeForm.id" placeholder="小写字母开头，如 bill_type" />
        </el-form-item>
        <el-form-item label="类型名称" prop="typeName">
          <el-input v-model="typeForm.typeName" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典项表单 -->
    <el-dialog v-model="itemFormVisible" :title="itemForm.id ? '编辑字典项' : '新增字典项'" width="460px">
      <el-alert v-if="itemHint" class="item-hint" :title="itemHint" type="warning" :closable="false" show-icon />
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="96px">
        <el-form-item label="项编码" prop="itemCode">
          <el-input v-model="itemForm.itemCode" :disabled="!!itemForm.id" :placeholder="codePlaceholder" />
        </el-form-item>
        <el-form-item label="项名称" prop="itemName">
          <el-input v-model="itemForm.itemName" />
        </el-form-item>
        <el-form-item v-if="currentType?.typeCode === 'dept'" label="上级部门">
          <el-select v-model="itemForm.parentId" clearable placeholder="（顶级）" style="width: 100%">
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.itemName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="itemForm.sortNo" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="extValueLabel">
          <el-input v-model="itemForm.extValue" :placeholder="extValuePlaceholder" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createDictItem,
  createDictType,
  dictItemsAdmin,
  dictTypes,
  updateDictItem,
  updateDictType,
  type DictItemRow,
  type DictTypeRow
} from '@/api/m1'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const typeLoading = ref(false)
const itemLoading = ref(false)
const saving = ref(false)
const types = ref<DictTypeRow[]>([])
const items = ref<(DictItemRow & { parentName: string })[]>([])
const currentType = ref<DictTypeRow | null>(null)

async function loadTypes() {
  typeLoading.value = true
  try {
    types.value = await dictTypes()
  } finally {
    typeLoading.value = false
  }
}

async function loadItems(typeCode: string) {
  itemLoading.value = true
  try {
    const list = await dictItemsAdmin(typeCode)
    const parentMap = new Map(list.map((item) => [item.id, item.itemName]))
    items.value = list.map((item) => ({
      ...item,
      parentName: item.parentId ? parentMap.get(item.parentId) || '' : ''
    }))
  } finally {
    itemLoading.value = false
  }
}

function onTypeChange(row: DictTypeRow | null) {
  currentType.value = row
  if (row) {
    loadItems(row.typeCode)
  } else {
    items.value = []
  }
}

/** 按字典类型给出针对性提示，避免误改系统预置项 */
const typeHint = computed(() => {
  const code = currentType.value?.typeCode
  switch (code) {
    case 'bill_type':
      return 'M1-4-2 单据类型：扩展值即单据编号前缀（如 CG-SQ），决定编号规则与统计分组。'
    case 'supplier_type':
      return 'M1-4-2 供应商类型：项编码须为 1 至 3 的整数，与供应商档案的类型字段一致。'
    case 'material_type':
      return '物料类型：项编码须为 1 至 4 的整数，与物料档案的类型字段一致。'
    case 'unit':
      return 'M1-1-7 计量单位：编码为大写字母数字（如 PCS、KG），物料导入按编码寻址。'
    case 'dept':
      return '部门字典：上级id 形成部门树，用户档案与数据范围都引用这里。'
    case 'lifecycle_status':
    case 'audit_status':
    case 'fulfill_status':
    case 'item_status':
      return '状态字典由系统预置：编码即三状态口径，只可改名称与排序，不要改编码。'
    default:
      return ''
  }
})

const extValueLabel = computed(() => {
  if (currentType.value?.typeCode === 'bill_type') {
    return '编号前缀'
  }
  return '扩展值'
})

const extValuePlaceholder = computed(() => {
  if (currentType.value?.typeCode === 'bill_type') {
    return '2 至 10 位大写字母、数字或连字符，如 CG-SQ'
  }
  return '可选'
})

const codePlaceholder = computed(() => {
  const code = currentType.value?.typeCode
  if (code === 'bill_type') {
    return '数字序号，如 3'
  }
  if (code === 'supplier_type') {
    return '1 生产厂家、2 经销商、3 服务商'
  }
  if (code === 'material_type') {
    return '1 原材料、2 辅料、3 外购件、4 备件'
  }
  if (code === 'unit') {
    return '大写字母数字，如 PCS'
  }
  return ''
})

const itemHint = computed(() => typeHint.value)

const parentOptions = computed(() => items.value.filter((item) => item.id !== itemForm.id))

// ---------------- 字典类型表单 ----------------

const typeFormVisible = ref(false)
const typeFormRef = ref<FormInstance>()
const typeForm = reactive<{
  id?: number
  typeCode: string
  typeName: string
  status: number
  remark: string
}>({ typeCode: '', typeName: '', status: 1, remark: '' })

const typeRules: FormRules = {
  typeCode: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{1,49}$/, message: '小写字母开头，可含小写字母、数字与下划线', trigger: 'blur' }
  ],
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }]
}

function openTypeCreate() {
  typeForm.id = undefined
  typeForm.typeCode = ''
  typeForm.typeName = ''
  typeForm.status = 1
  typeForm.remark = ''
  typeFormVisible.value = true
}

function openTypeEdit(row: DictTypeRow) {
  typeForm.id = row.id
  typeForm.typeCode = row.typeCode
  typeForm.typeName = row.typeName
  typeForm.status = row.status
  typeForm.remark = row.remark || ''
  typeFormVisible.value = true
}

async function submitType() {
  if (!typeFormRef.value) {
    return
  }
  const valid = await typeFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = {
      typeCode: typeForm.typeCode,
      typeName: typeForm.typeName,
      status: typeForm.status,
      remark: typeForm.remark
    }
    if (typeForm.id) {
      await updateDictType(typeForm.id, payload)
      ElMessage.success('字典类型已更新')
    } else {
      await createDictType(payload)
      ElMessage.success('字典类型已创建')
    }
    typeFormVisible.value = false
    await loadTypes()
  } finally {
    saving.value = false
  }
}

// ---------------- 字典项表单 ----------------

const itemFormVisible = ref(false)
const itemFormRef = ref<FormInstance>()
const itemForm = reactive<{
  id?: number
  typeId: number
  itemCode: string
  itemName: string
  parentId?: number
  sortNo: number
  status: number
  extValue: string
  remark: string
}>({ typeId: 0, itemCode: '', itemName: '', sortNo: 0, status: 1, extValue: '', remark: '' })

const itemRules: FormRules = {
  itemCode: [{ required: true, message: '请输入项编码', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入项名称', trigger: 'blur' }]
}

function openItemCreate() {
  if (!currentType.value) {
    return
  }
  itemForm.id = undefined
  itemForm.typeId = currentType.value.id
  itemForm.itemCode = ''
  itemForm.itemName = ''
  itemForm.parentId = undefined
  itemForm.sortNo = 0
  itemForm.status = 1
  itemForm.extValue = ''
  itemForm.remark = ''
  itemFormVisible.value = true
}

function openItemEdit(row: DictItemRow) {
  itemForm.id = row.id
  itemForm.typeId = row.typeId
  itemForm.itemCode = row.itemCode
  itemForm.itemName = row.itemName
  itemForm.parentId = row.parentId || undefined
  itemForm.sortNo = row.sortNo
  itemForm.status = row.status
  itemForm.extValue = row.extValue || ''
  itemForm.remark = row.remark || ''
  itemFormVisible.value = true
}

async function submitItem() {
  if (!itemFormRef.value) {
    return
  }
  const valid = await itemFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const payload = {
      typeId: itemForm.typeId,
      itemCode: itemForm.itemCode,
      itemName: itemForm.itemName,
      parentId: itemForm.parentId,
      sortNo: itemForm.sortNo,
      status: itemForm.status,
      extValue: itemForm.extValue,
      remark: itemForm.remark
    }
    if (itemForm.id) {
      await updateDictItem(itemForm.id, payload)
      ElMessage.success('字典项已更新')
    } else {
      await createDictItem(payload)
      ElMessage.success('字典项已创建')
    }
    itemFormVisible.value = false
    if (currentType.value) {
      await loadItems(currentType.value.typeCode)
    }
    await loadTypes()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadTypes()
})
</script>

<style scoped>
.dict-page {
  padding: 20px 24px;
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

.dict-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.type-panel {
  width: 420px;
  flex-shrink: 0;
}

.item-panel {
  flex: 1;
  min-width: 0;
}

.item-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f4e79;
}

.type-hint {
  margin-bottom: 10px;
}

.item-hint {
  margin-bottom: 10px;
}
</style>

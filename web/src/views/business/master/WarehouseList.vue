<template>
  <div class="page-card warehouse-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">仓库与库位</h2>
        <p class="text-sub">P3｜M1-3 仓库档案、库位与使用人对应关系（数据范围授权依据）</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m1:warehouse:create')" type="primary" :icon="Plus" @click="openCreate">
          新增仓库
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
      title="仓库档案由系统管理员维护；使用人对应关系是数据范围授权的依据，与角色或用户级范围配合生效。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="warehouseNo" label="仓库编码" width="150" fixed="left" />
      <el-table-column prop="warehouseName" label="仓库名称" min-width="150" />
      <el-table-column prop="managerName" label="负责人" width="100">
        <template #default="{ row }">{{ row.managerName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="address" label="地址" min-width="160" show-overflow-tooltip />
      <el-table-column prop="phone" label="电话" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="库位数" width="80" align="center">
        <template #default="{ row }">{{ row.locationCount }}</template>
      </el-table-column>
      <el-table-column label="使用人数" width="90" align="center">
        <template #default="{ row }">{{ row.userCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m1:warehouse:update')" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button v-if="store.hasPerm('m1:location:list')" link type="primary" @click="openLocations(row)">
            库位
          </el-button>
          <el-button
            v-if="store.hasPerm('m1:warehouse_user:manage')"
            link
            type="primary"
            @click="openUsers(row)"
          >
            使用人
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

    <!-- 新增 / 编辑仓库 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑仓库' : '新增仓库'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="仓库编码" prop="warehouseNo">
          <el-input v-model="form.warehouseNo" :disabled="!!form.id" placeholder="全系统唯一，创建后不可改" />
        </el-form-item>
        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="form.warehouseName" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.managerId" placeholder="选择负责人" clearable filterable style="width: 100%">
            <el-option v-for="item in users" :key="item.id" :label="`${item.realName}（${item.username}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 库位维护 -->
    <el-dialog v-model="locationVisible" :title="`库位维护 - ${currentWarehouse?.warehouseName || ''}`" width="620px">
      <div class="location-bar">
        <el-input v-model="newLocationNo" placeholder="新库位编码" style="width: 180px" />
        <el-input v-model="newLocationName" placeholder="库位名称（可选）" style="width: 180px" />
        <el-button type="primary" :icon="Plus" :loading="saving" @click="submitLocation">添加库位</el-button>
      </div>
      <el-table v-loading="locationLoading" :data="locations" border size="small">
        <el-table-column prop="locationNo" label="库位编码" width="160" />
        <el-table-column prop="locationName" label="库位名称" min-width="140" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="toggleLocation(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="locationVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 使用人分配 -->
    <el-dialog v-model="userVisible" :title="`使用人分配 - ${currentWarehouse?.warehouseName || ''}`" width="560px">
      <p class="text-sub">M1-3-3：整体替换该仓库的使用人；默认仓库使用人至多一个，且必须在列表中。</p>
      <el-table
        ref="userTableRef"
        v-loading="userLoading"
        :data="users"
        border
        size="small"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="deptName" label="部门" min-width="110" />
        <el-table-column label="默认仓库" width="90" align="center">
          <template #default="{ row }">
            <el-radio v-model="defaultUserId" :label="row.id" :disabled="!isSelected(row.id)" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="userVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUsers">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElTable, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createWarehouse,
  createWarehouseLocation,
  pageWarehouses,
  saveWarehouseUsers,
  updateWarehouse,
  updateWarehouseLocation,
  warehouseLocations,
  warehouseUsers,
  type LocationRow,
  type WarehouseRow,
  type WarehouseUserRow
} from '@/api/m1'
import { pageUsers, type UserRow } from '@/api/m11'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<WarehouseRow[]>([])
const total = ref(0)
const users = ref<UserRow[]>([])

const query = reactive<{ pageNo: number; pageSize: number; keyword: string; status?: number }>({
  pageNo: 1,
  pageSize: 10,
  keyword: ''
})

async function reload(page = query.pageNo) {
  loading.value = true
  try {
    const result = await pageWarehouses({
      pageNo: page,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
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
  query.status = undefined
  reload(1)
}

// ---------------- 仓库表单 ----------------

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{
  id?: number
  warehouseNo: string
  warehouseName: string
  managerId?: number
  address: string
  phone: string
  remark: string
}>({ warehouseNo: '', warehouseName: '', address: '', phone: '', remark: '' })

const rules: FormRules = {
  warehouseNo: [{ required: true, message: '请输入仓库编码', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }]
}

function openCreate() {
  form.id = undefined
  form.warehouseNo = ''
  form.warehouseName = ''
  form.managerId = undefined
  form.address = ''
  form.phone = ''
  form.remark = ''
  formVisible.value = true
}

function openEdit(row: WarehouseRow) {
  form.id = row.id
  form.warehouseNo = row.warehouseNo
  form.warehouseName = row.warehouseName
  form.managerId = row.managerId
  form.address = row.address || ''
  form.phone = row.phone || ''
  form.remark = row.remark || ''
  formVisible.value = true
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
      warehouseNo: form.warehouseNo,
      warehouseName: form.warehouseName,
      managerId: form.managerId,
      address: form.address,
      phone: form.phone,
      remark: form.remark
    }
    if (form.id) {
      await updateWarehouse(form.id, payload)
      ElMessage.success('仓库已更新')
    } else {
      await createWarehouse(payload)
      ElMessage.success('仓库已创建')
    }
    formVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

// ---------------- 库位 ----------------

const locationVisible = ref(false)
const locationLoading = ref(false)
const locations = ref<LocationRow[]>([])
const currentWarehouse = ref<WarehouseRow | null>(null)
const newLocationNo = ref('')
const newLocationName = ref('')

async function openLocations(row: WarehouseRow) {
  currentWarehouse.value = row
  locationVisible.value = true
  await loadLocations(row.id)
}

async function loadLocations(warehouseId: number) {
  locationLoading.value = true
  try {
    locations.value = await warehouseLocations(warehouseId)
  } finally {
    locationLoading.value = false
  }
}

async function submitLocation() {
  if (!currentWarehouse.value || !newLocationNo.value.trim()) {
    ElMessage.warning('请输入库位编码')
    return
  }
  saving.value = true
  try {
    await createWarehouseLocation(currentWarehouse.value.id, {
      locationNo: newLocationNo.value.trim(),
      locationName: newLocationName.value.trim() || undefined
    })
    ElMessage.success('库位已添加')
    newLocationNo.value = ''
    newLocationName.value = ''
    await loadLocations(currentWarehouse.value.id)
    await reload()
  } finally {
    saving.value = false
  }
}

async function toggleLocation(row: LocationRow) {
  await updateWarehouseLocation(row.id, { status: row.status === 1 ? 2 : 1 })
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  if (currentWarehouse.value) {
    await loadLocations(currentWarehouse.value.id)
  }
}

// ---------------- 使用人 ----------------

const userVisible = ref(false)
const userLoading = ref(false)
const userTableRef = ref<InstanceType<typeof ElTable>>()
const selectedUserIds = ref<number[]>([])
const defaultUserId = ref<number | null>(null)

function isSelected(id: number) {
  return selectedUserIds.value.includes(id)
}

function onSelectionChange(selection: WarehouseUserRow[] | UserRow[]) {
  selectedUserIds.value = selection.map((item) => item.id)
  if (defaultUserId.value && !selectedUserIds.value.includes(defaultUserId.value)) {
    defaultUserId.value = null
  }
}

async function openUsers(row: WarehouseRow) {
  currentWarehouse.value = row
  userVisible.value = true
  userLoading.value = true
  try {
    const existing = await warehouseUsers(row.id)
    selectedUserIds.value = existing.map((item) => item.userId)
    defaultUserId.value = existing.find((item) => item.isDefault === 1)?.userId ?? null
    await nextTick()
    userTableRef.value?.clearSelection()
    for (const user of users.value) {
      if (selectedUserIds.value.includes(user.id)) {
        userTableRef.value?.toggleRowSelection(user, true)
      }
    }
  } finally {
    userLoading.value = false
  }
}

async function submitUsers() {
  if (!currentWarehouse.value) {
    return
  }
  if (defaultUserId.value && !selectedUserIds.value.includes(defaultUserId.value)) {
    ElMessage.warning('默认仓库使用人必须在列表中')
    return
  }
  saving.value = true
  try {
    await saveWarehouseUsers(currentWarehouse.value.id, selectedUserIds.value, defaultUserId.value)
    ElMessage.success('使用人已保存')
    userVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const result = await pageUsers({ pageNo: 1, pageSize: 100 })
  users.value = result.records
  await reload(1)
})
</script>

<style scoped>
.warehouse-page {
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

.location-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
</style>

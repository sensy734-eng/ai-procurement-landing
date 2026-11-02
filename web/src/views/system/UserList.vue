<template>
  <div class="page-card user-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="text-sub">用户维护与角色分配，列表按部门数据范围过滤</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m11:user:create')" type="primary" :icon="Plus" @click="openCreate">
          新增用户
        </el-button>
      </div>
    </header>

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="账号或姓名"
          clearable
          style="width: 200px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="部门">
        <el-select v-model="query.deptId" placeholder="全部部门" clearable style="width: 180px">
          <el-option v-for="item in depts" :key="item.id" :label="item.itemName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
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
      :title="`当前数据范围：${store.scopeText}。范围条件由后端注入查询构造器，前端不参与过滤。`"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="username" label="账号" width="130" />
      <el-table-column label="姓名" width="140">
        <template #default="{ row }">
          <span>{{ row.realName }}</span>
          <el-tooltip v-if="row.pwdNeedChange === 1" content="初始密码待修改" placement="top">
            <span class="pwd-dot"></span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="deptName" label="部门" width="120" />
      <el-table-column label="角色" min-width="150">
        <template #default="{ row }">
          <el-tag v-for="name in row.roleNames" :key="name" size="small" class="role-tag">{{ name }}</el-tag>
          <span v-if="!row.roleNames || !row.roleNames.length" class="text-sub">未分配</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机" width="120">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录" width="150">
        <template #default="{ row }">{{ formatTime(row.lastLoginTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="235" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m11:user:update')" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button v-if="store.hasPerm('m11:user:update')" link type="primary" @click="openRoles(row)">
            角色
          </el-button>
          <el-button
            v-if="store.hasPerm('m11:user:toggle')"
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggle(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="store.hasPerm('m11:user:resetpwd')" link type="danger" @click="resetPwd(row)">
            重置密码
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

    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新增用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="字母开头，3 至 50 位" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-select v-model="form.deptId" placeholder="选择部门" style="width: 100%">
            <el-option v-for="item in depts" :key="item.id" :label="item.itemName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!form.id" label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="至少一个角色" style="width: 100%">
            <el-option v-for="role in enabledRoles" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
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

    <el-dialog v-model="roleVisible" title="分配角色" width="420px">
      <p class="text-sub role-hint">支持多角色，权限点取并集，数据范围取并集。</p>
      <el-checkbox-group v-model="roleSelection" class="role-group">
        <el-checkbox v-for="role in enabledRoles" :key="role.id" :value="role.id" :label="role.id">
          {{ role.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="初始密码" width="420px">
      <el-alert type="warning" :closable="false" show-icon
        title="初始密码只显示这一次，关闭后无法再次查看。用户首次登录需立即修改。" />
      <div class="pwd-box">
        <span class="pwd-label">{{ pwdResult.username }}</span>
        <code class="pwd-value">{{ pwdResult.initialPassword }}</code>
      </div>
      <template #footer>
        <el-button type="primary" @click="pwdVisible = false">我已记录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  assignUserRoles,
  createUser,
  listRoles,
  pageUsers,
  resetUserPassword,
  toggleUserStatus,
  updateUser,
  type ResetPwdResult,
  type RoleRow,
  type UserRow
} from '@/api/m11'
import { dictItems, type DictItem } from '@/api/system'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<UserRow[]>([])
const total = ref(0)
const depts = ref<DictItem[]>([])
const roles = ref<RoleRow[]>([])

const query = reactive<{ pageNo: number; pageSize: number; keyword: string; deptId?: number; status?: number }>({
  pageNo: 1,
  pageSize: 10,
  keyword: ''
})

const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{
  id?: number
  username: string
  realName: string
  deptId?: number
  phone: string
  email: string
  remark: string
  roleIds: number[]
}>({ username: '', realName: '', phone: '', email: '', remark: '', roleIds: [] })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,49}$/, message: '字母开头，3 至 50 位字母数字下划线', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  roleIds: [{ required: true, message: '至少选择一个角色', trigger: 'change' }]
}

const roleVisible = ref(false)
const roleSelection = ref<number[]>([])
const roleTarget = ref<UserRow | null>(null)

const pwdVisible = ref(false)
const pwdResult = ref<ResetPwdResult>({ userId: 0, username: '', initialPassword: '' })

const enabledRoles = computed(() => roles.value.filter((role) => role.status === 1))

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

async function reload(page = query.pageNo) {
  loading.value = true
  try {
    const result = await pageUsers({
      pageNo: page,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
      deptId: query.deptId,
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
  query.deptId = undefined
  query.status = undefined
  reload(1)
}

function openCreate() {
  form.id = undefined
  form.username = ''
  form.realName = ''
  form.deptId = undefined
  form.phone = ''
  form.email = ''
  form.remark = ''
  form.roleIds = []
  formVisible.value = true
}

function openEdit(row: UserRow) {
  form.id = row.id
  form.username = row.username
  form.realName = row.realName
  form.deptId = row.deptId
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.remark = row.remark || ''
  form.roleIds = row.roleIds || []
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
      username: form.username,
      realName: form.realName,
      deptId: form.deptId,
      phone: form.phone,
      email: form.email,
      remark: form.remark,
      roleIds: form.roleIds
    }
    if (form.id) {
      await updateUser(form.id, payload)
      ElMessage.success('用户已更新')
      formVisible.value = false
    } else {
      const result = await createUser(payload)
      formVisible.value = false
      pwdResult.value = result
      pwdVisible.value = true
    }
    await reload()
  } finally {
    saving.value = false
  }
}

function openRoles(row: UserRow) {
  roleTarget.value = row
  roleSelection.value = [...(row.roleIds || [])]
  roleVisible.value = true
}

async function submitRoles() {
  if (!roleTarget.value) {
    return
  }
  if (!roleSelection.value.length) {
    ElMessage.warning('至少保留一个角色')
    return
  }
  saving.value = true
  try {
    await assignUserRoles(roleTarget.value.id, roleSelection.value)
    ElMessage.success('角色已更新')
    roleVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

async function toggle(row: UserRow) {
  const next = row.status === 1 ? 2 : 1
  const action = next === 2 ? '停用' : '启用'
  await ElMessageBox.confirm(
    next === 2 ? `停用后 ${row.realName} 将无法登录，已建立的会话下次请求即被拒绝。` : `确认启用 ${row.realName}？`,
    action + '确认',
    { type: 'warning' }
  )
  await toggleUserStatus(row.id, next)
  ElMessage.success(action + '成功')
  await reload()
}

async function resetPwd(row: UserRow) {
  await ElMessageBox.confirm(
    `将为 ${row.realName} 生成新的初始密码，并要求其登录后立即修改。`,
    '重置密码',
    { type: 'warning' }
  )
  pwdResult.value = await resetUserPassword(row.id)
  pwdVisible.value = true
  await reload()
}

onMounted(async () => {
  const [deptList, roleList] = await Promise.all([dictItems('dept'), listRoles()])
  depts.value = deptList
  roles.value = roleList
  await reload(1)
})
</script>

<style scoped>
.user-page {
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

.role-tag {
  margin-right: 4px;
}

.pwd-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-left: 6px;
  border-radius: 50%;
  background: #e6a23c;
  vertical-align: middle;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.role-hint {
  margin: 0 0 10px;
}

.role-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pwd-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 14px;
  background: #f5f7fa;
  border-radius: 4px;
}

.pwd-label {
  color: #4b5563;
  font-size: 13px;
}

.pwd-value {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #1f4e79;
}
</style>

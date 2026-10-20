<template>
  <div class="role-page">
    <aside class="page-card role-side">
      <div class="side-head">
        <h2 class="page-title">角色</h2>
        <el-button v-if="store.hasPerm('m11:role:create')" link type="primary" :icon="Plus" @click="openCreate">
          新增
        </el-button>
      </div>
      <div
        v-for="role in roles"
        :key="role.id"
        class="role-item"
        :class="{ active: selected?.id === role.id }"
        @click="selectRole(role)"
      >
        <div class="role-item-main">
          <span class="role-name">{{ role.roleName }}</span>
          <el-tag v-if="role.isSystem === 1" size="small" type="info">内置</el-tag>
          <el-tag v-if="role.status === 2" size="small" type="danger">停用</el-tag>
        </div>
        <div class="role-item-sub">
          <code>{{ role.roleCode }}</code>
          <span>{{ role.userCount }} 人 · {{ role.permCount }} 权限点</span>
        </div>
      </div>
    </aside>

    <section class="page-card role-main">
      <template v-if="selected">
        <header class="main-head">
          <div>
            <h2 class="page-title">{{ selected.roleName }}</h2>
            <p class="text-sub">
              M11-2-5 角色维护 · M11-3-2 角色授权 · M11-3-3 数据范围授权
            </p>
          </div>
          <div class="head-actions">
            <el-tag v-if="selected.dataScopeAll === 1" type="warning" effect="plain">数据范围全量</el-tag>
            <el-button v-if="store.hasPerm('m11:role:update')" @click="openEdit">编辑角色</el-button>
          </div>
        </header>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="功能权限" name="perm">
            <div class="perm-toolbar">
              <span class="text-sub">按 M1 至 M12 模块分组，动作级权限点，勾选后保存立即生效</span>
              <div>
                <el-button size="small" @click="checkAll(true)">全选</el-button>
                <el-button size="small" @click="checkAll(false)">清空</el-button>
                <el-button
                  v-if="store.hasPerm('m11:role:grant')"
                  size="small"
                  type="primary"
                  :loading="saving"
                  @click="savePerms"
                >
                  保存授权
                </el-button>
              </div>
            </div>

            <el-alert v-if="selected.dataScopeAll === 1" class="tip" type="info" :closable="false" show-icon
              title="系统管理员持有全量数据范围，其权限点仍按动作逐条授予，不因全量范围而豁免校验。" />

            <el-collapse v-model="openModules">
              <el-collapse-item v-for="group in permissionGroups" :key="group.moduleCode" :name="group.moduleCode">
                <template #title>
                  <div class="module-title">
                    <span class="module-code">{{ group.moduleCode }}</span>
                    <span>{{ group.moduleName }}</span>
                    <el-tag size="small" effect="plain" type="info">
                      {{ countChecked(group) }} / {{ group.permissions.length }}
                    </el-tag>
                  </div>
                </template>
                <el-checkbox-group v-model="checkedPermIds" class="perm-group">
                  <el-checkbox v-for="perm in group.permissions" :key="perm.id" :value="perm.id" :label="perm.id">
                    <span class="perm-name">{{ perm.permName }}</span>
                    <code class="perm-code">{{ perm.permCode }}</code>
                  </el-checkbox>
                </el-checkbox-group>
              </el-collapse-item>
            </el-collapse>
          </el-tab-pane>

          <el-tab-pane label="数据范围" name="scope">
            <el-form label-width="96px" class="scope-form">
              <el-form-item label="授权主体">
                <el-radio-group v-model="scopeSubjectType" @change="loadScope">
                  <el-radio :value="2">当前角色</el-radio>
                  <el-radio :value="1">指定用户</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="scopeSubjectType === 1" label="选择用户">
                <el-select v-model="scopeUserId" filterable placeholder="选择用户" style="width: 280px" @change="loadScope">
                  <el-option
                    v-for="user in userOptions"
                    :key="user.id"
                    :label="`${user.realName}（${user.username}）`"
                    :value="user.id"
                  />
                </el-select>
              </el-form-item>

              <el-alert class="tip" type="info" :closable="false" show-icon
                title="三个维度可组合，保存后该主体下次请求即按新范围过滤。范围为空表示看不到任何数据，不会退化为全量。" />

              <el-form-item label="仓库">
                <el-select v-model="scopeWarehouses" multiple filterable placeholder="不限仓库" style="width: 100%">
                  <el-option v-for="item in scopeOptions.warehouses" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="部门">
                <el-select v-model="scopeDepts" multiple filterable placeholder="不限部门" style="width: 100%">
                  <el-option v-for="item in scopeOptions.depts" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="归属对象">
                <el-select v-model="scopeOwners" multiple filterable placeholder="不限归属对象" style="width: 100%">
                  <el-option v-for="item in scopeOptions.owners" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button
                  v-if="store.hasPerm('m11:scope:grant')"
                  type="primary"
                  :loading="saving"
                  @click="saveScope"
                >
                  保存数据范围
                </el-button>
                <span class="text-sub scope-note">变更会写入操作日志，可在日志审计中查看修改前后值</span>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="请选择左侧角色" />
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="!!form.id && form.isSystem === 1"
            placeholder="小写字母开头，如 buyer_assistant" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createRole,
  getApproverOptions,
  getScopeOptions,
  grantDataScopes,
  grantRolePermissions,
  listDataScopes,
  listPermissions,
  listRolePermissions,
  listRoles,
  updateRole,
  type ApproverOptions,
  type PermissionGroup,
  type RoleRow,
  type ScopeOptions
} from '@/api/m11'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const roles = ref<RoleRow[]>([])
const selected = ref<RoleRow | null>(null)
const permissionGroups = ref<PermissionGroup[]>([])
const checkedPermIds = ref<number[]>([])
const openModules = ref<string[]>(['M11'])
const activeTab = ref('perm')
const saving = ref(false)

const scopeOptions = ref<ScopeOptions>({ warehouses: [], depts: [], owners: [] })
const approverOptions = ref<ApproverOptions>({ roles: [], users: [] })
const scopeSubjectType = ref<2 | 1>(2)
const scopeUserId = ref<number>()
const scopeWarehouses = ref<string[]>([])
const scopeDepts = ref<string[]>([])
const scopeOwners = ref<string[]>([])

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{
  id?: number
  roleCode: string
  roleName: string
  status: number
  remark: string
  isSystem?: number
}>({ roleCode: '', roleName: '', status: 1, remark: '' })

const rules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{1,49}$/, message: '小写字母开头，2 至 50 位小写字母数字下划线', trigger: 'blur' }
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const userOptions = computed(() => approverOptions.value.users)

function countChecked(group: PermissionGroup) {
  const ids = new Set(group.permissions.map((p) => p.id))
  return checkedPermIds.value.filter((id) => ids.has(id)).length
}

async function loadRoles(keepId?: number) {
  roles.value = await listRoles()
  const target = roles.value.find((r) => r.id === (keepId ?? selected.value?.id)) || roles.value[0]
  if (target) {
    await selectRole(target)
  }
}

async function selectRole(role: RoleRow) {
  selected.value = role
  activeTab.value = 'perm'
  const [permIds] = await Promise.all([listRolePermissions(role.id)])
  checkedPermIds.value = permIds
  scopeSubjectType.value = 2
  await loadScope()
}

async function loadScope() {
  if (!selected.value) {
    return
  }
  const subjectId = scopeSubjectType.value === 2 ? selected.value.id : scopeUserId.value
  if (!subjectId) {
    scopeWarehouses.value = []
    scopeDepts.value = []
    scopeOwners.value = []
    return
  }
  const rows = await listDataScopes(scopeSubjectType.value, subjectId)
  scopeWarehouses.value = rows.filter((r) => r.scopeDimension === 1).map((r) => r.scopeValue)
  scopeDepts.value = rows.filter((r) => r.scopeDimension === 2).map((r) => r.scopeValue)
  scopeOwners.value = rows.filter((r) => r.scopeDimension === 3).map((r) => r.scopeValue)
}

function checkAll(checked: boolean) {
  checkedPermIds.value = checked
    ? permissionGroups.value.flatMap((g) => g.permissions.map((p) => p.id))
    : []
}

async function savePerms() {
  if (!selected.value) {
    return
  }
  if (!checkedPermIds.value.length) {
    ElMessage.warning('至少勾选一个权限点')
    return
  }
  saving.value = true
  try {
    await grantRolePermissions(selected.value.id, checkedPermIds.value)
    ElMessage.success('授权已保存，持有该角色的用户下次请求即按新权限校验')
    await loadRoles(selected.value.id)
  } finally {
    saving.value = false
  }
}

async function saveScope() {
  if (!selected.value) {
    return
  }
  const subjectId = scopeSubjectType.value === 2 ? selected.value.id : scopeUserId.value
  if (!subjectId) {
    ElMessage.warning('请选择用户')
    return
  }
  const items = [
    ...scopeWarehouses.value.map((value) => ({ scopeDimension: 1, scopeValue: value })),
    ...scopeDepts.value.map((value) => ({ scopeDimension: 2, scopeValue: value })),
    ...scopeOwners.value.map((value) => ({ scopeDimension: 3, scopeValue: value }))
  ]
  saving.value = true
  try {
    await grantDataScopes({ subjectType: scopeSubjectType.value, subjectId, items })
    ElMessage.success('数据范围已保存')
    await loadScope()
  } finally {
    saving.value = false
  }
}

function openCreate() {
  form.id = undefined
  form.roleCode = ''
  form.roleName = ''
  form.status = 1
  form.remark = ''
  form.isSystem = 0
  dialogVisible.value = true
}

function openEdit() {
  if (!selected.value) {
    return
  }
  form.id = selected.value.id
  form.roleCode = selected.value.roleCode
  form.roleName = selected.value.roleName
  form.status = selected.value.status
  form.remark = selected.value.remark || ''
  form.isSystem = selected.value.isSystem
  dialogVisible.value = true
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
      roleCode: form.roleCode,
      roleName: form.roleName,
      status: form.status,
      remark: form.remark
    }
    if (form.id) {
      await updateRole(form.id, payload)
      ElMessage.success('角色已更新')
      await loadRoles(form.id)
    } else {
      const id = await createRole(payload)
      ElMessage.success('角色已创建，请继续为其勾选权限点')
      await loadRoles(id)
    }
    dialogVisible.value = false
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const [groups, options, approvers] = await Promise.all([
    listPermissions(),
    getScopeOptions(),
    getApproverOptions()
  ])
  permissionGroups.value = groups
  scopeOptions.value = options
  approverOptions.value = approvers
  await loadRoles()
})
</script>

<style scoped>
.role-page {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  align-items: start;
}

.role-side {
  padding: 16px 12px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 6px 10px;
}

.role-item {
  padding: 10px 12px;
  border-radius: 4px;
  cursor: pointer;
  border-left: 3px solid transparent;
}

.role-item:hover {
  background: #f5f7fa;
}

.role-item.active {
  background: #eef4fb;
  border-left-color: #1f4e79;
}

.role-item-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.role-name {
  font-size: 14px;
  color: #1f2937;
  font-weight: 500;
}

.role-item-sub {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: #8a94a6;
}

.role-item-sub code {
  color: #6b7280;
}

.role-main {
  padding: 18px 22px;
  min-height: 520px;
}

.main-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
}

.main-head .text-sub {
  margin: 6px 0 0;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.perm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.tip {
  margin-bottom: 12px;
}

.module-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.module-code {
  display: inline-block;
  min-width: 40px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #eef4fb;
  color: #1f4e79;
  font-size: 12px;
  text-align: center;
  font-weight: 600;
}

.perm-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
}

.perm-name {
  margin-right: 8px;
}

.perm-code {
  color: #98a2b3;
  font-size: 12px;
}

.scope-form {
  max-width: 720px;
}

.scope-note {
  margin-left: 12px;
}
</style>

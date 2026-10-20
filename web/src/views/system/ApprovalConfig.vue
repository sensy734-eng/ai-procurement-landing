<template>
  <div class="page-card approval-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">审批配置</h2>
        <p class="text-sub">
          M11-5-1 审批流程配置 · M11-5-2 审批人指定，节点顺序固定，不做金额分支与发起人自选
        </p>
      </div>
      <div class="actions">
        <el-radio-group v-model="billType" @change="load">
          <el-radio-button :value="1">采购申请</el-radio-button>
          <el-radio-button :value="2">采购订单</el-radio-button>
        </el-radio-group>
        <el-button :icon="Plus" @click="addNode">添加节点</el-button>
        <el-button
          v-if="store.hasPerm('m11:approval_node:update')"
          type="primary"
          :loading="saving"
          @click="save"
        >
          保存配置
        </el-button>
      </div>
    </header>

    <el-alert class="tip" type="info" :closable="false" show-icon
      title="或签：任一审批人通过即推进；会签：全部通过才推进，任一驳回则整单驳回。节点序号从 1 连续，保存时校验。" />

    <div v-loading="loading" class="node-list">
      <el-empty v-if="!nodes.length" description="尚未配置审批节点，点击右上角添加节点" />

      <div v-for="(node, index) in nodes" :key="index" class="node-card">
        <div class="node-head">
          <div class="node-seq">{{ index + 1 }}</div>
          <el-input v-model="node.nodeName" placeholder="节点名称，如采购主管审批" class="node-name" />
          <el-radio-group v-model="node.signType" size="small">
            <el-radio-button :value="1">或签</el-radio-button>
            <el-radio-button :value="2">会签</el-radio-button>
          </el-radio-group>
          <div class="node-ops">
            <el-button link :disabled="index === 0" @click="move(index, -1)">上移</el-button>
            <el-button link :disabled="index === nodes.length - 1" @click="move(index, 1)">下移</el-button>
            <el-button link type="danger" @click="removeNode(index)">删除</el-button>
          </div>
        </div>

        <div class="node-body">
          <div class="approvers">
            <el-tag
              v-for="(approver, ai) in node.approvers"
              :key="ai"
              closable
              :type="approver.approverType === 1 ? 'primary' : 'success'"
              class="approver-tag"
              @close="removeApprover(index, ai)"
            >
              {{ approver.displayName || labelOf(approver) }}
            </el-tag>
            <span v-if="!node.approvers.length" class="text-sub">至少指定一个审批人</span>
          </div>
          <el-button link type="primary" :icon="Plus" @click="openApprover(index)">添加审批人</el-button>
        </div>

        <div v-if="node.signType === 2 && node.approvers.length < 2" class="node-warn">
          会签节点至少需要两个审批人，否则保存会被拒绝
        </div>
      </div>
    </div>

    <el-dialog v-model="approverVisible" title="添加审批人" width="440px">
      <el-form label-width="88px">
        <el-form-item label="指定方式">
          <el-radio-group v-model="approverForm.approverType">
            <el-radio :value="1">按角色</el-radio>
            <el-radio :value="2">按用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="approverForm.approverType === 1" label="角色">
          <el-select v-model="approverForm.roleId" placeholder="选择角色" style="width: 100%">
            <el-option v-for="role in options.roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="用户">
          <el-select v-model="approverForm.userId" filterable placeholder="选择用户" style="width: 100%">
            <el-option
              v-for="user in options.users"
              :key="user.id"
              :label="`${user.realName}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approverVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmApprover">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getApproverOptions,
  listApprovalFlow,
  saveApprovalFlow,
  type ApproverItem,
  type ApproverOptions,
  type ApprovalNode
} from '@/api/m11'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const billType = ref(1)
const nodes = ref<ApprovalNode[]>([])
const options = ref<ApproverOptions>({ roles: [], users: [] })
const loading = ref(false)
const saving = ref(false)

const approverVisible = ref(false)
const approverNodeIndex = ref(0)
const approverForm = reactive<{ approverType: number; roleId?: number; userId?: number }>({
  approverType: 1
})

function labelOf(approver: ApproverItem) {
  if (approver.approverType === 1) {
    const role = options.value.roles.find((r) => r.id === approver.roleId)
    return role ? `角色：${role.name}` : '角色'
  }
  const user = options.value.users.find((u) => u.id === approver.userId)
  return user ? `用户：${user.realName}` : '用户'
}

async function load() {
  loading.value = true
  try {
    nodes.value = await listApprovalFlow(billType.value)
  } finally {
    loading.value = false
  }
}

function addNode() {
  nodes.value.push({
    nodeSeq: nodes.value.length + 1,
    nodeName: '第 ' + (nodes.value.length + 1) + ' 节点',
    signType: 1,
    approvers: []
  })
}

async function removeNode(index: number) {
  await ElMessageBox.confirm('删除该节点后，其余节点顺序将前移，确认删除？', '删除节点', { type: 'warning' })
  nodes.value.splice(index, 1)
  renumber()
}

function move(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= nodes.value.length) {
    return
  }
  const [item] = nodes.value.splice(index, 1)
  nodes.value.splice(target, 0, item)
  renumber()
}

function renumber() {
  nodes.value.forEach((node, i) => {
    node.nodeSeq = i + 1
  })
}

function openApprover(index: number) {
  approverNodeIndex.value = index
  approverForm.approverType = 1
  approverForm.roleId = undefined
  approverForm.userId = undefined
  approverVisible.value = true
}

function confirmApprover() {
  const node = nodes.value[approverNodeIndex.value]
  if (!node) {
    return
  }
  if (approverForm.approverType === 1 && !approverForm.roleId) {
    ElMessage.warning('请选择角色')
    return
  }
  if (approverForm.approverType === 2 && !approverForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  const exists = node.approvers.some((a) =>
    approverForm.approverType === 1 ? a.roleId === approverForm.roleId : a.userId === approverForm.userId
  )
  if (exists) {
    ElMessage.warning('该审批人已在本节点中')
    return
  }
  node.approvers.push({
    approverType: approverForm.approverType,
    roleId: approverForm.approverType === 1 ? approverForm.roleId : undefined,
    userId: approverForm.approverType === 2 ? approverForm.userId : undefined
  })
  approverVisible.value = false
}

function removeApprover(nodeIndex: number, approverIndex: number) {
  nodes.value[nodeIndex].approvers.splice(approverIndex, 1)
}

async function save() {
  const emptyNode = nodes.value.find((node) => !node.nodeName.trim())
  if (emptyNode) {
    ElMessage.warning('节点名称不能为空')
    return
  }
  const noApprover = nodes.value.find((node) => !node.approvers.length)
  if (noApprover) {
    ElMessage.warning(`节点「${noApprover.nodeName}」没有审批人`)
    return
  }
  const badSign = nodes.value.find((node) => node.signType === 2 && node.approvers.length < 2)
  if (badSign) {
    ElMessage.warning(`会签节点「${badSign.nodeName}」至少需要两个审批人`)
    return
  }
  saving.value = true
  try {
    renumber()
    const payload = nodes.value.map((node) => ({
      nodeSeq: node.nodeSeq,
      nodeName: node.nodeName.trim(),
      signType: node.signType,
      approvers: node.approvers.map((a) => ({
        approverType: a.approverType,
        roleId: a.roleId,
        userId: a.userId
      }))
    }))
    await saveApprovalFlow(billType.value, payload)
    ElMessage.success('审批配置已保存')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  options.value = await getApproverOptions()
  await load()
})
</script>

<style scoped>
.approval-page {
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

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tip {
  margin-bottom: 16px;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.node-card {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #ffffff;
}

.node-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px dashed #ebeef5;
}

.node-seq {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #1f4e79;
  color: #ffffff;
  font-size: 13px;
  flex-shrink: 0;
}

.node-name {
  max-width: 280px;
}

.node-ops {
  margin-left: auto;
}

.node-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
}

.approvers {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.node-warn {
  padding: 0 16px 12px;
  color: #e6a23c;
  font-size: 12px;
}
</style>

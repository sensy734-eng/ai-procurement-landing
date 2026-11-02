<template>
  <div class="page-card param-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">参数配置</h2>
        <p class="text-sub">
          M11-4-1 价格偏离阈值 · M11-4-2 库存预警下限 · M11-4-3 单据编号规则
        </p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </header>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="参数口径固化于此，业务模块只读取不解释。修改后即时生效，不需要重启；每次变更写入操作日志。"
    />

    <el-tabs v-model="activeGroup" @tab-change="load">
      <el-tab-pane label="价格偏离阈值" name="price" />
      <el-tab-pane label="库存预警下限" name="stock" />
      <el-tab-pane label="单据编号规则" name="bill_no" />
    </el-tabs>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="paramName" label="参数名称" width="180" />
      <el-table-column prop="paramCode" label="参数编码" width="240">
        <template #default="{ row }"><code class="code">{{ row.paramCode }}</code></template>
      </el-table-column>
      <el-table-column label="当前值" width="240">
        <template #default="{ row }"><span class="value">{{ row.paramValue }}</span></template>
      </el-table-column>
      <el-table-column prop="paramType" label="类型" width="90" align="center" />
      <el-table-column prop="remark" label="口径说明" min-width="260" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" width="165">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="store.hasPerm('m11:param:update') && row.editable === 1"
            link
            type="primary"
            @click="openEdit(row)"
          >
            修改
          </el-button>
          <span v-else class="text-sub">只读</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="'修改 ' + editing.paramName" width="460px">
      <el-form label-width="88px">
        <el-form-item label="参数编码">
          <code class="code">{{ editing.paramCode }}</code>
        </el-form-item>
        <el-form-item label="口径说明">
          <span class="text-sub">{{ editing.remark }}</span>
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="editingValue" :placeholder="placeholderOf(editing)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listParams, updateParam, type ParamRow } from '@/api/m11'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const activeGroup = ref('price')
const rows = ref<ParamRow[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editing = ref<ParamRow>({} as ParamRow)
const editingValue = ref('')

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function placeholderOf(row: ParamRow) {
  if (row.paramType === 'decimal') {
    return '0 至 1 之间的小数，如 0.05'
  }
  if (row.paramType === 'int') {
    return '整数'
  }
  return '如 CG-SQ-{yyyyMMdd}-{seq:4}'
}

async function load() {
  loading.value = true
  try {
    rows.value = await listParams(activeGroup.value)
  } finally {
    loading.value = false
  }
}

function openEdit(row: ParamRow) {
  editing.value = row
  editingValue.value = row.paramValue
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    await updateParam(editing.value.paramCode, editingValue.value)
    ElMessage.success('参数已保存并即时生效')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.param-page {
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

.tip {
  margin-bottom: 12px;
}

.code {
  color: #4b5563;
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
}

.value {
  font-weight: 600;
  color: #1f4e79;
}
</style>

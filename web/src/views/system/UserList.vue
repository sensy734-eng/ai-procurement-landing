<template>
  <div class="page-card user-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="text-sub">M11-2-1 用户列表与检索，按部门数据范围过滤</p>
      </div>
      <div class="actions">
        <el-input
          v-model="keyword"
          placeholder="账号或姓名"
          clearable
          style="width: 220px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
        <el-button type="primary" :icon="Search" @click="reload(1)">查询</el-button>
        <el-button :icon="Refresh" @click="reload(pageNo)">刷新</el-button>
      </div>
    </header>

    <el-alert
      class="scope-alert"
      type="info"
      :closable="false"
      show-icon
      :title="`当前数据范围：${store.scopeText}。范围条件由后端注入查询，前端不参与过滤。`"
    />

    <el-table v-loading="loading" :data="rows" border stripe size="default">
      <el-table-column prop="username" label="账号" width="130" />
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column prop="deptName" label="部门" width="140" />
      <el-table-column prop="phone" label="手机" width="140">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="180">
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="密码" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.pwdNeedChange === 1" type="warning" size="small">待修改</el-tag>
          <span v-else class="text-sub">正常</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录" width="170">
        <template #default="{ row }">{{ formatTime(row.lastLoginTime) }}</template>
      </el-table-column>
    </el-table>

    <footer class="pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="reload"
        @size-change="reload(1)"
      />
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageUsers, type UserRow } from '@/api/system'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const loading = ref(false)
const rows = ref<UserRow[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const keyword = ref('')

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

async function reload(page = pageNo.value) {
  loading.value = true
  try {
    const result = await pageUsers({
      pageNo: page,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = result.records
    total.value = result.total
    pageNo.value = result.pageNo
  } finally {
    loading.value = false
  }
}

onMounted(() => reload(1))
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
  margin-bottom: 16px;
}

.toolbar .text-sub {
  margin: 6px 0 0;
}

.actions {
  display: flex;
  gap: 10px;
}

.scope-alert {
  margin-bottom: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

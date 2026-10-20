<template>
  <div class="home">
    <section class="welcome">
      <div>
        <h2>{{ greeting }}，{{ store.user?.realName || store.user?.username }}</h2>
        <p class="text-sub">
          {{ store.roleNames.join('、') }} · {{ store.user?.deptName || '未分配部门' }} ·
          数据范围 {{ store.scopeText }}
        </p>
      </div>
      <el-button v-if="store.hasPerm('m11:user:list')" type="primary" @click="router.push('/system/users')">
        用户管理
      </el-button>
    </section>

    <section class="stat-grid">
      <div v-for="card in stats" :key="card.label" class="stat-card page-card">
        <span class="stat-label">{{ card.label }}</span>
        <span class="stat-value">{{ card.value }}</span>
        <span class="stat-hint">{{ card.hint }}</span>
      </div>
    </section>

    <section class="panel-grid">
      <div class="page-card panel">
        <h3>本轮已交付</h3>
        <ul class="check-list">
          <li><el-icon><CircleCheck /></el-icon>登录、会话建立与超时续期（M11-1-1、M11-1-2）</li>
          <li><el-icon><CircleCheck /></el-icon>退出与修改密码（M11-1-3、M11-1-4）</li>
          <li><el-icon><CircleCheck /></el-icon>动作级权限拦截，无权限返回 403 与 NoPermission</li>
          <li><el-icon><CircleCheck /></el-icon>数据范围解析与查询条件注入（M11-3-3）</li>
          <li><el-icon><CircleCheck /></el-icon>菜单按权限点渲染，用户列表带部门范围过滤（M11-2-1）</li>
        </ul>
      </div>

      <div class="page-card panel">
        <h3>下一步</h3>
        <p class="text-sub">
          轮次 2 交付 M11 平台与权限管理：用户新增与停用、重置密码、角色授权、
          权限点树、数据范围配置、会话管理、操作日志、审批流程配置。
        </p>
        <el-divider />
        <h3>本机环境</h3>
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="后端">Spring Boot 3.5.16 / JDK 17</el-descriptions-item>
          <el-descriptions-item label="数据库">procurement_db，49 张表</el-descriptions-item>
          <el-descriptions-item label="会话方式">落库会话，默认 8 小时，滑动续期</el-descriptions-item>
        </el-descriptions>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const router = useRouter()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const stats = computed(() => [
  { label: '当前角色', value: store.roleNames.join('、') || '-', hint: `${store.roles.length} 个角色` },
  { label: '权限点', value: `${store.permissions.length}`, hint: '动作级授权，按接口校验' },
  { label: '数据范围', value: store.scopeText, hint: '后端解析，前端仅展示' },
  { label: '所在部门', value: store.user?.deptName || '-', hint: `用户编号 ${store.user?.id ?? '-'}` }
])
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: linear-gradient(120deg, #1f4e79, #2e6da4);
  border-radius: 6px;
  color: #ffffff;
}

.welcome h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.welcome .text-sub {
  color: rgba(255, 255, 255, 0.78);
  margin: 0;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 20px;
}

.stat-label {
  font-size: 13px;
  color: #6b7280;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #1f4e79;
  word-break: break-all;
}

.stat-hint {
  font-size: 12px;
  color: #9ca3af;
}

.panel-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.panel {
  padding: 20px 24px;
}

.panel h3 {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
}

.check-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13px;
  color: #374151;
}

.check-list li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.check-list .el-icon {
  color: #2e7d5b;
  font-size: 16px;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>

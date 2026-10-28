<template>
  <div class="home">
    <section class="welcome">
      <div>
        <h2>{{ greeting }}，{{ overview?.realName || store.user?.realName || store.user?.username }}</h2>
        <p class="welcome-sub">
          {{ (overview?.roleNames || store.roleNames).join('、') }} ·
          {{ store.user?.deptName || '未分配部门' }} · 数据范围 {{ overview?.scopeHint || store.scopeText }}
        </p>
      </div>
      <el-button v-if="store.hasPerm('m12:summary:purchase')" type="primary" plain
                 @click="router.push('/system/dashboard')">
        打开运营仪表盘
      </el-button>
    </section>

    <!-- 指标卡（M12-1-1）：只显示当前角色有权拿的指标，缺权限的指标项由后端不返回 -->
    <section class="metric-grid">
      <div v-for="metric in overview?.metrics || []" :key="metric.key" class="metric-card page-card">
        <span class="metric-label">{{ metric.label }}</span>
        <span class="metric-value">
          {{ metric.value }}<span class="metric-unit">{{ metric.unit }}</span>
        </span>
        <span class="metric-hint">{{ metric.hint }}</span>
      </div>
      <div v-if="!overview?.metrics?.length" class="metric-card page-card muted">暂无指标</div>
    </section>

    <!-- 维护入口聚合（M12-1-2）：五类入口按权限点过滤，无权限的整组不出现 -->
    <section class="entry-groups">
      <div v-for="group in overview?.groups || []" :key="group.key" class="group-card page-card">
        <header class="group-head">
          <el-icon class="group-icon"><component :is="iconOf(group.icon)" /></el-icon>
          <h3>{{ group.title }}</h3>
          <span class="group-count">{{ group.entries.length }} 个入口</span>
        </header>
        <ul class="entry-list">
          <li v-for="entry in group.entries" :key="entry.path" class="entry-item"
              @click="router.push(entry.path)">
            <div class="entry-main">
              <span class="entry-title">{{ entry.title }}</span>
              <span v-if="entry.count !== null" class="entry-count">
                {{ entry.count }} {{ entry.countLabel }}
              </span>
            </div>
            <p class="entry-desc">{{ entry.description }}</p>
            <el-icon class="entry-arrow"><ArrowRight /></el-icon>
          </li>
        </ul>
      </div>
    </section>

    <section class="footer-card page-card">
      <h3>关于入口</h3>
      <p class="text-sub">
        入口按当前账号的权限点动态显示（M12-1-1）：没有权限的入口既不在菜单里，也不在首页出现。
        例如物料、供应商、分类归采购员维护，系统管理员不持有这三类权限，
        所以管理员的「基础数据」只保留仓库与库位——这是权限矩阵的设计结果，不是页面缺项。
      </p>
      <el-divider />
      <div class="env-row">
        <span>后端 Spring Boot 3.5.16 / JDK 17</span>
        <span>数据库 procurement_db，49 张表</span>
        <span>会话落库，默认 8 小时滑动续期</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Collection,
  Document,
  Files,
  Reading,
  User
} from '@element-plus/icons-vue'
import { getOverview } from '@/api/m12'
import type { Overview } from '@/api/m12'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const router = useRouter()

const overview = ref<Overview | null>(null)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const ICONS: Record<string, unknown> = {
  Files,
  Collection,
  User,
  Reading,
  Document
}

function iconOf(name: string) {
  return ICONS[name] || Files
}

onMounted(async () => {
  try {
    overview.value = await getOverview()
  } catch {
    overview.value = null
  }
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.welcome-sub {
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
  font-size: 13px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
}

.metric-card.muted {
  color: #9ca3af;
  font-size: 13px;
}

.metric-label {
  font-size: 13px;
  color: var(--text-sub);
}

.metric-value {
  font-size: 23px;
  font-weight: 600;
  color: #1f4e79;
  font-variant-numeric: tabular-nums;
}

.metric-unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-sub);
  margin-left: 3px;
}

.metric-hint {
  font-size: 12px;
  color: #9ca3af;
}

/* 入口组用瀑布流排列：每组入口数不同，等高网格会让只有一条入口的组留出大片空白 */
.entry-groups {
  column-count: 3;
  column-gap: 14px;
}

.group-card {
  padding: 16px 18px;
  margin-bottom: 14px;
  break-inside: avoid;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.group-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.group-icon {
  color: #2e6da4;
  font-size: 17px;
}

.group-count {
  margin-left: auto;
  font-size: 12px;
  color: #9ca3af;
}

.entry-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.entry-item {
  position: relative;
  padding: 9px 26px 9px 11px;
  border: 1px solid #eef1f6;
  border-radius: 5px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.entry-item:hover {
  border-color: #c8d6e3;
  background: #f7fafc;
}

.entry-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.entry-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.entry-count {
  font-size: 12px;
  color: #2e6da4;
}

.entry-desc {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}

.entry-arrow {
  position: absolute;
  right: 9px;
  top: 50%;
  transform: translateY(-50%);
  color: #c0c6d0;
  font-size: 13px;
}

.footer-card {
  padding: 16px 20px;
}

.footer-card h3 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.footer-card .text-sub {
  margin: 0;
  line-height: 1.7;
}

.env-row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 12px;
  color: #9ca3af;
}

@media (max-width: 1280px) {
  .entry-groups {
    column-count: 2;
  }
}

@media (max-width: 900px) {
  .welcome {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .entry-groups {
    column-count: 1;
  }
}
</style>

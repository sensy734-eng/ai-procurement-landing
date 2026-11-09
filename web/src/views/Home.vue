<template>
  <div class="home">
    <section class="welcome">
      <div>
        <p class="welcome-label">存量系统 AI 改造演示</p>
        <h2>用 AI 查业务、审订单、起草需求</h2>
        <p class="welcome-description">在已有业务、权限和数据上接入 AI，体验受控查询、知识检索与保守降级。</p>
        <p class="welcome-sub">
          {{ overview?.realName || store.user?.realName || store.user?.username }} ·
          {{ (overview?.roleNames || store.roleNames).join('、') }} ·
          {{ store.user?.deptName || '未分配部门' }} · 数据范围 {{ overview?.scopeHint || store.scopeText }}
        </p>
      </div>
      <el-button v-if="store.hasPerm('m7:chat:query')" type="primary" plain
                 @click="router.push('/assistant')">
        开始 AI 对话
      </el-button>
    </section>

    <section v-if="store.hasPerm('m7:chat:query')" class="task-entry">
      <div class="section-head">
        <div>
          <h3>体验 AI 能力</h3>
          <p>选择场景预填问题，确认后发送；查询始终受当前账号权限约束</p>
        </div>
      </div>
      <div class="task-actions">
        <el-button v-if="['m9:data:query', 'm3:order:view', 'm3:order:list'].every(p => store.hasPerm(p))" class="task-button" :icon="Document" @click="router.push('/order?task=review')">
          <span class="task-copy"><strong>审查订单</strong><small>打开订单详情 → AI 订单审查</small></span>
        </el-button>
        <el-button v-if="['m2:request:create', 'm2:request_item:create', 'm1:material:list', 'm2:request:list'].every(p => store.hasPerm(p))" class="task-button" :icon="Files" @click="router.push('/request?task=draft')">
          <span class="task-copy"><strong>起草采购需求</strong><small>提取候选 → 人工确认 → 填入表单</small></span>
        </el-button>
        <el-button v-if="store.hasPerm('m9:data:query')" class="task-button" :icon="Search" @click="ask('订单 CG-DD-20261011-0001 现在什么状态')">
          <span class="task-copy"><strong>查订单</strong><small>自然语言 → 受控业务查询</small></span>
        </el-button>
        <el-button v-if="store.hasPerm('m9:data:query')" class="task-button" :icon="Files" @click="ask('内六角螺栓还有多少库存')">
          <span class="task-copy"><strong>查库存</strong><small>实体识别 → 仓库数据范围校验</small></span>
        </el-button>
        <el-button v-if="store.hasPerm('m8:kb:query')" class="task-button" :icon="Reading" @click="ask('采购申请提交后多久必须审批完')">
          <span class="task-copy"><strong>问制度</strong><small>检索依据 → 回答、参考或拒答</small></span>
        </el-button>
      </div>
    </section>

    <section v-if="store.hasPerm('m7:chat:query') && store.hasPerm('m7:session:list')" class="recent-sessions">
      <div class="section-head">
        <h3>最近 AI 会话</h3>
        <el-button link type="primary" @click="router.push('/assistant')">全部会话</el-button>
      </div>
      <div v-if="recentSessions.length" class="recent-list">
        <button v-for="session in recentSessions" :key="session.id" class="recent-item"
                @click="openSession(session.id)">
          <span>{{ session.title }}</span>
          <small>{{ session.messageCount }} 条 · {{ shortTime(session.lastTime) }}</small>
        </button>
      </div>
      <el-empty v-else description="暂无会话" :image-size="52" />
    </section>

    <section v-if="store.hasPerm('m10:document:list') || store.hasPerm('m9:audit:list')" class="validation-entry">
      <div class="section-head">
        <div>
          <h3>验证 AI 的依据与边界</h3>
          <p>检查检索片段、实际策略与查询审计；实验结果以真实运行记录为准</p>
        </div>
      </div>
      <div class="validation-actions">
        <el-button v-if="store.hasPerm('m10:document:list')" :icon="Reading" @click="router.push('/system/kb')">知识库与检索实验</el-button>
        <el-button v-if="store.hasPerm('m9:audit:list')" :icon="Document" @click="router.push('/assistant/audit')">受控查询审计</el-button>
      </div>
    </section>

    <details v-if="overview?.metrics?.length || overview?.groups?.length" class="business-context page-card">
      <summary>
        <strong>业务验证环境</strong>
        <span>展开订单、库存与业务入口，核对 AI 查询结果</span>
      </summary>
      <div class="business-content">
        <p class="text-sub">采购流程为 AI 改造提供数据、权限和事务约束。业务处理仍在对应页面完成。</p>
        <el-button v-if="store.hasPerm('m12:summary:purchase')" link type="primary" @click="router.push('/system/dashboard')">查看业务指标</el-button>
        <!-- 指标与维护入口均按当前账号权限过滤。 -->
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
      </div>
    </details>

    <section class="footer-card page-card">
      <div class="env-row">
        <span>采购 AI 工作台</span>
        <span>独立 AI 应用工程示例 · 全部业务数据与制度为虚构演示内容</span>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Collection,
  Document,
  Files,
  Reading,
  Search,
  User
} from '@element-plus/icons-vue'
import { getOverview } from '@/api/m12'
import type { Overview } from '@/api/m12'
import { listAiSessions } from '@/api/ai'
import type { AiSession } from '@/api/ai'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const router = useRouter()

const overview = ref<Overview | null>(null)
const recentSessions = ref<AiSession[]>([])

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
  const [overviewResult, sessionsResult] = await Promise.allSettled([
    getOverview(),
    store.hasPerm('m7:session:list') ? listAiSessions() : Promise.resolve([])
  ])
  overview.value = overviewResult.status === 'fulfilled' ? overviewResult.value : null
  recentSessions.value = sessionsResult.status === 'fulfilled' ? sessionsResult.value.slice(0, 4) : []
})

function ask(draft: string) {
  router.push({ path: '/assistant', query: { draft } })
}

function openSession(sessionId: number) {
  router.push({ path: '/assistant', query: { session: String(sessionId) } })
}

function shortTime(value: string) {
  return (value || '').replace('T', ' ').slice(5, 16)
}
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

.welcome-label {
  margin: 0 0 8px;
  color: #c5dff4;
  font-size: 12px;
  letter-spacing: 1px;
}

.welcome-description {
  margin: 8px 0 16px;
  font-size: 14px;
  line-height: 1.7;
}

.validation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.validation-actions .el-button {
  margin: 0;
}

.business-context {
  padding: 16px 18px;
}

.business-context summary {
  cursor: pointer;
  line-height: 1.7;
}

.business-context summary span {
  display: inline-block;
  margin-left: 12px;
  color: var(--text-sub);
  font-size: 12px;
}

.business-content {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
  margin-top: 14px;
}

.business-content > p {
  margin: 0;
}

.business-content > .el-button {
  align-self: flex-start;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.task-entry,
.validation-entry,
.recent-sessions {
  padding: 14px 18px;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.section-head p {
  margin: 4px 0 0;
  color: var(--text-sub);
  font-size: 12px;
}

.task-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.task-button {
  justify-content: flex-start;
  min-height: 62px;
  margin: 0;
  white-space: normal;
  text-align: left;
}

.task-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.task-button strong {
  color: #1f2937;
  font-size: 13px;
}

.task-button small,
.recent-item small {
  color: var(--text-sub);
  font-size: 12px;
}

.recent-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.recent-item {
  display: flex;
  min-width: 0;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 9px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #ffffff;
  color: #303133;
  text-align: left;
  cursor: pointer;
}

.recent-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  .task-actions,
  .recent-list {
    grid-template-columns: 1fr;
  }
}
</style>

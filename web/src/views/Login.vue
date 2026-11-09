<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="brand-inner">
        <div class="logo-row">
          <div class="logo-mark">AI</div>
          <span class="logo-text">Procurement AI Workbench</span>
        </div>
        <h1>采购 AI 工作台</h1>
        <p class="sub">
          存量业务系统的 AI 改造实践。以采购场景展示自然语言查询、知识问答与可验证的落地边界。
        </p>
        <ul class="features">
          <li>
            <el-icon><DocumentChecked /></el-icon>
            <div>
              <strong>自然语言查询</strong>
              <span>识别意图与实体，沿用业务权限获取数据</span>
            </div>
          </li>
          <li>
            <el-icon><DataLine /></el-icon>
            <div>
              <strong>有依据的知识问答</strong>
              <span>检索制度原文，核对引用、数值与单位</span>
            </div>
          </li>
          <li>
            <el-icon><ChatDotRound /></el-icon>
            <div>
              <strong>可验证的 AI 边界</strong>
              <span>模型失败时保守降级，用检索实验与审计验证行为</span>
            </div>
          </li>
        </ul>
      </div>
      <div class="panel-foot">独立 AI 应用工程示例 · 采购业务为演示载体 · 数据与制度均为虚构</div>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <h2>账号登录</h2>
        <p class="tip">选择角色，体验 AI 在不同数据权限下的查询边界</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>

        <el-divider>演示账号，密码均为 123456</el-divider>
        <div class="demo-accounts">
          <div v-for="acc in demoAccounts" :key="acc.username" class="demo-item" @click="fill(acc.username)">
            <span class="demo-role">{{ acc.role }}</span>
            <span class="demo-user">{{ acc.username }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const demoAccounts = [
  { role: 'AI 任务演示', username: 'ai_buyer' },
  { role: '系统管理员', username: 'admin' },
  { role: '采购员', username: 'caigou01' },
  { role: '采购主管', username: 'zhuguan01' },
  { role: '仓管员', username: 'cangku01' }
]

function fill(username: string) {
  form.username = username
  form.password = '123456'
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await store.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎，${store.user?.realName ?? store.user?.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    await router.replace(redirect)
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 56%;
  padding: 56px 64px 32px;
  color: #ffffff;
  background:
    radial-gradient(circle at 82% 12%, rgba(83, 141, 196, 0.45), transparent 42%),
    linear-gradient(150deg, #12314f 0%, #1f4e79 58%, #2e6da4 100%);
  overflow: hidden;
}

.brand-panel::after {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.85), transparent 78%);
  pointer-events: none;
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 520px;
}

.logo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 56px;
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  font-size: 20px;
  font-weight: 700;
}

.logo-text {
  letter-spacing: 1px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.82);
}

h1 {
  margin: 0 0 18px;
  font-size: 34px;
  line-height: 1.42;
  font-weight: 600;
  letter-spacing: 1px;
}

.sub {
  margin: 0 0 44px;
  max-width: 460px;
  font-size: 14px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.76);
}

.features {
  display: flex;
  flex-direction: column;
  gap: 22px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.features li {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.features .el-icon {
  margin-top: 3px;
  font-size: 18px;
  color: #9dc3e6;
}

.features div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.features strong {
  font-size: 14px;
  font-weight: 600;
}

.features span {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.66);
}

.panel-foot {
  position: relative;
  z-index: 1;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.form-panel {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 40px 24px;
}

.form-card {
  width: 100%;
  max-width: 372px;
}

.form-card h2 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
}

.tip {
  margin: 0 0 28px;
  font-size: 13px;
  color: #6b7280;
}

.submit {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  font-size: 15px;
  letter-spacing: 4px;
}

.demo-accounts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.form-card :deep(.el-divider__text) {
  white-space: nowrap;
  font-size: 12px;
  background: #f5f7fa;
}

.demo-item {
  display: flex;
  justify-content: space-between;
  padding: 9px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  background: #ffffff;
}

.demo-item:hover {
  border-color: #1f4e79;
  box-shadow: 0 2px 8px rgba(31, 78, 121, 0.12);
}

.demo-role {
  font-size: 12px;
  color: #6b7280;
}

.demo-user {
  font-size: 12px;
  font-family: Consolas, monospace;
  color: #1f4e79;
}

@media (max-width: 960px) {
  .brand-panel {
    display: none;
  }
}
</style>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="brand" :class="{ compact: collapsed }">
        <div class="brand-mark">AI</div>
        <span v-show="!collapsed" class="brand-name">采购 AI 工作台</span>
      </div>

      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          :collapse-transition="false"
          router
          unique-opened
          background-color="#12314f"
          text-color="#c7d5e4"
          active-text-color="#ffffff"
        >
          <template v-for="item in visibleMenus" :key="item.title">
            <el-sub-menu v-if="item.children && item.children.length" :index="item.title">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path!">
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path!">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <span class="page-title">{{ route.meta.title || '工作台' }}</span>
        </div>

        <div class="header-right">
          <el-tag size="small" effect="plain" type="info" class="scope-tag">
            数据范围：{{ store.scopeText }}
          </el-tag>
          <el-tag v-for="name in store.roleNames" :key="name" size="small" effect="light">
            {{ name }}
          </el-tag>

          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-entry">
              <el-avatar :size="28" class="avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ store.user?.realName || store.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ store.user?.username }} · {{ store.user?.deptName || '未分配部门' }}
                </el-dropdown-item>
                <el-dropdown-item divided command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog
      v-model="pwdVisible"
      :title="pwdForced ? '首次登录需修改密码' : '修改密码'"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="!pwdForced"
      :show-close="!pwdForced"
    >
      <el-alert
        v-if="pwdForced"
        class="pwd-tip"
        type="warning"
        :closable="false"
        show-icon
        title="当前密码为管理员设置的初始密码，修改后才能继续使用系统。"
      />
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="!pwdForced" @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { menuTree, type MenuNode } from '@/router/menu'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const collapsed = ref(false)
const pwdVisible = ref(false)
const pwdForced = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 30, message: '长度需在 8 到 30 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const avatarText = computed(() => {
  const name = store.user?.realName || store.user?.username || ''
  return name.slice(-1)
})

function filterMenu(nodes: MenuNode[]): MenuNode[] {
  return nodes
    .map((node) => {
      if (node.children) {
        return { ...node, children: filterMenu(node.children) }
      }
      return node
    })
    .filter((node) => {
      if (node.children) {
        return node.children.length > 0
      }
      return store.hasPerm(node.perm)
    })
}

const visibleMenus = computed(() => filterMenu(menuTree))

async function handleCommand(command: string) {
  if (command === 'password') {
    pwdForced.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdVisible.value = true
    return
  }
  if (command === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    await store.logout()
    await router.replace('/login')
    ElMessage.success('已退出登录')
  }
}

async function submitPassword() {
  if (!pwdFormRef.value) {
    return
  }
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  pwdLoading.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    store.pwdNeedChange = false
    pwdForced.value = false
    pwdVisible.value = false
    ElMessage.success('密码已修改，其它会话已下线')
  } finally {
    pwdLoading.value = false
  }
}

/**
 * M11-2-4 首登必改：管理员重置密码后，用户进入系统即强制修改，
 * 未修改前弹窗不可关闭、不可点遮罩退出。
 */
onMounted(() => {
  if (store.pwdNeedChange) {
    pwdForced.value = true
    pwdVisible.value = true
  }
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  display: flex;
  flex-direction: column;
  background: #12314f;
  transition: width 0.2s;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  white-space: nowrap;
}

.brand.compact {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: #2e6da4;
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
  flex-shrink: 0;
}

.brand-name {
  color: #ffffff;
  font-size: 15px;
  letter-spacing: 1px;
}

.menu-scroll {
  flex: 1;
}

.menu-scroll :deep(.el-menu) {
  border-right: none;
}

.menu-scroll :deep(.el-menu-item.is-active) {
  background: #1f4e79 !important;
  border-right: 3px solid #7fb3dd;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-btn {
  font-size: 18px;
  color: #6b7280;
  cursor: pointer;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.scope-tag {
  margin-right: 4px;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
}

.user-entry:hover {
  background: #f5f7fa;
}

.avatar {
  background: #1f4e79;
  color: #ffffff;
  font-size: 13px;
}

.user-name {
  font-size: 13px;
  color: #1f2937;
}

.main {
  padding: 20px;
  background: #f5f7fa;
  overflow-y: auto;
}

.pwd-tip {
  margin-bottom: 14px;
}
</style>

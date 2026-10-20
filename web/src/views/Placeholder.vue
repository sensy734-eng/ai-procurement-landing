<template>
  <div class="page-card placeholder">
    <el-empty :description="`${title} 将在后续轮次交付`">
      <template #image>
        <el-icon class="empty-icon"><Tools /></el-icon>
      </template>
      <p class="hint">
        当前工程骨架已打通登录、权限、数据范围与菜单渲染，
        本页面对应的后端接口与前端界面按开发路线分批实现。
      </p>
      <p class="text-sub">页面编号与功能点对应关系见功能模块清单，开发顺序见 DEV_ROADMAP.md</p>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Tools } from '@element-plus/icons-vue'

const route = useRoute()
const title = computed(() => (route.meta.title as string) || '该功能')
</script>

<style scoped>
.placeholder {
  display: grid;
  place-items: center;
  min-height: 460px;
  padding: 40px;
}

.empty-icon {
  font-size: 64px;
  color: #c8d6e3;
}

.hint {
  max-width: 460px;
  margin: 0 auto 8px;
  font-size: 13px;
  line-height: 1.9;
  color: #6b7280;
}
</style>

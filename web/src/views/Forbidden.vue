<template>
  <div class="page-card forbidden">
    <el-result icon="warning" title="403 无权限" sub-title="当前角色没有该页面的动作级权限点">
      <template #extra>
        <el-button type="primary" @click="router.replace('/home')">返回工作台</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.forbidden {
  display: grid;
  place-items: center;
  min-height: 460px;
}
</style>

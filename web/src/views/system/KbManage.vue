<template>
  <div class="page-card kb-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">知识库</h2>
        <p class="text-sub">
          文档上传解析 · 切块校对合并 · 双路检索测试台
        </p>
      </div>
    </header>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="知识库是智能助手的答案溯源来源：文档仅启用态参与召回；索引为派生数据，可随时全量重建；上传 pdf 暂不支持自动解析，请用正文补录粘贴。"
    />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="文档管理" name="docs" lazy>
        <KbDocPanel />
      </el-tab-pane>
      <el-tab-pane label="切块查看" name="chunks" lazy>
        <KbChunkPanel />
      </el-tab-pane>
      <el-tab-pane label="检索测试" name="test" lazy>
        <KbTestPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import KbDocPanel from './kb/KbDocPanel.vue'
import KbChunkPanel from './kb/KbChunkPanel.vue'
import KbTestPanel from './kb/KbTestPanel.vue'

const activeTab = ref('docs')
</script>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.tip {
  margin: 0;
}
</style>

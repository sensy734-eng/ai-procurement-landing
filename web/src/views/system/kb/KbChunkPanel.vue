<template>
  <div class="kb-panel">
    <header class="toolbar">
      <div class="filters">
        <span class="text-sub">文档</span>
        <el-select
          v-model="docId"
          placeholder="选择文档"
          filterable
          style="width: 320px"
          @change="loadChunks"
        >
          <el-option
            v-for="doc in docOptions"
            :key="doc.id"
            :label="`${doc.docName}（v${doc.version}，${doc.chunkCount} 块）`"
            :value="doc.id"
          />
        </el-select>
        <el-button :icon="Refresh" @click="loadDocs">刷新文档</el-button>
      </div>
      <div class="actions">
        <el-button
          v-if="store.hasPerm('m10:chunk:edit')"
          type="primary"
          :icon="Minus"
          :disabled="selected.length < 2"
          @click="mergeSelected"
        >
          合并选中切块（{{ selected.length }}）
        </el-button>
        <el-button
          v-if="store.hasPerm('m10:index:rebuild')"
          :icon="RefreshRight"
          :disabled="!docId"
          @click="rebuildDoc"
        >
          重建该文档索引
        </el-button>
      </div>
    </header>

    <el-alert class="tip" type="info" :closable="false" show-icon
      title="切块口径：目标块长 kb_chunk_size（默认 500 字），条款（第X条 / 3.2.1 / 一、 等）不被跨块切断；单条款超过 1.5 倍块长时按句界硬切，相邻片段重叠 kb_chunk_overlap（默认 50 字）。仅序号相邻的切块可合并。" />

    <el-table v-loading="loading" :data="rows" border stripe @selection-change="onSelectionChange">
      <el-table-column v-if="store.hasPerm('m10:chunk:edit')" type="selection" width="44" />
      <el-table-column prop="chunkIndex" label="序号" width="70" align="center" />
      <el-table-column prop="sectionNo" label="章节号" width="90" align="center" />
      <el-table-column prop="sectionTitle" label="章节名" width="160" show-overflow-tooltip />
      <el-table-column prop="charCount" label="字数" width="80" align="center" />
      <el-table-column label="向量" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.hasVector ? 'success' : 'info'" size="small">
            {{ row.hasVector ? '已建' : '未建' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="正文" min-width="420">
        <template #default="{ row }">
          <span class="chunk-text">{{ row.chunkText }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !docId" description="请先选择文档" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Minus, Refresh, RefreshRight } from '@element-plus/icons-vue'
import {
  listChunks,
  listDocuments,
  mergeChunks,
  rebuildDocIndex,
  type KbChunkRow,
  type KbDocumentRow
} from '@/api/m10'
import { useUserStore } from '@/stores/user'

const store = useUserStore()

const docOptions = ref<KbDocumentRow[]>([])
const docId = ref<number>()
const rows = ref<KbChunkRow[]>([])
const selected = ref<KbChunkRow[]>([])
const loading = ref(false)

async function loadDocs() {
  const page = await listDocuments({ pageNo: 1, pageSize: 100 })
  docOptions.value = page.records
}

async function loadChunks() {
  if (!docId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await listChunks(docId.value)
    selected.value = []
  } finally {
    loading.value = false
  }
}

function onSelectionChange(picked: KbChunkRow[]) {
  selected.value = picked
}

async function mergeSelected() {
  const ids = selected.value.map((row) => row.id)
  const sorted = [...ids].sort((a, b) => a - b)
  if (ids.join(',') !== sorted.join(',')) {
    ElMessage.warning('请按列表顺序勾选相邻切块')
    return
  }
  const indexes = selected.value.map((row) => row.chunkIndex).sort((a, b) => a - b)
  if (indexes[indexes.length - 1] - indexes[0] !== indexes.length - 1) {
    ElMessage.warning('仅序号相邻的切块可合并（条款边界保护）')
    return
  }
  await ElMessageBox.confirm(
    `合并 ${ids.length} 个相邻切块？合并后按句重新切分该文档的块序号并重建索引。`,
    '合并切块',
    { type: 'warning' }
  )
  const stat = await mergeChunks(docId.value!, ids)
  ElMessage.success(`已合并并重建索引：剩余 ${stat.chunkCount} 块、${stat.tokenCount} 个词条`)
  await loadChunks()
  await loadDocs()
}

async function rebuildDoc() {
  const stat = await rebuildDocIndex(docId.value!)
  ElMessage.success(`索引已重建：${stat.chunkCount} 个切块、${stat.tokenCount} 个词条，耗时 ${stat.elapsedMs}ms`)
  await loadChunks()
}

onMounted(async () => {
  try {
    await loadDocs()
    if (docOptions.value.length > 0) {
      docId.value = docOptions.value[0].id
      await loadChunks()
    }
  } catch (error) {
    ElMessage.error((error as Error)?.message || '加载失败')
  }
})
</script>

<style scoped>
.kb-panel {
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
.filters,
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tip {
  margin: 0;
}
.chunk-text {
  display: block;
  max-height: 96px;
  overflow-y: auto;
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>

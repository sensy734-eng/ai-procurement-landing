<template>
  <div class="bar-chart">
    <div v-if="!items.length" class="empty-hint">暂无数据</div>
    <svg v-else :viewBox="`0 0 460 ${height}`" class="bar-svg" role="img" :aria-label="ariaLabel">
      <g v-for="(item, index) in layout" :key="item.label + index">
        <text :x="0" :y="item.y + 4" class="bar-label">{{ item.label }}</text>
        <rect :x="labelWidth" :y="item.y - 6" :width="barArea" :height="12" rx="3" fill="#f0f3f7" />
        <rect :x="labelWidth" :y="item.y - 6" :width="item.width" :height="12" rx="3"
              :fill="item.color">
          <title>{{ item.label }}：{{ item.display }}</title>
        </rect>
        <text :x="460" :y="item.y + 4" text-anchor="end" class="bar-value">{{ item.display }}</text>
        <text v-if="item.sub" :x="labelWidth + item.width + 6" :y="item.y + 4" class="bar-sub">
          {{ item.sub }}
        </text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface BarItem {
  label: string
  value: number
  /** 右侧展示文本，缺省用千分位数字 */
  display?: string
  /** 条尾的补充说明，如占比 */
  sub?: string
  color?: string
}

const props = withDefaults(
  defineProps<{ items: BarItem[]; labelWidth?: number; rowHeight?: number; unit?: string }>(),
  { labelWidth: 132, rowHeight: 34, unit: '' }
)

const COLORS = ['#1f4e79', '#2e6da4', '#4a739c', '#7c9ab8', '#aec2d5']

const height = computed(() => Math.max(props.items.length * props.rowHeight + 10, 60))

const maxValue = computed(() => {
  const max = Math.max(...props.items.map((item) => Number(item.value) || 0), 0)
  return max > 0 ? max : 1
})

const barArea = computed(() => 460 - props.labelWidth - 78)

const layout = computed(() =>
  props.items.map((item, index) => {
    const value = Number(item.value) || 0
    return {
      label: item.label,
      y: 20 + index * props.rowHeight,
      width: Math.max((value / maxValue.value) * barArea.value, value > 0 ? 3 : 0),
      display: item.display ?? (value ? value.toLocaleString('zh-CN') : '0'),
      sub: item.sub,
      color: item.color || COLORS[index % COLORS.length]
    }
  })
)

const ariaLabel = computed(
  () => '条形图：' + props.items.map((item) => `${item.label} ${item.display ?? item.value}`).join('，')
)
</script>

<style scoped>
.bar-chart {
  width: 100%;
}

.bar-svg {
  width: 100%;
  height: auto;
  display: block;
}

.bar-label {
  font-size: 12px;
  fill: #374151;
}

.bar-value {
  font-size: 11px;
  fill: #1f2937;
  font-variant-numeric: tabular-nums;
}

.bar-sub {
  font-size: 10px;
  fill: #6b7280;
}

.empty-hint {
  padding: 60px 0;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
</style>

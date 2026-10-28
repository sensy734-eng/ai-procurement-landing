<template>
  <div class="pie-chart">
    <svg v-if="total > 0" viewBox="0 0 360 210" class="pie-svg" role="img" :aria-label="ariaLabel">
      <g>
        <path v-for="slice in slices" :key="slice.name" :d="slice.path" :fill="slice.color"
              stroke="#ffffff" stroke-width="1.5">
          <title>{{ slice.name }}：{{ slice.value }}（{{ slice.percent }}%）</title>
        </path>
        <circle :cx="cx" :cy="cy" :r="innerR" fill="#ffffff" />
        <text :x="cx" :y="cy - 4" text-anchor="middle" class="center-value">{{ total }}</text>
        <text :x="cx" :y="cy + 14" text-anchor="middle" class="center-label">合计</text>
      </g>
      <g>
        <g v-for="(item, index) in slices" :key="'legend-' + item.name"
           :transform="`translate(196, ${34 + index * 30})`">
          <rect width="10" height="10" rx="2" :fill="item.color" />
          <text x="18" y="9" class="legend-name">{{ item.name }}</text>
          <text x="18" y="24" class="legend-value">{{ item.value }} · {{ item.percent }}%</text>
        </g>
      </g>
    </svg>
    <div v-else class="empty-hint">暂无数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface PieItem {
  name: string
  value: number
  color?: string
}

const props = withDefaults(defineProps<{ items: PieItem[]; unit?: string }>(), {
  unit: ''
})

const DEFAULT_COLORS = ['#1f4e79', '#2e6da4', '#7c9ab8', '#2e7d5b', '#d4a017', '#9ca3af']

const cx = 96
const cy = 105
const radius = 72
const innerR = 44

const total = computed(() => props.items.reduce((sum, item) => sum + (Number(item.value) || 0), 0))

const ariaLabel = computed(
  () => '饼图：' + props.items.map((item) => `${item.name} ${item.value}${props.unit}`).join('，')
)

function polar(angle: number, r: number) {
  const rad = ((angle - 90) * Math.PI) / 180
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) }
}

/**
 * 扇区路径：外弧 + 内弧反向，形成环形段。
 * 整圆（单个 100% 项）用两段半圆拼接，否则起止点重合会让路径退化成一条线。
 */
const slices = computed(() => {
  const sum = total.value
  if (sum <= 0) {
    return []
  }
  const visible = props.items.filter((item) => Number(item.value) > 0)
  let start = 0
  return visible.map((item, index) => {
    const value = Number(item.value)
    const angle = (value / sum) * 360
    const end = start + angle
    const color = item.color || DEFAULT_COLORS[index % DEFAULT_COLORS.length]
    const percent = Math.round((value / sum) * 1000) / 10
    let path: string
    if (angle >= 359.999) {
      const top = polar(0, radius)
      const bottom = polar(180, radius)
      const innerTop = polar(0, innerR)
      const innerBottom = polar(180, innerR)
      path = [
        `M ${top.x} ${top.y}`,
        `A ${radius} ${radius} 0 1 1 ${bottom.x} ${bottom.y}`,
        `A ${radius} ${radius} 0 1 1 ${top.x} ${top.y}`,
        `L ${innerTop.x} ${innerTop.y}`,
        `A ${innerR} ${innerR} 0 1 0 ${innerBottom.x} ${innerBottom.y}`,
        `A ${innerR} ${innerR} 0 1 0 ${innerTop.x} ${innerTop.y}`,
        'Z'
      ].join(' ')
    } else {
      const outerStart = polar(start, radius)
      const outerEnd = polar(end, radius)
      const innerEnd = polar(end, innerR)
      const innerStart = polar(start, innerR)
      const largeArc = angle > 180 ? 1 : 0
      path = [
        `M ${outerStart.x} ${outerStart.y}`,
        `A ${radius} ${radius} 0 ${largeArc} 1 ${outerEnd.x} ${outerEnd.y}`,
        `L ${innerEnd.x} ${innerEnd.y}`,
        `A ${innerR} ${innerR} 0 ${largeArc} 0 ${innerStart.x} ${innerStart.y}`,
        'Z'
      ].join(' ')
    }
    start = end
    return { name: item.name, value, percent, color, path }
  })
})
</script>

<style scoped>
.pie-chart {
  width: 100%;
}

.pie-svg {
  width: 100%;
  height: auto;
  display: block;
}

.center-value {
  font-size: 22px;
  font-weight: 600;
  fill: #1f2937;
}

.center-label {
  font-size: 11px;
  fill: #6b7280;
}

.legend-name {
  font-size: 12px;
  fill: #1f2937;
}

.legend-value {
  font-size: 11px;
  fill: #6b7280;
}

.empty-hint {
  padding: 60px 0;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
</style>

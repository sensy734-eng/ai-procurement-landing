<template>
  <div class="line-chart">
    <svg v-if="points.length" viewBox="0 0 720 300" class="line-svg" role="img" :aria-label="ariaLabel">
      <defs>
        <linearGradient id="trend-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#2e6da4" stop-opacity="0.28" />
          <stop offset="100%" stop-color="#2e6da4" stop-opacity="0.02" />
        </linearGradient>
      </defs>

      <!-- 横向网格与刻度 -->
      <g v-for="tick in ticks" :key="'tick-' + tick.value">
        <line :x1="padding.left" :y1="tick.y" :x2="width - padding.right" :y2="tick.y"
              stroke="#eceff4" stroke-width="1" />
        <text :x="padding.left - 8" :y="tick.y + 4" text-anchor="end" class="axis-text">
          {{ tick.label }}
        </text>
      </g>

      <!-- 面积 -->
      <path :d="areaPath" fill="url(#trend-fill)" />

      <!-- 折线 -->
      <polyline :points="linePoints" fill="none" stroke="#2e6da4" stroke-width="2"
                stroke-linejoin="round" stroke-linecap="round" />

      <!-- 数据点与悬停提示 -->
      <g v-for="(point, index) in layout" :key="'pt-' + point.month">
        <circle :cx="point.x" :cy="point.y" r="3.5" fill="#ffffff" stroke="#2e6da4" stroke-width="2">
          <title>{{ point.label }}：{{ formatMoney(point.amount) }} 元，{{ point.orderCount }} 单</title>
        </circle>
        <text v-if="point.amount > 0" :x="point.x" :y="point.y - 10" text-anchor="middle"
              class="value-text" :class="{ dense: points.length > 8 && index % 2 === 1 }">
          {{ shortMoney(point.amount) }}
        </text>
      </g>

      <!-- x 轴标签 -->
      <g v-for="point in layout" :key="'label-' + point.month">
        <text :x="point.x" :y="height - 12" text-anchor="middle" class="axis-text">{{ point.label }}</text>
      </g>
    </svg>
    <div v-else class="empty-hint">暂无数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface LinePoint {
  label: string
  amount: number
  orderCount?: number
  month?: string
}

const props = defineProps<{ points: LinePoint[] }>()

const width = 720
const height = 300
const padding = { left: 64, right: 20, top: 24, bottom: 34 }

const maxValue = computed(() => {
  const max = Math.max(...props.points.map((item) => Number(item.amount) || 0), 0)
  return max > 0 ? max : 1
})

/** 纵轴取整刻度：把最大值向上取到 1/2/5×10^n，避免出现 43200 这种不好读的刻度 */
const niceMax = computed(() => {
  const raw = maxValue.value
  const magnitude = Math.pow(10, Math.floor(Math.log10(raw)))
  const normalized = raw / magnitude
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return step * magnitude
})

const ticks = computed(() => {
  const result = []
  for (let i = 0; i <= 4; i += 1) {
    const value = (niceMax.value / 4) * i
    const y = plotBottom - (value / niceMax.value) * plotHeight
    result.push({ value, y, label: shortMoney(value) })
  }
  return result
})

const plotHeight = height - padding.top - padding.bottom
const plotBottom = height - padding.bottom

const layout = computed(() => {
  const count = props.points.length
  const stepX = count > 1 ? (width - padding.left - padding.right) / (count - 1) : 0
  return props.points.map((item, index) => {
    const amount = Number(item.amount) || 0
    return {
      month: item.month || String(index),
      label: item.label,
      amount,
      orderCount: item.orderCount || 0,
      x: count > 1 ? padding.left + stepX * index : (padding.left + width - padding.right) / 2,
      y: plotBottom - (amount / niceMax.value) * plotHeight
    }
  })
})

const linePoints = computed(() => layout.value.map((point) => `${point.x},${point.y}`).join(' '))

const areaPath = computed(() => {
  const items = layout.value
  if (!items.length) {
    return ''
  }
  const head = `M ${items[0].x} ${plotBottom}`
  const line = items.map((point) => `L ${point.x} ${point.y}`).join(' ')
  const tail = `L ${items[items.length - 1].x} ${plotBottom} Z`
  return `${head} ${line} ${tail}`
})

const ariaLabel = computed(
  () => '折线图：' + props.points.map((item) => `${item.label} ${item.amount} 元`).join('，')
)

function shortMoney(value: number) {
  if (!value) {
    return '0'
  }
  if (value >= 10000) {
    return (value / 10000).toFixed(value >= 100000 ? 0 : 1) + '万'
  }
  return String(Math.round(value))
}

function formatMoney(value: number) {
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.line-chart {
  width: 100%;
}

.line-svg {
  width: 100%;
  height: auto;
  display: block;
}

.axis-text {
  font-size: 11px;
  fill: #8a92a6;
}

.value-text {
  font-size: 10px;
  fill: #1f4e79;
}

.value-text.dense {
  opacity: 0.55;
}

.empty-hint {
  padding: 60px 0;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
</style>

<template>
  <div v-if="data && data.count" class="price-index card">
    <div class="between">
      <strong>教材价格指数</strong>
      <span class="muted">{{ data.count }} 条在售</span>
    </div>

    <div class="bars">
      <div v-for="b in bars" :key="b.label" class="bar-row">
        <span class="bar-label">{{ b.label }}</span>
        <div class="bar-track">
          <div class="bar-fill" :class="b.tone" :style="{ width: b.width }"></div>
        </div>
        <span class="bar-value">{{ money(b.value) }}</span>
      </div>
    </div>

    <p v-if="data.suggest" class="hint">
      建议定价 <strong class="price">{{ money(data.suggest) }}</strong>
      <span v-if="data.basis" class="muted">· {{ data.basis }}</span>
    </p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { money } from '@/utils/format'

const props = defineProps({
  data: { type: Object, default: null }
})

const bars = computed(() => {
  const d = props.data || {}
  const max = Math.max(Number(d.max) || 1, 1)
  const rows = [
    { label: '最低', value: d.min, tone: 'low' },
    { label: '均价', value: d.avg, tone: 'avg' },
    { label: '最高', value: d.max, tone: 'high' }
  ]
  return rows
    .filter((r) => r.value !== null && r.value !== undefined)
    .map((r) => ({ ...r, width: `${Math.max(6, (Number(r.value) / max) * 100)}%` }))
})
</script>

<style scoped>
.price-index {
  margin-top: 12px;
}
.bars {
  margin: 12px 0 8px;
  display: grid;
  gap: 8px;
}
.bar-row {
  display: grid;
  grid-template-columns: 34px 1fr 74px;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.bar-label {
  color: var(--muted);
}
.bar-track {
  height: 8px;
  border-radius: 999px;
  background: #f0f2f3;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
}
.bar-fill.low {
  background: #9fc9b3;
}
.bar-fill.high {
  background: #e5b98a;
}
.bar-value {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--ink-2);
}
.hint {
  margin: 4px 0 0;
  font-size: 13px;
}
</style>

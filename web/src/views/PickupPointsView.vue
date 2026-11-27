<template>
  <div class="page pickup-page">
    <van-nav-bar title="自提点" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <div class="intro">
        <van-icon name="location-o" size="20" />
        <div>
          <strong>校内当面交易点</strong>
          <p class="muted">下单时选择自提点，到店出示核销码即可完成交易。</p>
        </div>
      </div>

      <div v-if="defaultPoint" class="default-tip">
        <van-icon name="success" />
        我的默认自提点：{{ defaultPoint.name }}
      </div>

      <div v-if="loading" class="loading-wrap">
        <van-loading size="24">正在加载自提点</van-loading>
      </div>

      <div v-else-if="error" class="card empty-wrap">
        <EmptyState :text="error" icon="📍">
          <van-button size="small" round type="primary" @click="load">重新加载</van-button>
        </EmptyState>
      </div>

      <div v-else-if="!points.length" class="card empty-wrap">
        <EmptyState text="暂时没有可用自提点" icon="📍" />
      </div>

      <div v-else class="list">
        <div v-for="p in points" :key="p.id" class="card point" :class="{ active: isDefault(p) }">
          <div class="between">
            <strong>{{ p.name }}</strong>
            <span v-if="isDefault(p)" class="tag">默认</span>
          </div>
          <p class="addr muted">
            <van-icon name="location-o" /> {{ p.address }}
          </p>
          <p class="muted hours">
            <van-icon name="clock-o" /> {{ p.openTime || '--' }} - {{ p.closeTime || '--' }}
            <span v-if="p.capacity"> · 可容纳 {{ p.capacity }} 册</span>
          </p>
          <div class="between foot">
            <span class="muted dist">{{ distanceText(p.distance) }}</span>
            <van-button
              size="small"
              round
              :type="isDefault(p) ? 'default' : 'primary'"
              :plain="isDefault(p)"
              @click="setDefault(p)"
            >
              {{ isDefault(p) ? '已设为默认' : '设为默认' }}
            </van-button>
          </div>
        </div>
      </div>

      <p class="muted tip">
        默认自提点仅保存在当前浏览器，下次下单时会自动选中；自提点由学校勤工助学中心维护。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { pickupApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const POINT_KEY = 'tl_default_point'

const points = ref([])
const loading = ref(true)
const error = ref('')
const defaultPoint = ref(null)

const isDefault = (p) => Boolean(defaultPoint.value) && Number(defaultPoint.value.id) === Number(p.id)

const distanceText = (d) => {
  if (d === null || d === undefined || d === '') return '距离未知'
  const n = Number(d)
  if (!Number.isFinite(n)) return '距离未知'
  return n < 1000 ? `距你约 ${Math.round(n)} m` : `距你约 ${(n / 1000).toFixed(1)} km`
}

function readDefault() {
  try {
    const raw = localStorage.getItem(POINT_KEY)
    defaultPoint.value = raw ? JSON.parse(raw) : null
  } catch {
    defaultPoint.value = null
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await pickupApi.list()
    points.value = Array.isArray(res) ? res : res?.records || []
  } catch {
    points.value = []
    error.value = '自提点加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function setDefault(p) {
  if (isDefault(p)) return
  const value = { id: p.id, name: p.name }
  localStorage.setItem(POINT_KEY, JSON.stringify(value))
  defaultPoint.value = value
  showToast('已设为默认自提点')
}

onMounted(() => {
  readDefault()
  load()
})
</script>

<style scoped>
.pickup-page {
  background: var(--bg);
}
.intro {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  color: var(--brand);
}
.intro strong {
  color: var(--ink);
  font-size: 14px;
}
.intro p {
  margin: 3px 0 0;
  font-size: 11px;
  line-height: 1.6;
}
.default-tip {
  margin-top: 12px;
  background: #e8f6ee;
  color: var(--brand);
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 5px;
}
.loading-wrap {
  padding: 40px 0;
  display: grid;
  place-items: center;
}
.empty-wrap {
  margin-top: 16px;
  padding: 0;
}
.list {
  margin-top: 12px;
}
.point {
  margin-bottom: 10px;
  border: 1px solid transparent;
}
.point.active {
  border-color: var(--brand);
}
.addr,
.hours {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  display: flex;
  gap: 4px;
  align-items: flex-start;
}
.foot {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}
.dist {
  font-size: 11px;
}
.tip {
  margin: 14px 4px 0;
  font-size: 11px;
  line-height: 1.6;
}
</style>

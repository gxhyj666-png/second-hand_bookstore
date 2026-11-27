<template>
  <div class="page credit-page">
    <van-nav-bar title="信用记录" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section class="score-card">
        <p class="label">当前信用分</p>
        <strong class="score">{{ store.creditScore }}</strong>
        <p class="desc">
          按时履约、完成交易会提升信用分；被取消或产生纠纷会影响信用分。100 分为初始分。
        </p>
      </section>

      <h2 class="section-title">变动明细</h2>

      <van-list
        v-model:loading="loading"
        :finished="finished"
        :finished-text="logs.length ? '没有更多记录了' : ''"
        :immediate-check="false"
        @load="loadMore"
      >
        <div v-for="(log, idx) in logs" :key="log.id || idx" class="card log">
          <div class="between">
            <strong class="reason">{{ log.reason || '信用分变动' }}</strong>
            <span class="change" :class="changeTone(log.changeVal)">{{ changeText(log.changeVal) }}</span>
          </div>
          <div class="between foot">
            <span class="muted">{{ shortDate(log.createdAt) }}</span>
            <span class="muted">变动后 {{ log.scoreAfter ?? '--' }} 分</span>
          </div>
        </div>

        <EmptyState v-if="loadedOnce && !logs.length" :text="loadError || '还没有信用变动记录'" icon="📈">
          <van-button v-if="loadError" size="small" round type="primary" @click="reload">重新加载</van-button>
        </EmptyState>
      </van-list>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { socialApi } from '@/api'
import { useUserStore } from '@/store/user'
import { shortDate } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()

const PAGE_SIZE = 15
const logs = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

function changeText(val) {
  const n = Number(val) || 0
  return n > 0 ? `+${n}` : String(n)
}

function changeTone(val) {
  const n = Number(val) || 0
  if (n > 0) return 'up'
  if (n < 0) return 'down'
  return ''
}

async function fetchPage(reset) {
  if (reset) {
    page.value = 1
    finished.value = false
    loadError.value = ''
  }
  loading.value = true
  try {
    const res = await socialApi.myCredit({ page: page.value, size: PAGE_SIZE })
    const records = recordsOf(res)
    logs.value = reset ? records : logs.value.concat(records)
    finished.value = records.length < PAGE_SIZE
    page.value += 1
    loadedOnce.value = true
  } catch {
    finished.value = true
    loadError.value = '信用记录加载失败，请稍后重试'
    loadedOnce.value = true
  } finally {
    loading.value = false
  }
}

function loadMore() {
  fetchPage(false)
}

function reload() {
  logs.value = []
  loadedOnce.value = false
  fetchPage(true)
}

onMounted(async () => {
  fetchPage(true)
  // 头部用 store 里的分数，进入页面时顺带刷新一次
  store.fetchMe().catch(() => {})
})
</script>

<style scoped>
.credit-page {
  background: var(--bg);
}
.score-card {
  margin-top: 12px;
  border-radius: var(--radius);
  padding: 18px 16px;
  background: linear-gradient(140deg, #245f45, #3d8a66);
  color: #fff;
}
.score-card .label {
  margin: 0;
  font-size: 12px;
  opacity: 0.85;
}
.score {
  display: block;
  font-size: 40px;
  font-weight: 800;
  line-height: 1.2;
  margin: 2px 0 6px;
}
.score-card .desc {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  opacity: 0.86;
}
.section-title {
  margin: 18px 2px 10px;
  font-size: 15px;
}
.log {
  margin-bottom: 8px;
}
.reason {
  font-size: 13px;
  font-weight: 600;
}
.change {
  font-size: 16px;
  font-weight: 800;
  flex: none;
  color: var(--ink-2);
}
.change.up {
  color: var(--brand);
}
.change.down {
  color: var(--accent);
}
.foot {
  margin-top: 6px;
  font-size: 11px;
}
</style>

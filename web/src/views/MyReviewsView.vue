<template>
  <div class="page">
    <van-nav-bar title="我的评价" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <van-tabs v-model:active="active" sticky offset-top="46" @change="reload">
      <van-tab title="收到的" name="received" />
      <van-tab title="我发出的" name="sent" />
    </van-tabs>

    <div class="container">
      <div class="summary">
        <span v-if="active === 'received'">买家/卖家对你的评价会显示在这里</span>
        <span v-else>你给别人的评价会显示在这里</span>
        <strong>{{ total }} 条</strong>
      </div>

      <van-loading v-if="loading && !list.length" class="center" />
      <van-empty v-else-if="!list.length" :description="active === 'received' ? '还没有收到评价' : '还没有发出评价'" />

      <div v-else class="cards">
        <article v-for="item in list" :key="item.id" class="card">
          <header class="head">
            <button type="button" class="who" @click="openUser(item.counterpartId)">
              <span class="avatar">{{ (item.counterpartName || '同').slice(0, 1) }}</span>
              <span class="name">
                {{ item.counterpartName }}
                <em v-if="item.role">{{ roleText(item.role) }}</em>
              </span>
            </button>
            <span class="rating">
              <van-rate :model-value="Number(item.rating) || 5" readonly :size="13" gutter="1" />
            </span>
          </header>
          <p v-if="item.content" class="content">{{ item.content }}</p>
          <p v-else class="content muted">这位同学没有写文字评价</p>
          <footer class="foot">
            <span class="order" @click="openOrder(item.orderNo)">{{ item.orderTitle || item.orderNo }}</span>
            <span class="muted">{{ formatDate(item.createdAt) }}</span>
          </footer>
        </article>
      </div>

      <div v-if="hasMore && list.length" class="more">
        <van-button block plain :loading="loading" @click="loadMore">加载更多</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { meApi } from '@/api'

const router = useRouter()

const active = ref('received')
const list = ref([])
const page = ref(1)
const total = ref(0)
const size = 10
const loading = ref(false)
const hasMore = ref(false)

function roleText(r) {
  const map = { BUYER: '买家', SELLER: '卖家', REVIEW_GOOD: '好评' }
  return map[r] || r
}

function formatDate(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function fetchPage(reset) {
  if (loading.value) return
  loading.value = true
  try {
    const p = reset ? 1 : page.value
    const res = await meApi.reviews({ type: active.value, page: p, size })
    const records = res.records || []
    list.value = reset ? records : list.value.concat(records)
    total.value = res.total || 0
    page.value = p
    hasMore.value = list.value.length < total.value
  } catch {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

function reload() {
  list.value = []
  page.value = 1
  fetchPage(true)
}

function loadMore() {
  page.value += 1
  fetchPage(false)
}

function openUser(id) {
  if (id) router.push(`/user/${id}`)
}

function openOrder(orderNo) {
  if (orderNo) router.push(`/order/${orderNo}`)
}

onMounted(() => fetchPage(true))
</script>

<style scoped>
.page {
  background: var(--bg);
  min-height: 100vh;
}
.container {
  padding-bottom: 40px;
}
.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px 4px;
  font-size: 12px;
  color: var(--ink-2);
}
.center {
  display: block;
  margin: 60px auto;
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 12px 0;
}
.card {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px 14px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.who {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: none;
  padding: 0;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 13px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.name {
  font-size: 13px;
  color: var(--ink);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.name em {
  font-style: normal;
  font-size: 10px;
  color: var(--ink-3, #94a3b8);
  background: #f2f4f8;
  border-radius: 6px;
  padding: 1px 5px;
}
.content {
  margin: 8px 0 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink);
  white-space: pre-wrap;
  word-break: break-word;
}
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
}
.order {
  color: var(--brand);
  max-width: 62%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.muted {
  color: var(--ink-3, #94a3b8);
}
.more {
  padding: 14px 16px 0;
}
</style>

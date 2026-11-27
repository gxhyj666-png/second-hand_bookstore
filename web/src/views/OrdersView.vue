<template>
  <div class="page orders-page">
    <header class="orders-head">
      <h1>我的订单</h1>
      <p class="muted">担保交易，核销后才把钱打给卖家</p>
      <van-tabs v-model:active="role" color="var(--brand)" title-active-color="var(--brand)" @change="onRoleChange">
        <van-tab title="我买到的" name="buyer" />
        <van-tab title="我卖出的" name="seller" />
      </van-tabs>
    </header>

    <div class="container">
      <div class="status-row">
        <button
          v-for="s in STATUS_TABS"
          :key="s.value"
          type="button"
          class="chip"
          :class="{ active: status === s.value }"
          @click="onStatusChange(s.value)"
        >
          {{ s.text }}
        </button>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          :finished-text="orders.length ? '没有更多订单了' : ''"
          :immediate-check="false"
          @load="loadMore"
        >
          <div v-for="o in orders" :key="o.orderNo" class="card order-card" @click="goDetail(o)">
            <div class="order-top between">
              <span class="order-no">订单 {{ shortNo(o.orderNo) }}</span>
              <span class="tag" :class="statusTone(o.status)">{{ orderStatusText(o.status) }}</span>
            </div>

            <div class="order-body">
              <div class="thumb">
                <img v-if="coverOf(o)" :src="coverOf(o)" :alt="titleOf(o)" @error="onImgError" />
                <span v-else>{{ (titleOf(o) || '书').slice(0, 1) }}</span>
              </div>
              <div class="order-main">
                <strong class="clamp-2">{{ titleOf(o) || '教材信息加载中' }}</strong>
                <p class="muted">
                  <span v-if="counterpartOf(o)">{{ counterpartOf(o) }}</span>
                  <span v-if="counterpartOf(o)"> · </span>
                  <span>{{ shortDate(o.createdAt) }}</span>
                </p>
              </div>
              <span class="price amount">{{ money(o.amount) }}</span>
            </div>

            <div v-if="role === 'buyer' && o.status === 'PENDING_PAY'" class="order-actions">
              <van-button size="small" round plain @click.stop="cancel(o)">取消订单</van-button>
              <van-button size="small" round type="primary" @click.stop="pay(o)">去支付</van-button>
            </div>
            <div v-else-if="o.status === 'FROZEN' || o.status === 'WAIT_VERIFY'" class="order-hint">
              <van-icon name="info-o" />
              {{ role === 'buyer' ? '到店后请出示核销码' : '等待买家到店核销' }}
              <span class="link" @click.stop="goDetail(o)">查看详情</span>
            </div>
          </div>

          <EmptyState
            v-if="loadedOnce && !orders.length"
            :text="loadError || (role === 'buyer' ? '还没有买过教材' : '还没有卖出教材')"
            icon="🧾"
          >
            <van-button v-if="loadError" size="small" round type="primary" @click="reload">重新加载</van-button>
            <van-button v-else size="small" round plain type="primary" @click="router.push('/search')">
              去找书
            </van-button>
          </EmptyState>
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { listingApi, orderApi } from '@/api'
import { money, orderStatusText, shortDate } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const STATUS_TABS = [
  { text: '全部', value: '' },
  { text: '待支付', value: 'PENDING_PAY' },
  { text: '待核销', value: 'WAIT_VERIFY' },
  { text: '已完成', value: 'SETTLED' }
]

const PAGE_SIZE = 10

const role = ref('buyer')
const status = ref('')
const orders = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')
const busyNo = ref('')

// 订单表只有 listingId，列表里需要额外补教材标题/封面
const listingCache = ref({})

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

function shortNo(no) {
  return no ? String(no).slice(-8) : '--'
}

function titleOf(o) {
  return listingCache.value[o.listingId]?.title || o.textbookTitle || ''
}

function coverOf(o) {
  return listingCache.value[o.listingId]?.coverUrl || ''
}

function counterpartOf(o) {
  return o.sellerNickname || o.buyerNickname || ''
}

function statusTone(s) {
  if (s === 'PENDING_PAY') return 'warn'
  if (s === 'REFUNDED') return 'danger'
  if (s === 'CLOSED') return 'muted'
  return ''
}

function onImgError(e) {
  e.target.style.visibility = 'hidden'
}

async function enrich(records) {
  const ids = [...new Set(records.map((o) => o.listingId).filter((id) => id && !listingCache.value[id]))]
  if (!ids.length) return
  const results = await Promise.allSettled(
    ids.map(async (id) => {
      const d = await listingApi.detail(id)
      return { id, title: d?.listing?.title || d?.textbook?.title || '', coverUrl: d?.listing?.coverUrl || '' }
    })
  )
  const next = { ...listingCache.value }
  results.forEach((r) => {
    if (r.status === 'fulfilled' && r.value) next[r.value.id] = r.value
  })
  listingCache.value = next
}

async function fetchPage(reset) {
  if (reset) {
    page.value = 1
    finished.value = false
    loadError.value = ''
  }
  loading.value = true
  try {
    const res = await orderApi.list({
      role: role.value,
      status: status.value || undefined,
      page: page.value,
      size: PAGE_SIZE
    })
    const records = recordsOf(res)
    orders.value = reset ? records : orders.value.concat(records)
    finished.value = records.length < PAGE_SIZE
    page.value += 1
    loadedOnce.value = true
    enrich(records)
  } catch {
    // 请求层已经提示，这里只负责停止加载并给出重试入口
    finished.value = true
    loadError.value = '订单加载失败，请稍后重试'
    loadedOnce.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function loadMore() {
  fetchPage(false)
}

function reload() {
  orders.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function onRefresh() {
  orders.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function onRoleChange() {
  orders.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function onStatusChange(value) {
  if (status.value === value) return
  status.value = value
  orders.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function goDetail(o) {
  router.push(`/order/${o.orderNo}`)
}

async function pay(o) {
  if (busyNo.value) return
  busyNo.value = o.orderNo
  try {
    await orderApi.pay(o.orderNo, 'MOCK')
    showToast('支付成功，等待到店核销')
    reload()
  } catch {
    // 请求层已经提示
  } finally {
    busyNo.value = ''
  }
}

async function cancel(o) {
  try {
    await showConfirmDialog({
      title: '取消订单',
      message: '取消后订单将关闭，需要重新下单，确定取消吗？',
      confirmButtonText: '取消订单',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  if (busyNo.value) return
  busyNo.value = o.orderNo
  try {
    await orderApi.cancel(o.orderNo)
    showToast('订单已取消')
    reload()
  } catch {
    // 请求层已经提示
  } finally {
    busyNo.value = ''
  }
}

onMounted(() => fetchPage(true))
</script>

<style scoped>
.orders-page {
  background: var(--bg);
}
.orders-head {
  background: #fff;
  padding: 20px 16px 0;
  border-bottom: 1px solid var(--line);
}
.orders-head h1 {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 800;
}
.orders-head p {
  margin: 0 0 10px;
  font-size: 12px;
}
.status-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 12px 0;
}
.chip {
  flex: none;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-2);
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
}
.chip.active {
  border-color: var(--brand);
  background: var(--brand);
  color: #fff;
}
.order-card {
  margin-bottom: 10px;
  cursor: pointer;
}
.order-top {
  font-size: 12px;
  color: var(--muted);
}
.order-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.order-body {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 10px;
}
.thumb {
  width: 54px;
  height: 70px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
  display: grid;
  place-items: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 22px;
  font-weight: 700;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.order-main {
  flex: 1;
  min-width: 0;
}
.order-main strong {
  font-size: 14px;
  line-height: 1.45;
}
.order-main p {
  margin: 6px 0 0;
  font-size: 12px;
}
.amount {
  font-size: 16px;
  flex: none;
}
.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}
.order-hint {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  color: var(--muted);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.link {
  margin-left: auto;
  color: var(--brand);
  cursor: pointer;
}
</style>

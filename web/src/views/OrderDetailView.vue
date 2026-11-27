<template>
  <div class="page order-detail-page">
    <van-nav-bar title="订单详情" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div v-if="loading" class="container loading-wrap">
      <div class="skeleton line-lg"></div>
      <div class="skeleton line"></div>
      <div class="skeleton line short"></div>
    </div>

    <div v-else-if="!order" class="container">
      <div class="card empty-wrap">
        <EmptyState :text="loadError || '订单不存在或无权查看'" icon="🧾">
          <van-button size="small" round type="primary" @click="load">重新加载</van-button>
          <van-button size="small" round plain type="primary" @click="router.push('/orders')">返回订单</van-button>
        </EmptyState>
      </div>
    </div>

    <template v-else>
      <div class="container">
        <section class="status-hero" :class="statusTone">
          <van-icon :name="statusIcon" size="26" />
          <div class="status-main">
            <h1>{{ orderStatusText(order.status) }}</h1>
            <p>{{ statusDesc }}</p>
          </div>
        </section>

        <section v-if="isBuyer && showCode" class="card code-card">
          <p class="code-label">到店核销码</p>
          <div class="code-value">{{ code || '------' }}</div>
          <p v-if="countdown > 0" class="muted code-timer">
            剩余有效时间 <strong>{{ mmss }}</strong>，过期会自动刷新
          </p>
          <p v-else class="muted code-timer">正在获取最新核销码…</p>
          <p v-if="codeError" class="code-error">{{ codeError }}</p>
          <p class="muted code-tip">请到「{{ point?.name || '自提点' }}」向店员出示，店员核销后款项才会结算给卖家。</p>
        </section>

        <section class="card book-card" @click="goListing">
          <div class="thumb">
            <img v-if="cover" :src="cover" :alt="listing?.title" @error="onImgError" />
            <span v-else>{{ (listing?.title || '书').slice(0, 1) }}</span>
          </div>
          <div class="book-main">
            <strong class="clamp-2">{{ listing?.title || '教材信息' }}</strong>
            <p class="muted">
              <span v-if="condition">{{ conditionText(condition) }}</span>
              <span v-if="condition && listing?.dorm"> · </span>
              <span v-if="listing?.dorm">{{ listing.dorm }}</span>
            </p>
          </div>
          <div class="price amount">{{ money(order.amount) }}</div>
        </section>

        <section class="card">
          <h2 class="card-title">订单信息</h2>
          <div class="rows">
            <div class="row-item">
              <span>订单号</span>
              <span class="val mono">
                {{ order.orderNo }}
                <van-icon name="description" class="copy" @click.stop="copyNo" />
              </span>
            </div>
            <div class="row-item">
              <span>商品金额</span>
              <span class="val">{{ money(order.amount) }}</span>
            </div>
            <div class="row-item">
              <span>平台佣金</span>
              <span class="val">{{ money(order.commission || 0) }}</span>
            </div>
            <div class="row-item">
              <span>自提点</span>
              <span class="val">{{ point?.name || '加载中…' }}</span>
            </div>
            <div v-if="point?.address" class="row-item">
              <span>自提地址</span>
              <span class="val">{{ point.address }}</span>
            </div>
            <div class="row-item">
              <span>下单时间</span>
              <span class="val">{{ shortDate(order.createdAt) }}</span>
            </div>
            <div v-if="order.payChannel" class="row-item">
              <span>支付方式</span>
              <span class="val">{{ order.payChannel }}</span>
            </div>
            <div v-if="order.remark" class="row-item">
              <span>备注</span>
              <span class="val">{{ order.remark }}</span>
            </div>
            <div v-if="order.refundReason" class="row-item">
              <span>退款原因</span>
              <span class="val">{{ order.refundReason }}</span>
            </div>
          </div>
        </section>

        <section v-if="timeline.length" class="card">
          <h2 class="card-title">订单进度</h2>
          <ol class="timeline">
            <li v-for="(t, idx) in timeline" :key="t.label" :class="{ latest: idx === timeline.length - 1 }">
              <span class="dot" />
              <div class="tl-main">
                <strong>{{ t.label }}</strong>
                <p class="muted">{{ shortDate(t.value) }}</p>
              </div>
            </li>
          </ol>
        </section>

        <section v-if="isSeller && !readonly" class="card seller-tip">
          <van-icon name="passed" size="18" />
          <div>
            <strong>我已交付，让买家核销</strong>
            <p class="muted">买家到店出示核销码后由店员核销，货款会自动结算到你的账户。</p>
          </div>
        </section>

        <p v-if="readonly" class="muted readonly-tip">该订单已结束，只能查看，无法继续操作。</p>
      </div>

      <div v-if="hasActions" class="action-bar">
        <div class="action-inner">
          <template v-if="isBuyer && order.status === 'PENDING_PAY'">
            <van-button round plain :loading="cancelling" @click="cancel">取消订单</van-button>
            <van-button round type="primary" :loading="paying" @click="pay">去支付</van-button>
          </template>
          <template v-else-if="isBuyer && order.status === 'FROZEN'">
            <van-button block round type="primary" :loading="waiting" @click="markArrived">
              我到了，标记待核销
            </van-button>
          </template>
          <template v-else-if="isBuyer && (order.status === 'VERIFIED' || order.status === 'SETTLED')">
            <van-button block round type="primary" @click="goReview">评价卖家</van-button>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { listingApi, orderApi, pickupApi } from '@/api'
import { useUserStore } from '@/store/user'
import { conditionText, money, orderStatusText, remainText, shortDate } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const orderNo = String(route.params.orderNo || '')
const order = ref(null)
const listing = ref(null)
const point = ref(null)
const loading = ref(true)
const loadError = ref('')

const code = ref('')
const countdown = ref(0)
const codeError = ref('')
let timer = null

const paying = ref(false)
const cancelling = ref(false)
const waiting = ref(false)

const isBuyer = computed(
  () => Boolean(order.value) && Number(order.value.buyerId) === Number(store.userId)
)
const isSeller = computed(
  () => Boolean(order.value) && Number(order.value.sellerId) === Number(store.userId)
)
const cover = computed(() => listing.value?.coverUrl || '')
const condition = computed(() => listing.value?.conditionLevel || listing.value?.condition || '')
const showCode = computed(
  () => isBuyer.value && ['FROZEN', 'WAIT_VERIFY'].includes(order.value?.status)
)
const readonly = computed(() => ['REFUNDED', 'CLOSED'].includes(order.value?.status))
const hasActions = computed(() => {
  if (!order.value || readonly.value || !isBuyer.value) return false
  return ['PENDING_PAY', 'FROZEN', 'VERIFIED', 'SETTLED'].includes(order.value.status)
})

const mmss = computed(() => {
  const s = Math.max(0, countdown.value)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})

const statusTone = computed(() => {
  const s = order.value?.status
  if (s === 'PENDING_PAY') return 'warn'
  if (s === 'CLOSED' || s === 'REFUNDED') return 'muted'
  if (s === 'SETTLED') return 'done'
  return 'active'
})

const statusIcon = computed(() => {
  const map = {
    PENDING_PAY: 'clock-o',
    FROZEN: 'balance-pay',
    WAIT_VERIFY: 'scan',
    VERIFIED: 'passed',
    SETTLED: 'gold-coin-o',
    REFUNDED: 'back-top',
    CLOSED: 'close'
  }
  return map[order.value?.status] || 'info-o'
})

const statusDesc = computed(() => {
  const s = order.value?.status
  const map = {
    PENDING_PAY: order.value?.expireAt
      ? `请在 ${remainText(order.value.expireAt)} 内完成支付，超时自动关闭`
      : '请尽快完成支付，超时订单会自动关闭',
    FROZEN: isSeller.value ? '买家已付款，等待买家到店核销' : '已付款，请到自提点出示核销码',
    WAIT_VERIFY: '买家已到店，等待店员核销',
    VERIFIED: '核销完成，等待结算或已结算',
    SETTLED: '交易已完成，感谢使用',
    REFUNDED: '订单已退款',
    CLOSED: '订单已关闭'
  }
  return map[s] || '订单状态更新中'
})

const timeline = computed(() => {
  if (!order.value) return []
  return [
    { label: '提交订单', value: order.value.createdAt },
    { label: '买家付款', value: order.value.paidAt },
    { label: '到店核销', value: order.value.verifiedAt },
    { label: '交易结算', value: order.value.settledAt },
    { label: '订单关闭', value: order.value.closedAt }
  ].filter((t) => t.value)
})

function onImgError(e) {
  e.target.style.visibility = 'hidden'
}

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function startCountdown(seconds) {
  clearTimer()
  countdown.value = Number(seconds) || 0
  if (countdown.value <= 0) return
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearTimer()
      // 核销码每 30 秒轮换，归零后重新拉取而不是本地循环
      if (showCode.value) loadCode()
    }
  }, 1000)
}

async function loadCode() {
  if (!order.value) return
  try {
    const res = await orderApi.verifyCode(order.value.orderNo)
    code.value = res?.code || ''
    codeError.value = ''
    if (res?.status && res.status !== order.value.status) order.value.status = res.status
    startCountdown(res?.remainSeconds)
  } catch {
    codeError.value = '核销码获取失败，可下拉或稍后重试'
    startCountdown(0)
  }
}

async function loadExtras() {
  const tasks = []
  if (order.value?.listingId) tasks.push(listingApi.detail(order.value.listingId))
  else tasks.push(Promise.resolve(null))
  if (order.value?.pointId) tasks.push(pickupApi.get(order.value.pointId))
  else tasks.push(Promise.resolve(null))

  const [listingRes, pointRes] = await Promise.allSettled(tasks)
  if (listingRes.status === 'fulfilled' && listingRes.value) {
    listing.value = listingRes.value.listing || listingRes.value
  }
  if (pointRes.status === 'fulfilled' && pointRes.value) point.value = pointRes.value
}

async function load() {
  loading.value = true
  loadError.value = ''
  clearTimer()
  try {
    order.value = await orderApi.detail(orderNo)
    await loadExtras()
    if (showCode.value) loadCode()
  } catch {
    order.value = null
    loadError.value = '订单不存在或无权查看'
  } finally {
    loading.value = false
  }
}

async function copyNo() {
  try {
    await navigator.clipboard.writeText(orderNo)
    showToast('订单号已复制')
  } catch {
    showToast('复制失败，请长按选择订单号')
  }
}

function goListing() {
  if (order.value?.listingId) router.push(`/listing/${order.value.listingId}`)
}

function goReview() {
  router.push(`/review/${orderNo}`)
}

async function pay() {
  if (paying.value) return
  try {
    await showConfirmDialog({
      title: '确认支付',
      message: `当前为演示环境，将使用模拟支付 ${money(order.value.amount)}，不会产生真实扣款。`,
      confirmButtonText: '模拟支付'
    })
  } catch {
    return
  }
  paying.value = true
  try {
    await orderApi.pay(orderNo, 'MOCK')
    showToast('支付成功')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    paying.value = false
  }
}

async function cancel() {
  if (cancelling.value) return
  try {
    await showConfirmDialog({
      title: '取消订单',
      message: '取消后订单将关闭，确定吗？',
      confirmButtonText: '取消订单'
    })
  } catch {
    return
  }
  cancelling.value = true
  try {
    await orderApi.cancel(orderNo)
    showToast('订单已取消')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    cancelling.value = false
  }
}

async function markArrived() {
  if (waiting.value) return
  try {
    await showConfirmDialog({
      title: '标记已到店',
      message: '确认你已到达自提点？标记后请把核销码出示给店员。',
      confirmButtonText: '我已到店'
    })
  } catch {
    return
  }
  waiting.value = true
  try {
    await orderApi.waitVerify(orderNo)
    showToast('已标记，等待店员核销')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    waiting.value = false
  }
}

onMounted(load)
onBeforeUnmount(clearTimer)
</script>

<style scoped>
.order-detail-page {
  background: var(--bg);
  padding-bottom: 92px;
}
.loading-wrap {
  padding-top: 14px;
}
.line-lg,
.line,
.line.short {
  height: 16px;
  margin-bottom: 12px;
}
.line-lg {
  height: 60px;
}
.line.short {
  width: 55%;
}
.empty-wrap {
  margin-top: 16px;
}
.status-hero {
  margin-top: 12px;
  border-radius: var(--radius);
  padding: 16px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: linear-gradient(135deg, #2f7d5a, #4c9d78);
  color: #fff;
}
.status-hero.warn {
  background: linear-gradient(135deg, #e08a2e, #e6a23c);
}
.status-hero.muted {
  background: linear-gradient(135deg, #6b7480, #8b939c);
}
.status-hero.done {
  background: linear-gradient(135deg, #245f45, #3d8a66);
}
.status-main h1 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 800;
}
.status-main p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  opacity: 0.92;
}
.code-card {
  margin-top: 12px;
  text-align: center;
  border: 1px dashed var(--brand);
}
.code-label {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}
.code-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 38px;
  font-weight: 800;
  letter-spacing: 10px;
  color: var(--brand);
  padding: 6px 0 2px 10px;
}
.code-timer {
  margin: 2px 0 0;
  font-size: 12px;
}
.code-timer strong {
  color: var(--accent);
}
.code-error {
  margin: 4px 0 0;
  color: var(--accent);
  font-size: 12px;
}
.code-tip {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
}
.book-card {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  cursor: pointer;
}
.thumb {
  width: 58px;
  height: 76px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
  display: grid;
  place-items: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 24px;
  font-weight: 700;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.book-main {
  flex: 1;
  min-width: 0;
}
.book-main strong {
  font-size: 14px;
  line-height: 1.45;
}
.book-main p {
  margin: 6px 0 0;
  font-size: 12px;
}
.amount {
  font-size: 16px;
  flex: none;
}
.card-title {
  margin: 0 0 10px;
  font-size: 15px;
}
.rows {
  display: grid;
  gap: 9px;
}
.row-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 13px;
  line-height: 1.5;
}
.row-item > span:first-child {
  color: var(--muted);
  flex: none;
}
.val {
  text-align: right;
  word-break: break-all;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.copy {
  color: var(--brand);
  cursor: pointer;
}
.timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 4px;
}
.timeline li {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 0 0 16px 14px;
  border-left: 1px solid var(--line);
}
.timeline li:last-child {
  border-left-color: transparent;
  padding-bottom: 0;
}
.dot {
  position: absolute;
  left: -4px;
  top: 5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--line);
}
.timeline li.latest .dot {
  background: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-weak);
}
.tl-main strong {
  font-size: 13px;
}
.tl-main p {
  margin: 2px 0 0;
  font-size: 12px;
}
.seller-tip {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  color: var(--brand);
  align-items: flex-start;
}
.seller-tip strong {
  color: var(--ink);
  font-size: 14px;
}
.seller-tip p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
}
.readonly-tip {
  margin: 16px 4px;
  text-align: center;
  font-size: 12px;
}
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  border-top: 1px solid var(--line);
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom, 0px));
  z-index: 20;
}
.action-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  gap: 10px;
}
.action-inner :deep(.van-button) {
  flex: 1;
}
.empty-wrap {
  padding: 0;
}
</style>

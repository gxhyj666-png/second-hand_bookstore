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
            <h1>{{ orderStatusText(order.status, order.closeType) }}</h1>
            <p>{{ statusDesc }}</p>
          </div>
        </section>

        <!-- 买家核销码 -->
        <section v-if="showCode" class="card code-card">
          <p class="code-label">到店核销码</p>
          <div class="code-value">{{ code || '------' }}</div>
          <p v-if="countdown > 0" class="muted code-timer">
            剩余有效时间 <strong>{{ mmss }}</strong>，超时后需卖家重新生成
          </p>
          <p v-else class="muted code-timer">正在获取最新核销码…</p>
          <p v-if="order.meetTime" class="muted code-timer">
            约定时间 {{ fmtTime(order.meetTime) }}，超过约定时间 15 分钟核销码自动失效
          </p>
          <p v-if="codeError" class="code-error">{{ codeError }}</p>
          <van-button size="small" round plain type="primary" :loading="codeLoading" @click="loadCode">
            刷新验证码
          </van-button>
          <p class="muted code-tip">
            请到「{{ point?.name || '自提点' }}」向卖家出示，卖家核销并上传签到照后订单完成。
          </p>
        </section>

        <!-- 卖家核销 -->
        <section v-if="isSeller && order.status === 'WAIT_VERIFY'" id="verify-card" class="card">
          <div class="between">
            <h2 class="card-title">核销订单</h2>
            <span class="tag info">仅卖家可操作</span>
          </div>
          <p class="muted tip-line">向买家索要 6 位核销码，并上传一张当面交付的签到照。</p>
          <van-field
            v-model="verifyForm.code"
            label="核销码"
            maxlength="6"
            type="digit"
            placeholder="请输入买家出示的核销码"
          />
          <div class="sign-wrap">
            <DemoUploader v-model="verifyForm.signPhotoUrl" label="签到照片" file-key="sign" />
          </div>
          <van-button
            block
            round
            type="primary"
            class="submit-btn"
            :loading="verifying"
            loading-text="核销中"
            @click="submitVerify"
          >
            确认核销
          </van-button>
          <van-button
            block
            round
            plain
            type="primary"
            class="submit-btn"
            :loading="regenerating"
            @click="regenerateCode"
          >
            重新生成核销码
          </van-button>
          <p class="muted tip-line">买家核销码失效或未出示时，由你重新生成，旧码立即作废。</p>
        </section>

        <!-- 退款卡片 -->
        <section v-if="showRefundCard" class="card refund-card">
          <div class="between">
            <h2 class="card-title">退款 / 退货</h2>
            <span class="tag" :class="refundTone">{{ refundStatusText }}</span>
          </div>
          <p v-if="order.refundReason" class="refund-reason">申请原因：{{ order.refundReason }}</p>

          <div v-if="!refundId && needsRefundId" class="refund-id-fix">
            <p class="muted tip-line">
              订单详情接口未返回退货单号，无法直接操作。可填入申请退款时返回的单号（本机申请会自动记住）。
            </p>
            <div class="refund-id-row">
              <van-field v-model="manualRefundId" type="digit" placeholder="退货单号 refundId" />
              <van-button size="small" round type="primary" @click="saveRefundId">保存</van-button>
            </div>
          </div>

          <div class="refund-actions">
            <template v-if="isSeller && order.status === 'REFUNDING'">
              <van-button size="small" round plain type="danger" :loading="rejecting" @click="rejectRefund">
                拒绝退款
              </van-button>
              <van-button size="small" round type="primary" :loading="agreeing" @click="agreeRefund">
                同意退货
              </van-button>
            </template>
            <template v-else-if="isBuyer && order.status === 'REFUND_AGREED'">
              <van-button size="small" round type="primary" :loading="finishing" @click="finishRefund">
                确认退货完成
              </van-button>
            </template>
            <template v-else-if="isBuyer && refundId && ['REFUNDING', 'COMPLETED'].includes(order.status)">
              <van-button size="small" round plain type="danger" :loading="arbitrating" @click="requestArbitration">
                申请平台介入
              </van-button>
            </template>
          </div>
          <p class="muted tip-line">退款完成后教材自动回到在售；平台介入由管理员裁决。</p>
        </section>

        <!-- 商品 -->
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

        <!-- 约见 -->
        <section class="card meet-card">
          <div class="between">
            <h2 class="card-title">约见记录</h2>
            <van-button size="mini" round plain type="primary" @click="openMeetPopup">发起约见</van-button>
          </div>
          <p v-if="!meets.length" class="muted tip-line">还没有约见，和对方约个时间当面交易吧。</p>
          <div v-for="m in meets" :key="m.id" class="meet-item">
            <div class="between">
              <strong>{{ MEET_TYPE[m.meetType] || '约见' }}</strong>
              <span class="tag" :class="meetTone(m.status)">{{ meetStatusText(m.status) }}</span>
            </div>
            <p class="meet-line"><van-icon name="location-o" /> {{ m.place || '地点待定' }}</p>
            <p class="meet-line"><van-icon name="clock-o" /> {{ fmtTime(m.meetTime) }}</p>
            <p v-if="m.remark" class="muted meet-note">{{ m.remark }}</p>
            <div v-if="m.status === 'PROPOSED' && Number(m.proposerId) !== Number(store.userId)" class="meet-actions">
              <van-button size="mini" round plain @click="confirmMeet(m, false)">拒绝</van-button>
              <van-button size="mini" round type="primary" @click="confirmMeet(m, true)">同意约见</van-button>
            </div>
            <p v-else-if="m.status === 'PROPOSED'" class="muted meet-note">等待对方确认</p>
          </div>
          <div v-if="canReportNoShow" class="no-show">
            <van-button size="small" round plain type="danger" :loading="noShowBusy" @click="reportNoShow">
              上报对方爽约
            </van-button>
            <span class="muted tip-line">约定时间 30 分钟后可上报，对方扣 5 信誉分</span>
          </div>
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
            <div v-if="order.meetPlace" class="row-item">
              <span>约定地点</span>
              <span class="val">{{ order.meetPlace }}</span>
            </div>
            <div v-if="order.meetTime" class="row-item">
              <span>约定时间</span>
              <span class="val">{{ fmtTime(order.meetTime) }}</span>
            </div>
            <div class="row-item">
              <span>下单时间</span>
              <span class="val">{{ shortDate(order.createdAt) }}</span>
            </div>
            <div v-if="order.payChannel" class="row-item">
              <span>支付方式</span>
              <span class="val">{{ order.payChannel }}</span>
            </div>
            <div v-if="order.signAt" class="row-item">
              <span>核销时间</span>
              <span class="val">{{ shortDate(order.signAt) }}</span>
            </div>
            <div v-if="order.remark" class="row-item">
              <span>备注</span>
              <span class="val">{{ order.remark }}</span>
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

        <div v-if="(isBuyer || isSeller) && order.listingId" class="contact-row">
          <van-button size="small" round plain icon="chat-o" @click="openChat">联系对方</van-button>
        </div>

        <p v-if="readonly" class="muted readonly-tip">该订单已结束，只能查看，无法继续操作。</p>
      </div>

      <div v-if="showActionBar" class="action-bar">
        <div class="action-inner">
          <template v-if="isBuyer && order.status === 'PENDING_PAY'">
            <van-button round plain :loading="cancelling" @click="cancel">取消订单</van-button>
            <van-button round type="primary" :loading="paying" @click="pay">去支付</van-button>
          </template>
          <template v-else-if="isBuyer && order.status === 'WAIT_VERIFY'">
            <van-button round plain :loading="cancelling" @click="cancel">取消订单</van-button>
            <van-button round type="primary" :loading="codeLoading" @click="loadCode">
              刷新核销码
            </van-button>
          </template>
          <template v-else-if="isBuyer && order.status === 'COMPLETED'">
            <van-button v-if="canApplyRefund" round plain type="danger" @click="refundPopup = true">
              申请退款
            </van-button>
            <van-button round type="primary" @click="goReview">评价卖家</van-button>
          </template>
          <template v-else-if="isBuyer && order.status === 'REFUND_AGREED'">
            <van-button block round type="primary" :loading="finishing" @click="finishRefund">
              确认退货完成
            </van-button>
          </template>
          <template v-else-if="isSeller && order.status === 'WAIT_VERIFY'">
            <van-button round plain :loading="cancelling" @click="cancel">取消订单</van-button>
            <van-button round type="primary" @click="scrollToVerify">去核销</van-button>
          </template>
        </div>
      </div>
    </template>

    <!-- 申请退款 -->
    <van-popup v-model:show="refundPopup" position="bottom" round>
      <div class="popup">
        <div class="between popup-head">
          <strong>申请退款</strong>
          <van-icon name="cross" size="18" @click="refundPopup = false" />
        </div>
        <p class="muted tip-line">完成交易 7 天内可申请；退货退款需再约时间地点当面退回，仅退款由卖家同意后直接退回钱款。</p>
        <van-radio-group v-model="refundForm.type" direction="horizontal" class="meet-type">
          <van-radio name="RETURN">退货退款</van-radio>
          <van-radio name="REFUND_ONLY">仅退款</van-radio>
        </van-radio-group>
        <van-field v-model="refundForm.reason" type="textarea" rows="3" autosize maxlength="200" label="退款原因" placeholder="例如：缺页、品相与描述不符" />
        <div class="refund-photo">
          <DemoUploader v-model="refundForm.photoUrl" label="问题凭证" file-key="refund" />
        </div>
        <van-button block round type="danger" class="submit-btn" :loading="refunding" @click="applyRefund">
          提交退款申请
        </van-button>
      </div>
    </van-popup>

    <!-- 发起约见 -->
    <van-popup v-model:show="meetPopup" position="bottom" round>
      <div class="popup">
        <div class="between popup-head">
          <strong>发起约见</strong>
          <van-icon name="cross" size="18" @click="meetPopup = false" />
        </div>
        <van-radio-group v-model="meetForm.type" direction="horizontal" class="meet-type">
          <van-radio name="TRADE">交易约见</van-radio>
          <van-radio name="RETURN">退货约见</van-radio>
        </van-radio-group>
        <van-field v-model="meetForm.place" label="地点" maxlength="60" placeholder="例如：图书馆西门" />
        <label class="native-field">
          <span>时间</span>
          <input v-model="meetForm.datetime" type="datetime-local" />
        </label>
        <van-button block round type="primary" class="submit-btn" :loading="creatingMeet" @click="createMeet">
          发起约见
        </van-button>
        <p class="muted tip-line">对方确认后生效；约定时间 30 分钟后可上报爽约。</p>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { conversationApi, listingApi, orderApi, pickupApi } from '@/api'
import { useUserStore } from '@/store/user'
import {
  MEET_TYPE,
  conditionText,
  meetStatusText,
  money,
  orderStatusText,
  remainText,
  shortDate
} from '@/utils/format'
import DemoUploader from '@/components/DemoUploader.vue'
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
const codeLoading = ref(false)
let timer = null
let clock = null

const paying = ref(false)
const cancelling = ref(false)
const verifying = ref(false)
const regenerating = ref(false)
const refunding = ref(false)
const agreeing = ref(false)
const rejecting = ref(false)
const finishing = ref(false)
const arbitrating = ref(false)
const noShowBusy = ref(false)
const creatingMeet = ref(false)
const confirmingId = ref(null)

const verifyForm = reactive({ code: '', signPhotoUrl: '' })
const refundForm = reactive({ reason: '', photoUrl: '', type: 'RETURN' })
const meetForm = reactive({ type: 'TRADE', place: '', datetime: '' })
const refundPopup = ref(false)
const meetPopup = ref(false)

const meets = ref([])
const nowTs = ref(Date.now())
const manualRefundId = ref('')
const refundId = ref('')

const refundCacheKey = `tl_refund_${orderNo}`

const isBuyer = computed(
  () => Boolean(order.value) && Number(order.value.buyerId) === Number(store.userId)
)
const isSeller = computed(
  () => Boolean(order.value) && Number(order.value.sellerId) === Number(store.userId)
)
const cover = computed(() => listing.value?.coverUrl || '')
const condition = computed(() => listing.value?.conditionLevel || listing.value?.condition || '')
const showCode = computed(() => isBuyer.value && ['WAIT_VERIFY', 'FROZEN'].includes(order.value?.status))
const readonly = computed(() => ['REFUNDED', 'CLOSED', 'SETTLED'].includes(order.value?.status))

const needsRefundId = computed(
  () => Boolean(order.value) && ['REFUNDING', 'REFUND_AGREED', 'ARBITRATING'].includes(order.value.status)
)
const showRefundCard = computed(
  () =>
    Boolean(order.value) &&
    (['REFUNDING', 'REFUND_AGREED', 'REFUNDED', 'ARBITRATING'].includes(order.value.status) ||
      Boolean(refundId.value) ||
      Boolean(order.value.refundReason))
)
const refundStatusText = computed(() => {
  const s = order.value?.status
  return (
    {
      REFUNDING: '等待卖家处理',
      REFUND_AGREED: '卖家已同意退货',
      REFUNDED: '退款已完成',
      ARBITRATING: '平台介入中'
    }[s] || '退款处理中'
  )
})
const refundTone = computed(() => {
  const s = order.value?.status
  if (s === 'REFUNDED') return 'muted'
  if (s === 'ARBITRATING') return 'danger'
  return 'warn'
})

const completedTs = computed(() => {
  const raw = order.value?.completedAt || order.value?.verifiedAt || order.value?.settledAt
  if (!raw) return 0
  const t = new Date(String(raw).replace(' ', 'T')).getTime()
  return Number.isNaN(t) ? 0 : t
})
const canApplyRefund = computed(() => {
  if (!isBuyer.value || order.value?.status !== 'COMPLETED') return false
  if (!completedTs.value) return false
  return nowTs.value - completedTs.value <= 7 * 24 * 3600 * 1000
})
const refundDeadline = computed(() => (completedTs.value ? completedTs.value + 7 * 24 * 3600 * 1000 : 0))

const mmss = computed(() => {
  const s = Math.max(0, countdown.value)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})

const statusTone = computed(() => {
  const s = order.value?.status
  if (s === 'PENDING_PAY' || s === 'REFUNDING' || s === 'REFUND_AGREED') return 'warn'
  if (s === 'CLOSED' || s === 'REFUNDED') return 'muted'
  if (s === 'COMPLETED' || s === 'SETTLED') return 'done'
  if (s === 'ARBITRATING') return 'danger'
  return 'active'
})

const statusIcon = computed(
  () =>
    ({
      PENDING_PAY: 'clock-o',
      WAIT_VERIFY: 'scan',
      COMPLETED: 'passed',
      REFUNDING: 'refund-o',
      REFUND_AGREED: 'revoke',
      REFUNDED: 'back-top',
      ARBITRATING: 'warning-o',
      CLOSED: 'close',
      FROZEN: 'balance-pay',
      VERIFIED: 'passed',
      SETTLED: 'gold-coin-o'
    })[order.value?.status] || 'info-o'
)

const statusDesc = computed(() => {
  const s = order.value?.status
  const map = {
    PENDING_PAY: order.value?.expireAt
      ? `请在 ${remainText(order.value.expireAt)} 内完成支付，超时自动关闭`
      : '请尽快完成支付，超时订单会自动关闭',
    WAIT_VERIFY: isSeller.value ? '买家已付款，等待你当面核销' : '已付款，请向卖家出示核销码',
    COMPLETED: '交易已完成，感谢使用',
    REFUNDING: '退款申请已提交，等待卖家同意或拒绝',
    REFUND_AGREED: '卖家已同意退货，寄回/交付后由买家确认完成',
    REFUNDED: '订单已退款',
    ARBITRATING: '平台正在介入处理退款纠纷',
    CLOSED:
      {
        TIMEOUT: '超过 5 分钟未支付，订单已自动关闭',
        BUYER_CANCEL: '买家取消了订单，商品已重新上架',
        SELLER_CANCEL: '卖家取消了订单，款项原路退回',
        PAID_CANCEL: '付款后取消，款项原路退回'
      }[order.value?.closeType] || '订单已关闭'
  }
  return map[s] || '订单状态更新中'
})

const canReportNoShow = computed(() => {
  if (!order.value?.meetTime) return false
  if (['COMPLETED', 'CLOSED', 'SETTLED', 'REFUNDED'].includes(order.value.status)) return false
  const t = new Date(String(order.value.meetTime).replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return false
  return nowTs.value >= t + 30 * 60 * 1000 && (isBuyer.value || isSeller.value)
})

const showActionBar = computed(() => {
  if (!order.value || readonly.value) return false
  const s = order.value.status
  if (isBuyer.value) return ['PENDING_PAY', 'WAIT_VERIFY', 'COMPLETED', 'REFUND_AGREED'].includes(s)
  if (isSeller.value) return s === 'WAIT_VERIFY'
  return false
})

const timeline = computed(() => {
  if (!order.value) return []
  return [
    { label: '提交订单', value: order.value.createdAt },
    { label: '买家付款', value: order.value.paidAt },
    { label: '完成核销', value: order.value.verifiedAt || order.value.completedAt },
    { label: '退款关闭', value: order.value.closedAt }
  ].filter((t) => t.value)
})

function meetTone(status) {
  if (status === 'CONFIRMED') return 'ok'
  if (status === 'CANCELED') return 'muted'
  return 'warn'
}

function fmtTime(input) {
  if (!input) return '待定'
  const raw = String(input).replace('T', ' ')
  return raw.slice(0, 16)
}

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
      // 核销码 15 分钟过期，归零后重新拉取而不是本地循环
      if (showCode.value) loadCode()
    }
  }, 1000)
}

async function loadCode() {
  if (!order.value || codeLoading.value) return
  codeLoading.value = true
  try {
    const res = await orderApi.verifyCode(order.value.orderNo)
    code.value = res?.code || ''
    codeError.value = ''
    if (res?.status && res.status !== order.value.status) order.value.status = res.status
    startCountdown(res?.remainSeconds)
  } catch {
    codeError.value = '核销码获取失败，可稍后重试'
    startCountdown(0)
  } finally {
    codeLoading.value = false
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

async function loadMeets() {
  try {
    const res = await orderApi.meets(orderNo)
    meets.value = Array.isArray(res) ? res : res?.records || []
  } catch {
    meets.value = []
  }
}

function restoreRefundId() {
  let saved = ''
  try {
    saved = localStorage.getItem(refundCacheKey) || ''
  } catch {
    saved = ''
  }
  refundId.value = String(order.value?.refundId || order.value?.refund?.id || saved || '')
  manualRefundId.value = refundId.value
}

async function load() {
  loading.value = true
  loadError.value = ''
  clearTimer()
  try {
    order.value = await orderApi.detail(orderNo)
    restoreRefundId()
    await Promise.all([loadExtras(), loadMeets()])
    if (showCode.value) loadCode()
  } catch {
    order.value = null
    loadError.value = '订单不存在或无权查看'
  } finally {
    loading.value = false
  }
}

function saveRefundId() {
  const id = String(manualRefundId.value || '').trim()
  if (!/^\d+$/.test(id)) return showToast('请输入数字退货单号')
  refundId.value = id
  try {
    localStorage.setItem(refundCacheKey, id)
  } catch {
    // 隐私模式忽略
  }
  showToast('退货单号已保存')
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

function scrollToVerify() {
  document.getElementById('verify-card')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function openChat() {
  if (!store.isLogin) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  try {
    const c = await conversationApi.create(order.value.listingId)
    if (c?.id) router.push({ path: `/chat/${c.id}`, query: { listingId: order.value.listingId } })
  } catch {
    // 请求层已经提示
  }
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
    showToast('支付成功，等待当面核销')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    paying.value = false
  }
}

async function cancel() {
  if (cancelling.value) return
  const paid = order.value?.status === 'WAIT_VERIFY'
  try {
    await showConfirmDialog({
      title: '取消订单',
      message: paid
        ? '该订单已支付。取消后订单关闭、款项原路退回；若在约定时间后取消或卖家已到场，可能扣除 3 点信誉分。确定取消？'
        : '取消后订单将关闭，确定吗？',
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

async function regenerateCode() {
  if (regenerating.value) return
  try {
    await showConfirmDialog({
      title: '重新生成核销码',
      message: '生成后旧核销码立即作废，请让买家重新出示。确定生成？',
      confirmButtonText: '生成'
    })
  } catch {
    return
  }
  regenerating.value = true
  try {
    const res = await orderApi.regenerateVerifyCode(orderNo)
    showToast(`新核销码 ${res?.code || ''} 已生成，请告知买家`)
  } catch {
    // 请求层已经提示
  } finally {
    regenerating.value = false
  }
}

async function submitVerify() {
  if (verifying.value) return
  if (!/^\d{4,6}$/.test(verifyForm.code.trim())) return showToast('请输入买家出示的核销码')
  if (!verifyForm.signPhotoUrl) return showToast('请上传签到照片')
  verifying.value = true
  try {
    await orderApi.verify(orderNo, {
      code: verifyForm.code.trim(),
      signPhotoUrl: verifyForm.signPhotoUrl
    })
    showToast('核销成功，交易已完成')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    verifying.value = false
  }
}

async function applyRefund() {
  if (refunding.value) return
  if (!refundForm.reason.trim()) return showToast('请填写退款原因')
  refunding.value = true
  try {
    const res = await orderApi.refund(orderNo, {
      reason: refundForm.reason.trim(),
      photoUrl: refundForm.photoUrl,
      refundType: refundForm.type
    })
    if (res?.id) {
      refundId.value = String(res.id)
      manualRefundId.value = refundId.value
      try {
        localStorage.setItem(refundCacheKey, refundId.value)
      } catch {
        // 忽略存储失败
      }
    }
    refundPopup.value = false
    showToast('退款申请已提交，等待卖家处理')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    refunding.value = false
  }
}

async function agreeRefund() {
  if (!refundId.value) return showToast('缺少退货单号')
  try {
    await showConfirmDialog({
      title: '同意退货',
      message: '同意后买家可寄回/交付教材，确认后订单将退款。',
      confirmButtonText: '同意'
    })
  } catch {
    return
  }
  agreeing.value = true
  try {
    await orderApi.agreeRefund(refundId.value)
    showToast('已同意退款')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    agreeing.value = false
  }
}

async function rejectRefund() {
  if (!refundId.value) return showToast('缺少退货单号')
  try {
    await showConfirmDialog({
      title: '拒绝退款',
      message: '拒绝后买家可申请平台介入，确定拒绝这笔退款吗？',
      confirmButtonText: '拒绝退款'
    })
  } catch {
    return
  }
  rejecting.value = true
  try {
    await orderApi.rejectRefund(refundId.value, { reason: '卖家认为教材与描述一致，拒绝退款' })
    showToast('已拒绝退款')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    rejecting.value = false
  }
}

async function finishRefund() {
  if (!refundId.value) return showToast('缺少退货单号，无法确认')
  try {
    await showConfirmDialog({
      title: '确认退货完成',
      message: '确认已收到卖家退回的教材？确认后订单退款并关闭。',
      confirmButtonText: '确认完成'
    })
  } catch {
    return
  }
  finishing.value = true
  try {
    await orderApi.finishRefund(refundId.value)
    showToast('退款已完成')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    finishing.value = false
  }
}

async function requestArbitration() {
  if (!refundId.value) return showToast('缺少退货单号')
  try {
    await showConfirmDialog({
      title: '申请平台介入',
      message: '平台将查看双方凭证并作出裁决，确定申请吗？',
      confirmButtonText: '申请介入'
    })
  } catch {
    return
  }
  arbitrating.value = true
  try {
    await orderApi.arbitrateRefund(refundId.value, { note: '买家申请平台介入退款纠纷' })
    showToast('已申请平台介入')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    arbitrating.value = false
  }
}

function openMeetPopup() {
  meetForm.type = ['REFUNDING', 'REFUND_AGREED'].includes(order.value?.status) ? 'RETURN' : 'TRADE'
  meetForm.place = order.value?.meetPlace || listing.value?.dorm || ''
  meetForm.datetime = ''
  meetPopup.value = true
}

async function createMeet() {
  if (creatingMeet.value) return
  if (!meetForm.place.trim()) return showToast('请填写约见地点')
  if (!meetForm.datetime) return showToast('请选择约见时间')
  const time = meetForm.datetime.length === 16 ? `${meetForm.datetime}:00` : meetForm.datetime
  creatingMeet.value = true
  try {
    await orderApi.createMeet(orderNo, {
      type: meetForm.type,
      place: meetForm.place.trim(),
      time
    })
    meetPopup.value = false
    showToast('已发起约见，等待对方确认')
    await loadMeets()
  } catch {
    // 请求层已经提示
  } finally {
    creatingMeet.value = false
  }
}

async function confirmMeet(m, agreed) {
  if (confirmingId.value) return
  let note = ''
  if (!agreed) {
    try {
      await showConfirmDialog({
        title: '拒绝约见',
        message: '拒绝后需要重新发起约见，确定拒绝吗？',
        confirmButtonText: '拒绝'
      })
      note = '时间不合适，请重新约'
    } catch {
      return
    }
  }
  confirmingId.value = m.id
  try {
    await orderApi.confirmMeet(m.id, { agreed, note })
    showToast(agreed ? '已同意约见' : '已拒绝约见')
    await Promise.all([loadMeets(), load()])
  } catch {
    // 请求层已经提示
  } finally {
    confirmingId.value = null
  }
}

async function reportNoShow() {
  if (noShowBusy.value) return
  try {
    await showConfirmDialog({
      title: '上报爽约',
      message: '确认对方在约定时间 30 分钟后仍未到场？上报成功后对方将扣 5 信誉分。',
      confirmButtonText: '确认上报'
    })
  } catch {
    return
  }
  noShowBusy.value = true
  try {
    const res = await orderApi.reportNoShow(orderNo, { note: '约定时间 30 分钟后对方未到场' })
    showToast(`已上报，对方信用分 ${res?.creditDelta ?? -5}`)
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    noShowBusy.value = false
  }
}

onMounted(() => {
  load()
  clock = setInterval(() => {
    nowTs.value = Date.now()
  }, 30000)
})

onBeforeUnmount(() => {
  clearTimer()
  if (clock) clearInterval(clock)
  clock = null
})
</script>

<style scoped>
.order-detail-page {
  background: var(--bg);
  padding-bottom: 96px;
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
  padding: 0;
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
.status-hero.danger {
  background: linear-gradient(135deg, #b54a3a, #d2644f);
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
  margin: 2px 0 8px;
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
.card {
  margin-top: 12px;
}
.card-title {
  margin: 0 0 10px;
  font-size: 15px;
}
.tip-line {
  margin: 4px 0 8px;
  font-size: 12px;
  line-height: 1.6;
}
.submit-btn {
  margin-top: 12px;
}
.tag.info {
  color: var(--brand);
  background: var(--brand-weak);
}
.sign-wrap {
  max-width: 160px;
  margin-top: 10px;
}
.refund-reason {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--ink-2);
  line-height: 1.5;
}
.refund-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
.refund-id-fix {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fff8e8;
  border-radius: 8px;
}
.refund-id-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.refund-id-row :deep(.van-cell) {
  flex: 1;
  padding: 6px 8px;
  background: #fff;
  border-radius: 8px;
}
.book-card {
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
.meet-item {
  padding: 10px 0;
  border-bottom: 1px dashed var(--line);
}
.meet-item:last-of-type {
  border-bottom: 0;
}
.meet-item strong {
  font-size: 13px;
}
.meet-line {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ink-2);
  display: flex;
  align-items: center;
  gap: 4px;
}
.meet-note {
  margin: 4px 0 0;
  font-size: 11px;
}
.meet-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 6px;
}
.no-show {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
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
.contact-row {
  margin-top: 12px;
  display: flex;
  justify-content: center;
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
.popup {
  padding: 16px 14px calc(14px + env(safe-area-inset-bottom, 0px));
}
.popup-head {
  font-size: 16px;
  margin-bottom: 8px;
}
.refund-photo {
  max-width: 160px;
  margin-top: 10px;
}
.meet-type {
  padding: 4px 0 10px;
}
.native-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}
.native-field span {
  color: var(--ink-2);
}
.native-field input {
  border: 0;
  background: var(--bg);
  border-radius: 6px;
  padding: 6px 8px;
  color: var(--ink);
  font-size: 13px;
  outline: none;
}
</style>

<template>
  <div class="page detail-page">
    <van-nav-bar title="教材详情" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div v-if="loading" class="container loading-wrap">
      <div class="skeleton loading-cover"></div>
      <div class="skeleton loading-line"></div>
      <div class="skeleton loading-line short"></div>
    </div>

    <template v-else-if="detail">
      <div class="container">
        <div class="gallery">
          <van-swipe v-if="gallery.length" :autoplay="4000" :show-indicators="gallery.length > 1" indicator-color="#fff">
            <van-swipe-item v-for="(url, idx) in gallery" :key="url + idx">
              <img :src="url" :alt="`教材图片 ${idx + 1}`" @error="onImgError" />
            </van-swipe-item>
          </van-swipe>
          <div v-else class="gallery-fallback">
            <span>{{ (listing?.title || '书').slice(0, 1) }}</span>
            <small>卖家暂未上传图片</small>
          </div>
          <span v-if="listing?.status && listing.status !== 'ON_SALE'" class="sold-mask">
            {{ listingStatusText(listing.status) }}
          </span>
        </div>

        <section v-if="listing?.videoUrl" class="card video-card">
          <h2 class="card-title">展示视频</h2>
          <video :src="listing.videoUrl" controls playsinline preload="metadata" class="detail-video" />
        </section>

        <section class="card main-card">
          <div class="price-row">
            <span class="price big">{{ money(listing?.price) }}</span>
            <del v-if="listing?.originalPrice" class="origin">原价 {{ money(listing.originalPrice) }}</del>
            <span v-if="condition" class="tag">{{ conditionText(condition) }}</span>
          </div>
          <h1 class="title">{{ listing?.title || textbook?.title || '未命名教材' }}</h1>
          <div class="meta-row">
            <span class="meta"><van-icon name="location-o" /> {{ listing?.dorm || '校内自提' }}</span>
            <span v-if="listing?.publishedAt" class="meta">{{ relTime(listing.publishedAt) }}发布</span>
            <span v-if="Number(listing?.viewCount) > 0" class="meta">{{ listing.viewCount }} 次浏览</span>
          </div>
          <p class="desc">{{ listing?.description || '卖家没有填写成色描述，可以下单后到自提点当面确认。' }}</p>
        </section>

        <section class="card seller-card">
          <div class="seller">
            <div class="avatar">
              <img
                v-if="seller?.avatar && !avatarFailed"
                :src="seller.avatar"
                alt="卖家头像"
                @error="avatarFailed = true"
              />
              <span v-else>{{ (seller?.nickname || '同学').slice(0, 1) }}</span>
            </div>
            <div class="seller-main">
              <div class="seller-name">
                <strong>{{ seller?.nickname || '同校同学' }}</strong>
                <span v-if="seller?.verifyState === 'VERIFIED'" class="tag">已认证</span>
              </div>
              <p class="muted">
                {{ [seller?.college, seller?.dorm].filter(Boolean).join(' · ') || '校内同学' }}
              </p>
            </div>
            <div class="credit">
              <strong>{{ seller?.creditScore ?? '--' }}</strong>
              <small>信用分</small>
            </div>
          </div>
        </section>

        <section v-if="bookRows.length" class="card">
          <h2 class="card-title">教材信息</h2>
          <dl class="info-list">
            <div v-for="row in bookRows" :key="row.label" class="info-row">
              <dt>{{ row.label }}</dt>
              <dd>{{ row.value }}</dd>
            </div>
            <div v-if="course?.name" class="info-row">
              <dt>课程</dt>
              <dd>{{ course.name }}<span v-if="course.teacher" class="muted"> · {{ course.teacher }}</span></dd>
            </div>
          </dl>
        </section>

        <PriceIndex v-if="pi" :data="pi" />

        <p class="safe-tip">
          <van-icon name="shield-o" />
          建议在自提点当面验书，确认无误后再核销；平台资金在核销前由担保交易托管。
        </p>
      </div>

      <div class="action-bar">
        <div class="action-inner">
          <button class="fav-btn" type="button" :class="{ on: favored }" @click="toggleFavorite">
            <van-icon :name="favored ? 'star' : 'star-o'" size="20" />
            <span>{{ favored ? '已收藏' : '收藏' }}</span>
          </button>

          <template v-if="isOwner">
            <span class="owner-tip">这是我发布的</span>
            <van-button
              v-if="listing?.status === 'ON_SALE'"
              size="small"
              round
              plain
              type="danger"
              :loading="offlining"
              @click="offline"
            >
              下架
            </van-button>
          </template>
          <template v-else>
            <van-button
              class="chat-btn"
              size="small"
              round
              plain
              icon="chat-o"
              :loading="chatBusy"
              @click="openChat"
            >
              聊一聊
            </van-button>
            <van-button
              class="want-btn"
              round
              type="primary"
              :disabled="listing?.status !== 'ON_SALE'"
              @click="openBuy"
            >
              {{ listing?.status === 'ON_SALE' ? '我想要' : listingStatusText(listing?.status) }}
            </van-button>
          </template>
        </div>
      </div>

      <van-popup v-model:show="pointPopup" position="bottom" round :style="{ maxHeight: '78%' }">
        <div class="popup">
          <div class="popup-head between">
            <strong>选择自提点</strong>
            <van-icon name="cross" size="18" @click="pointPopup = false" />
          </div>
          <p class="muted popup-hint">下单后请到该自提点与卖家当面验书，并出示核销码。</p>

          <div v-if="pointLoading" class="popup-loading">
            <van-loading size="22">正在加载自提点</van-loading>
          </div>
          <EmptyState v-else-if="!points.length" text="暂时没有可用的自提点" icon="📍">
            <van-button size="small" round type="primary" @click="loadPoints">重新加载</van-button>
          </EmptyState>
          <van-radio-group v-else v-model="selectedPointId" class="point-list">
            <label v-for="p in points" :key="p.id" class="point-item" :class="{ active: selectedPointId === p.id }">
              <div class="point-main">
                <div class="between">
                  <strong>{{ p.name }}</strong>
                  <span v-if="p.distance !== null && p.distance !== undefined" class="tag muted">
                    {{ distanceText(p.distance) }}
                  </span>
                </div>
                <p class="muted">{{ p.address }}</p>
                <p class="muted hours">
                  <van-icon name="clock-o" /> {{ p.openTime || '--' }} - {{ p.closeTime || '--' }}
                  <span v-if="p.capacity"> · 容量 {{ p.capacity }} 册</span>
                </p>
              </div>
              <van-radio :name="p.id" />
            </label>
          </van-radio-group>

          <div class="popup-foot">
            <van-button
              block
              round
              type="primary"
              :loading="creating"
              :loading-text="'下单中'"
              :disabled="!selectedPointId || pointLoading"
              @click="createOrder"
            >
              确认下单
            </van-button>
          </div>
        </div>
      </van-popup>
    </template>

    <div v-else class="container">
      <div class="card empty-wrap">
        <EmptyState :text="loadError || '教材不存在或已下架'" icon="📕">
          <van-button size="small" round type="primary" @click="load">重新加载</van-button>
        </EmptyState>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { catalogApi, conversationApi, listingApi, orderApi, pickupApi } from '@/api'
import { useUserStore } from '@/store/user'
import { conditionText, listingStatusText, money, newRequestId, relTime } from '@/utils/format'
import PriceIndex from '@/components/PriceIndex.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const id = route.params.id
const detail = ref(null)
const loading = ref(true)
const loadError = ref('')
const pi = ref(null)
const avatarFailed = ref(false)

const pointPopup = ref(false)
const points = ref([])
const pointLoading = ref(false)
const selectedPointId = ref(null)
const creating = ref(false)

const favBusy = ref(false)
const offlining = ref(false)
const chatBusy = ref(false)

const listing = computed(() => detail.value?.listing || null)
const textbook = computed(() => detail.value?.textbook || null)
const course = computed(() => detail.value?.course || null)
const seller = computed(() => detail.value?.seller || null)
const condition = computed(() => listing.value?.conditionLevel || listing.value?.condition || '')
const favored = computed(() => Boolean(detail.value?.favored))

const isOwner = computed(
  () =>
    Boolean(store.userId) &&
    Boolean(seller.value?.id) &&
    Number(seller.value.id) === Number(store.userId)
)

const gallery = computed(() => {
  const raw = detail.value?.images
  const urls = Array.isArray(raw)
    ? raw.map((img) => (typeof img === 'string' ? img : img?.url)).filter(Boolean)
    : []
  if (!urls.length && listing.value?.coverUrl) urls.push(listing.value.coverUrl)
  return urls
})

const bookRows = computed(() => {
  const b = textbook.value
  if (!b) return []
  return [
    { label: '书名', value: b.title },
    { label: '作者', value: b.author },
    { label: '出版社', value: b.publisher },
    { label: '版次', value: b.edition },
    { label: 'ISBN', value: b.isbn13 || b.isbn10 },
    { label: '定价', value: b.price ? money(b.price) : '' }
  ].filter((row) => row.value)
})

function onImgError(e) {
  // 图片链接失效时隐藏，避免出现破图图标
  e.target.style.visibility = 'hidden'
}

function distanceText(meters) {
  const m = Number(meters)
  if (!Number.isFinite(m)) return ''
  return m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(1)} km`
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listingApi.detail(id)
    detail.value = res
    const bookId = res?.listing?.textbookId
    if (bookId) loadPriceIndex(bookId)
  } catch {
    detail.value = null
    loadError.value = '教材不存在或已下架'
  } finally {
    loading.value = false
  }
}

async function loadPriceIndex(bookId) {
  try {
    pi.value = await catalogApi.priceIndex(bookId)
  } catch {
    // 价格指数是锦上添花，失败不影响主流程
    pi.value = null
  }
}

async function loadPoints() {
  pointLoading.value = true
  try {
    const res = await pickupApi.list()
    points.value = Array.isArray(res) ? res : res?.records || []
    if (!selectedPointId.value && points.value.length) selectedPointId.value = points.value[0].id
  } catch {
    points.value = []
  } finally {
    pointLoading.value = false
  }
}

async function toggleFavorite() {
  if (!store.isLogin) return goLogin()
  if (favBusy.value) return
  favBusy.value = true
  try {
    const res = await listingApi.favorite(id)
    const next = typeof res === 'boolean' ? res : res?.favored
    if (detail.value) detail.value.favored = typeof next === 'boolean' ? next : !detail.value.favored
    showToast(detail.value.favored ? '已加入收藏' : '已取消收藏')
  } catch {
    // 请求层已经提示
  } finally {
    favBusy.value = false
  }
}

async function openChat() {
  if (!store.isLogin) return goLogin()
  if (isOwner.value) return showToast('这是你自己发布的教材')
  if (chatBusy.value) return
  chatBusy.value = true
  try {
    const c = await conversationApi.create(Number(id))
    if (c?.id) {
      router.push({
        path: `/chat/${c.id}`,
        query: {
          listingId: id,
          title: listing.value?.title || '',
          name: listing.value?.sellerNickname || '卖家'
        }
      })
    }
  } catch {
    // 请求层已经提示
  } finally {
    chatBusy.value = false
  }
}

async function openBuy() {
  if (!store.isLogin) return goLogin()
  if (isOwner.value) return showToast('不能购买自己发布的教材')
  if (listing.value?.status !== 'ON_SALE') return showToast('该教材已下架或已被下单')
  pointPopup.value = true
  if (!points.value.length) await loadPoints()
}

async function createOrder() {
  if (creating.value) return
  if (!selectedPointId.value) return showToast('请选择自提点')
  creating.value = true
  try {
    const order = await orderApi.create({
      listingId: Number(id),
      pointId: selectedPointId.value,
      requestId: newRequestId()
    })
    pointPopup.value = false
    showToast('下单成功，请尽快支付')
    if (order?.orderNo) router.push(`/order/${order.orderNo}`)
    else router.push('/orders')
  } catch {
    // 请求层已经提示，保留弹层方便重试
  } finally {
    creating.value = false
  }
}

async function offline() {
  try {
    await showConfirmDialog({
      title: '下架教材',
      message: '下架后同学们将无法再看到这本教材，确定吗？',
      confirmButtonText: '下架'
    })
  } catch {
    return
  }
  offlining.value = true
  try {
    await listingApi.offline(id)
    showToast('已下架')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    offlining.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-page {
  padding-bottom: 96px;
  background: var(--bg);
}
.loading-wrap {
  padding-top: 12px;
}
.loading-cover {
  height: 240px;
  border-radius: var(--radius);
}
.loading-line {
  height: 16px;
  margin-top: 16px;
}
.loading-line.short {
  width: 50%;
}
.video-card {
  margin-top: 12px;
}
.detail-video {
  width: 100%;
  border-radius: 10px;
  background: #000;
}
.gallery {
  position: relative;
  margin-top: 12px;
  border-radius: var(--radius);
  overflow: hidden;
  background: #eef4f1;
  aspect-ratio: 4 / 3;
  max-height: 340px;
}
.gallery :deep(.van-swipe) {
  height: 100%;
}
.gallery :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.gallery-fallback {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #7fb69b;
  background: linear-gradient(135deg, #eaf4ef, #f6f8f7);
}
.gallery-fallback span {
  font-size: 52px;
  font-weight: 800;
}
.gallery-fallback small {
  color: var(--muted);
}
.sold-mask {
  position: absolute;
  inset: 0;
  background: rgba(28, 34, 38, 0.45);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  display: grid;
  place-items: center;
  letter-spacing: 2px;
}
.main-card {
  margin-top: 12px;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.price.big {
  font-size: 26px;
}
.origin {
  color: var(--muted);
  font-size: 12px;
}
.title {
  margin: 8px 0 6px;
  font-size: 19px;
  line-height: 1.45;
  font-weight: 700;
}
.meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--muted);
  font-size: 12px;
}
.meta {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.desc {
  margin: 12px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  color: var(--ink-2);
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}
.seller-card {
  margin-top: 12px;
}
.seller {
  display: flex;
  align-items: center;
  gap: 12px;
}
.avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 20px;
  font-weight: 700;
  flex: none;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.seller-main {
  flex: 1;
  min-width: 0;
}
.seller-name {
  display: flex;
  align-items: center;
  gap: 6px;
}
.seller-main p {
  margin: 2px 0 0;
}
.credit {
  text-align: center;
  padding-left: 10px;
  border-left: 1px solid var(--line);
  flex: none;
}
.credit strong {
  display: block;
  color: var(--brand);
  font-size: 18px;
  line-height: 1.2;
}
.credit small {
  color: var(--muted);
  font-size: 11px;
}
.card-title {
  margin: 0 0 10px;
  font-size: 15px;
}
.info-list {
  margin: 0;
  display: grid;
  gap: 8px;
}
.info-row {
  display: flex;
  gap: 12px;
  font-size: 13px;
  line-height: 1.5;
}
.info-row dt {
  flex: none;
  width: 56px;
  color: var(--muted);
}
.info-row dd {
  margin: 0;
  flex: 1;
  min-width: 0;
  word-break: break-word;
}
.safe-tip {
  margin: 16px 4px 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
  display: flex;
  gap: 6px;
  align-items: flex-start;
}
.safe-tip :deep(.van-icon) {
  margin-top: 2px;
  color: var(--brand);
}
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  background: #fff;
  border-top: 1px solid var(--line);
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom, 0px));
}
.action-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.fav-btn {
  border: 0;
  background: none;
  color: var(--muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  padding: 0 6px;
  cursor: pointer;
}
.fav-btn.on {
  color: #e5a000;
}
.owner-tip {
  flex: 1;
  text-align: right;
  color: var(--muted);
  font-size: 13px;
}
.want-btn {
  flex: 1;
}
.chat-btn {
  flex: none;
}
.popup {
  padding: 16px 14px calc(14px + env(safe-area-inset-bottom, 0px));
  display: flex;
  flex-direction: column;
  max-height: 78vh;
}
.popup-head {
  font-size: 16px;
}
.popup-hint {
  margin: 6px 0 12px;
}
.popup-loading {
  padding: 32px 0;
  display: grid;
  place-items: center;
}
.point-list {
  overflow-y: auto;
  display: grid;
  gap: 8px;
  padding: 2px;
}
.point-item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
}
.point-item.active {
  border-color: var(--brand);
  background: var(--brand-weak);
}
.point-main {
  flex: 1;
  min-width: 0;
}
.point-main p {
  margin: 2px 0 0;
  font-size: 12px;
}
.hours {
  display: flex;
  align-items: center;
  gap: 3px;
}
.popup-foot {
  padding-top: 12px;
}
.empty-wrap {
  margin-top: 16px;
  padding: 0;
}
.empty-wrap :deep(.empty) {
  padding: 42px 16px;
}
</style>

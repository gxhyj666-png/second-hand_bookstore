<template>
  <div class="page review-page">
    <van-nav-bar title="评价卖家" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section class="card head-card">
        <div class="thumb">
          <img v-if="cover" :src="cover" :alt="listingTitle" @error="onImgError" />
          <span v-else>{{ (listingTitle || '书').slice(0, 1) }}</span>
        </div>
        <div class="head-main">
          <strong class="clamp-2">{{ listingTitle || '本次交易的教材' }}</strong>
          <p class="muted">订单 {{ orderNo }}</p>
        </div>
      </section>

      <section class="card rate-card">
        <h1>这次交易体验如何？</h1>
        <p class="muted">你的评价会影响卖家的信用分，请客观填写。</p>
        <van-rate v-model="rating" :size="34" color="#f7b500" void-color="#e3e6e8" allow-half />
        <span class="rate-text">{{ ratingText }}</span>
      </section>

      <van-cell-group inset class="form-group">
        <van-field
          v-model="content"
          type="textarea"
          rows="4"
          autosize
          maxlength="200"
          show-word-limit
          label="评价内容"
          label-align="top"
          placeholder="教材和描述一致吗？卖家沟通是否顺畅？写下来帮助其他同学。"
        />
      </van-cell-group>

      <van-cell-group inset class="form-group">
        <van-cell title="匿名评价" label="开启后其他同学看不到你的昵称">
          <template #right-icon>
            <van-switch v-model="anonymous" size="20" />
          </template>
        </van-cell>
      </van-cell-group>

      <div class="submit-wrap">
        <van-button block round type="primary" :loading="submitting" loading-text="提交中" @click="submit">
          提交评价
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listingApi, orderApi, socialApi } from '@/api'

const route = useRoute()
const router = useRouter()

const orderNo = String(route.params.orderNo || '')
const rating = ref(0)
const content = ref('')
const anonymous = ref(false)
const submitting = ref(false)

const listingTitle = ref('')
const cover = ref('')

const ratingText = computed(() => {
  const map = { 1: '很差', 2: '较差', 3: '一般', 4: '满意', 5: '非常满意' }
  return map[rating.value] || '点击星星评分'
})

function onImgError(e) {
  e.target.style.visibility = 'hidden'
}

async function loadSummary() {
  try {
    const order = await orderApi.detail(orderNo)
    if (!order?.listingId) return
    const d = await listingApi.detail(order.listingId)
    listingTitle.value = d?.listing?.title || d?.textbook?.title || ''
    cover.value = d?.listing?.coverUrl || ''
  } catch {
    // 只用于展示摘要，失败不影响评价
  }
}

async function submit() {
  if (submitting.value) return
  if (!rating.value) return showToast('请先选择星级')
  submitting.value = true
  try {
    await socialApi.createReview({
      orderNo,
      rating: rating.value,
      content: content.value.trim(),
      anonymous: anonymous.value
    })
    showToast('评价成功，感谢反馈')
    router.replace(`/order/${orderNo}`)
  } catch {
    // 请求层已经提示
  } finally {
    submitting.value = false
  }
}

onMounted(loadSummary)
</script>

<style scoped>
.review-page {
  background: var(--bg);
}
.head-card {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.thumb {
  width: 48px;
  height: 62px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
  display: grid;
  place-items: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 20px;
  font-weight: 700;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.head-main {
  flex: 1;
  min-width: 0;
}
.head-main strong {
  font-size: 14px;
  line-height: 1.45;
}
.head-main p {
  margin: 4px 0 0;
  font-size: 12px;
}
.rate-card {
  margin-top: 12px;
  text-align: center;
  padding: 22px 16px;
}
.rate-card h1 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 800;
}
.rate-card p {
  margin: 0 0 16px;
  font-size: 12px;
}
.rate-text {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: var(--muted);
}
.form-group {
  margin: 12px 0 0;
  overflow: hidden;
}
.submit-wrap {
  padding: 22px 0 28px;
}
</style>

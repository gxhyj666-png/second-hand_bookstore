<template>
  <div class="page shop-page">
    <van-nav-bar :title="data.shopName || '店铺'" left-arrow fixed placeholder safe-area-inset-top @click-left="goBack" />

    <div v-if="loading" class="container">
      <div class="skeleton line-lg"></div>
      <div class="skeleton line"></div>
    </div>

    <div v-else-if="!data.merchantId" class="container">
      <EmptyState :text="loadError || '店铺不存在或未通过审核'" icon="🏪">
        <van-button size="small" round type="primary" @click="load">重新加载</van-button>
      </EmptyState>
    </div>

    <template v-else>
      <section class="hero">
        <div class="avatar">{{ data.shopName.slice(0, 1) }}</div>
        <div class="hero-main">
          <h1>{{ data.shopName }}</h1>
          <p class="muted">
            <span class="badge">已认证商户</span>
            <span v-if="data.since"> · 入驻 {{ shortDate(data.since) }}</span>
          </p>
        </div>
      </section>

      <div class="grid">
        <div class="cell"><strong>{{ data.stats?.onSale ?? 0 }}</strong><span>在售</span></div>
        <div class="cell"><strong>{{ data.stats?.sold ?? 0 }}</strong><span>已成交</span></div>
        <div class="cell"><strong>{{ data.stats?.views ?? 0 }}</strong><span>总浏览</span></div>
        <div class="cell"><strong>{{ data.stats?.favorites ?? 0 }}</strong><span>总收藏</span></div>
        <div class="cell"><strong>{{ data.stats?.avgRating ?? '0.0' }}</strong><span>{{ data.stats?.reviews ?? 0 }} 条评价</span></div>
      </div>

      <section class="card">
        <div class="between">
          <h2 class="card-title">在售教材（{{ data.listingTotal ?? 0 }}）</h2>
          <span class="muted">点书看详情</span>
        </div>
        <div v-if="data.listings?.length" class="books">
          <div v-for="l in data.listings" :key="l.id" class="book" @click="router.push(`/listing/${l.id}`)">
            <div class="cover">
              <img v-if="l.coverUrl" :src="l.coverUrl" :alt="l.title" @error="onImgError" />
              <span v-else>{{ l.title.slice(0, 1) }}</span>
            </div>
            <strong class="clamp-2">{{ l.title }}</strong>
            <span class="price">{{ money(l.price) }}</span>
            <span class="muted small">{{ conditionText(l.conditionLevel) }}</span>
          </div>
        </div>
        <van-empty v-else description="这家店暂时没有在售教材" />
        <van-button
          v-if="(data.listingTotal ?? 0) > (data.listings?.length ?? 0)"
          block
          round
          plain
          type="primary"
          size="small"
          :loading="moreLoading"
          @click="loadMore"
        >
          查看更多
        </van-button>
      </section>

      <section v-if="data.reviews?.length" class="card">
        <h2 class="card-title">买家评价</h2>
        <div v-for="r in data.reviews" :key="r.id" class="review">
          <div class="between">
            <strong>{{ r.reviewer || '匿名同学' }}</strong>
            <van-rate :model-value="r.rating || 5" readonly size="12" />
          </div>
          <p class="muted">{{ r.content || '该买家没有填写评价内容' }}</p>
          <p class="muted small">{{ shortDate(r.createdAt) }}</p>
        </div>
      </section>
    </template>

    <MerchantTabbar v-if="isMine" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { shopApi } from '@/api'
import { conditionText, money, shortDate } from '@/utils/format'
import { useUserStore } from '@/store/user'
import EmptyState from '@/components/EmptyState.vue'
import MerchantTabbar from '@/components/MerchantTabbar.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const merchantId = computed(() => Number(route.params.id || 0))
const isMine = computed(() => Boolean(store.user?.merchantId) && Number(store.user.merchantId) === merchantId.value)

const data = reactive({})
const loading = ref(true)
const moreLoading = ref(false)
const loadError = ref('')
const page = ref(1)
const PAGE_SIZE = 12

function onImgError(e) {
  e.target.style.visibility = 'hidden'
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

async function load() {
  loading.value = true
  loadError.value = ''
  page.value = 1
  try {
    const res = await shopApi.get(merchantId.value, { page: 1, size: PAGE_SIZE })
    Object.assign(data, res || {})
  } catch {
    Object.keys(data).forEach((k) => delete data[k])
    loadError.value = '店铺不存在或未通过审核'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  moreLoading.value = true
  try {
    const next = page.value + 1
    const res = await shopApi.get(merchantId.value, { page: next, size: PAGE_SIZE })
    data.listings = (data.listings || []).concat(res?.listings || [])
    page.value = next
  } catch {
    // 请求层已提示
  } finally {
    moreLoading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.shop-page {
  background: var(--bg);
  padding-bottom: 70px;
}
.hero {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px;
  background: #fff;
}
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: linear-gradient(135deg, #7ec8a9, #4f9d86);
  color: #fff;
  font-size: 24px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-main h1 {
  margin: 0 0 4px;
  font-size: 18px;
}
.hero-main p {
  margin: 0;
  font-size: 12px;
}
.badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 999px;
  background: #e8f6f0;
  color: #2f8f6d;
  font-size: 11px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  padding: 12px;
  background: #fff;
  margin-bottom: 8px;
}
.cell {
  text-align: center;
  padding: 6px 2px;
  border-radius: 10px;
  background: #f7f9fc;
}
.cell strong {
  display: block;
  font-size: 15px;
}
.cell span {
  font-size: 11px;
  color: #8a93a0;
}
.container {
  padding: 12px;
}
.card {
  margin-bottom: 10px;
}
.books {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 8px;
}
.book {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 6px;
  cursor: pointer;
}
.cover {
  height: 92px;
  border-radius: 6px;
  background: #f1f4f8;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #b7c0cc;
  font-size: 26px;
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.book strong {
  font-size: 12px;
  line-height: 1.35;
}
.price {
  color: #e2703a;
  font-weight: 700;
  font-size: 13px;
}
.small {
  font-size: 11px;
}
.review {
  padding: 8px 0;
  border-top: 1px dashed var(--line);
}
.review p {
  margin: 4px 0 0;
  font-size: 12px;
}
</style>

<template>
  <div class="page">
    <van-nav-bar title="同学主页" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <van-loading v-if="loading && !profile" class="center" />
    <van-empty v-else-if="!profile" description="这位同学不存在或已注销" />

    <div v-else class="container">
      <header class="hero">
        <div class="avatar">
          <img v-if="profile.avatar" :src="profile.avatar" :alt="profile.nickname" />
          <span v-else>{{ (profile.nickname || '同').slice(0, 1) }}</span>
        </div>
        <div class="who">
          <h1>
            {{ profile.nickname }}
            <span v-if="profile.merchantId" class="shop-tag">商户</span>
          </h1>
          <p class="muted">{{ [profile.college, profile.major, profile.grade && `${profile.grade}级`].filter(Boolean).join(' · ') || '还没有填写学院和专业' }}</p>
          <p v-if="profile.shopName" class="shop">{{ profile.shopName }}</p>
        </div>
        <div class="credit">
          <strong>{{ profile.creditScore }}</strong>
          <span>信用分</span>
        </div>
      </header>

      <div class="badges">
        <span class="badge" :class="{ on: profile.idVerified }">
          <van-icon :name="profile.idVerified ? 'passed' : 'idcard'" /> 实名认证
        </span>
        <span class="badge" :class="{ on: profile.studentVerified }">
          <van-icon :name="profile.studentVerified ? 'passed' : 'notes-o'" /> 学生认证
        </span>
        <span class="badge" :class="{ on: profile.faceVerified }">
          <van-icon :name="profile.faceVerified ? 'passed' : 'smile-o'" /> 人脸核验
        </span>
      </div>

      <div class="stats">
        <div class="stat"><strong>{{ profile.stats?.onSale || 0 }}</strong><span>在售</span></div>
        <div class="stat"><strong>{{ profile.stats?.sold || 0 }}</strong><span>已成交</span></div>
        <div class="stat"><strong>{{ profile.stats?.reviews || 0 }}</strong><span>评价</span></div>
        <div class="stat"><strong>{{ profile.stats?.avgRating || '0.0' }}</strong><span>平均分</span></div>
      </div>

      <section class="block">
        <div class="block-head">
          <h2>正在出售</h2>
          <span class="muted">共 {{ profile.stats?.onSale || 0 }} 件</span>
        </div>
        <van-empty v-if="!profile.listings?.length" description="暂时没有在售的教材" image-size="72" />
        <div v-else class="grid">
          <button v-for="item in profile.listings" :key="item.id" type="button" class="book" @click="router.push(`/listing/${item.id}`)">
            <div class="thumb">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" />
              <span v-else class="ph">{{ (item.title || '书').slice(0, 1) }}</span>
            </div>
            <p class="btitle">{{ item.title }}</p>
            <p class="bprice">¥{{ Number(item.price).toFixed(2) }}</p>
          </button>
        </div>
      </section>

      <section class="block">
        <div class="block-head">
          <h2>收到的评价</h2>
          <span class="muted">共 {{ profile.stats?.reviews || 0 }} 条</span>
        </div>
        <van-empty v-if="!profile.reviews?.length" description="还没有收到评价" image-size="72" />
        <div v-else class="reviews">
          <article v-for="r in profile.reviews" :key="r.id" class="review">
            <header>
              <van-rate :model-value="Number(r.rating) || 5" readonly :size="13" gutter="1" />
              <span class="muted">{{ formatDate(r.createdAt) }}</span>
            </header>
            <p>{{ r.content || '这位同学没有写文字评价' }}</p>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api'

const route = useRoute()
const router = useRouter()

const profile = ref(null)
const loading = ref(false)

function formatDate(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 10)
}

async function load() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    profile.value = await userApi.profile(id)
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<style scoped>
.page {
  background: var(--bg);
  min-height: 100vh;
}
.container {
  padding-bottom: 40px;
}
.center {
  display: block;
  margin: 60px auto;
}
.hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 16px 10px;
}
.avatar {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 24px;
  font-weight: 800;
  display: grid;
  place-items: center;
  flex: 0 0 62px;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.who {
  flex: 1;
  min-width: 0;
}
.who h1 {
  margin: 0 0 4px;
  font-size: 17px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.shop-tag {
  font-size: 10px;
  font-weight: 500;
  color: #fff;
  background: var(--brand);
  border-radius: 6px;
  padding: 1px 5px;
}
.who p {
  margin: 0;
  font-size: 12px;
}
.shop {
  color: var(--brand);
  margin-top: 2px !important;
}
.credit {
  text-align: center;
  flex: 0 0 54px;
}
.credit strong {
  display: block;
  font-size: 18px;
  color: var(--price, #e8541e);
}
.credit span {
  font-size: 10px;
  color: var(--ink-3, #94a3b8);
}
.badges {
  display: flex;
  gap: 8px;
  padding: 0 16px 12px;
  flex-wrap: wrap;
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--ink-3, #94a3b8);
  background: #f2f4f8;
  border-radius: 999px;
  padding: 3px 9px;
}
.badge.on {
  color: #16a34a;
  background: #e6f7ee;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 0 12px;
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px 0;
}
.stat {
  text-align: center;
}
.stat strong {
  display: block;
  font-size: 16px;
}
.stat span {
  font-size: 11px;
  color: var(--ink-3, #94a3b8);
}
.block {
  margin: 16px 12px 0;
}
.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}
.block-head h2 {
  margin: 0;
  font-size: 14px;
}
.muted {
  color: var(--ink-3, #94a3b8);
  font-size: 11px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.book {
  border: 0;
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 8px;
  text-align: left;
}
.thumb {
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 8px;
  overflow: hidden;
  background: var(--brand-weak);
  display: grid;
  place-items: center;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ph {
  color: var(--brand);
  font-weight: 800;
  font-size: 20px;
}
.btitle {
  margin: 6px 0 2px;
  font-size: 12px;
  line-height: 1.4;
  height: 34px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.bprice {
  margin: 0;
  color: var(--price, #e8541e);
  font-weight: 700;
  font-size: 13px;
}
.reviews {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.review {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 10px 12px;
}
.review header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.review p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>

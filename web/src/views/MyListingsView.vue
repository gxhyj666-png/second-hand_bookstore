<template>
  <div class="page">
    <van-nav-bar title="我的发布" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <van-tabs v-model:active="active" sticky offset-top="46" @change="reload">
      <van-tab v-for="t in TABS" :key="t.value" :title="t.label" :name="t.value" />
    </van-tabs>

    <div class="container">
      <div class="summary">
        <span>共 {{ total }} 件</span>
        <button type="button" class="link" @click="router.push('/publish')">
          <van-icon name="plus" /> 再发一本
        </button>
      </div>

      <van-loading v-if="loading && !list.length" class="center" />
      <van-empty v-else-if="!list.length" description="这里还没有书，去发布一本吧" />

      <div v-else class="items">
        <article v-for="item in list" :key="item.id" class="item" @click="router.push(`/listing/${item.id}`)">
          <div class="thumb">
            <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" />
            <span v-else class="ph">{{ (item.title || '书').slice(0, 1) }}</span>
          </div>
          <div class="info">
            <h3 class="title">{{ item.title }}</h3>
            <p class="meta">
              <span class="price">¥{{ Number(item.price).toFixed(2) }}</span>
              <span class="tag" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
            </p>
            <p class="meta muted">
              {{ conditionText(item.conditionLevel) }} · {{ item.viewCount || 0 }} 浏览 · {{ item.favoriteCount || 0 }} 收藏
            </p>
            <div class="ops" @click.stop>
              <van-button
                v-if="item.status === 'ON_SALE'"
                size="mini"
                plain
                @click="offline(item)"
              >下架</van-button>
              <van-button
                v-if="item.status === 'OFFLINE'"
                size="mini"
                plain
                type="primary"
                @click="online(item)"
              >重新上架</van-button>
              <van-button
                v-if="item.status === 'ON_SALE'"
                size="mini"
                plain
                type="primary"
                @click="editPrice(item)"
              >改价</van-button>
              <van-button
                v-if="item.status === 'SOLD'"
                size="mini"
                plain
                @click="router.push('/orders?role=seller')"
              >查看订单</van-button>
            </div>
          </div>
        </article>
      </div>

      <div v-if="hasMore && list.length" class="more">
        <van-button block plain :loading="loading" @click="loadMore">加载更多</van-button>
      </div>
    </div>

    <van-popup v-model:show="price.show" position="bottom" round :style="{ paddingBottom: '18px' }">
      <div class="price-sheet">
        <h3>修改价格</h3>
        <p class="muted">{{ price.title }}</p>
        <van-cell-group inset>
          <van-field v-model="price.value" type="number" label="售价" placeholder="请输入新的售价（元）">
            <template #button><span class="muted">元</span></template>
          </van-field>
        </van-cell-group>
        <div class="price-actions">
          <van-button block round plain @click="price.show = false">取消</van-button>
          <van-button block round type="primary" :loading="price.saving" @click="submitPrice">确定</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { listingApi } from '@/api'

const router = useRouter()

const TABS = [
  { label: '全部', value: 'ALL' },
  { label: '在售', value: 'ON_SALE' },
  { label: '交易中', value: 'LOCKED' },
  { label: '已下架', value: 'OFFLINE' },
  { label: '已售出', value: 'SOLD' }
]

const STATUS_TEXT = {
  ON_SALE: '在售',
  LOCKED: '交易中',
  OFFLINE: '已下架',
  SOLD: '已售出',
  REJECTED: '已驳回'
}

const CONDITION_TEXT = {
  NEW: '全新',
  LIKE_NEW: '几乎全新',
  GOOD: '良好',
  FAIR: '一般',
  POOR: '较差'
}

const active = ref('ALL')
const list = ref([])
const page = ref(1)
const total = ref(0)
const size = 10
const loading = ref(false)
const hasMore = ref(false)

const price = reactive({ show: false, saving: false, item: null, title: '', value: '' })

function statusText(s) {
  return STATUS_TEXT[s] || s || ''
}

function statusClass(s) {
  return { on: s === 'ON_SALE', lock: s === 'LOCKED', off: s === 'OFFLINE', sold: s === 'SOLD' }
}

function conditionText(c) {
  return CONDITION_TEXT[c] || c || ''
}

async function fetchPage(reset) {
  if (loading.value) return
  loading.value = true
  try {
    const p = reset ? 1 : page.value
    const res = await listingApi.mine({
      status: active.value === 'ALL' ? undefined : active.value,
      page: p,
      size
    })
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

async function offline(item) {
  try {
    await showConfirmDialog({ title: '下架商品', message: `确定下架《${item.title}》吗？下架后买家看不到。` })
  } catch {
    return
  }
  try {
    await listingApi.offline(item.id)
    item.status = 'OFFLINE'
    showToast('已下架')
  } catch {
    // 请求层已提示
  }
}

async function online(item) {
  try {
    await listingApi.online(item.id)
    item.status = 'ON_SALE'
    showToast('已重新上架')
  } catch {
    // 请求层已提示
  }
}

async function editPrice(item) {
  price.item = item
  price.title = item.title
  price.value = String(item.price)
  price.show = true
}

async function submitPrice() {
  const num = Number(price.value)
  if (!num || Number.isNaN(num) || num <= 0) return showToast('请输入大于 0 的价格')
  if (price.saving) return
  price.saving = true
  try {
    await listingApi.update(price.item.id, { price: num })
    price.item.price = num
    price.show = false
    showToast('价格已更新')
  } catch {
    // 请求层已提示
  } finally {
    price.saving = false
  }
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
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 4px;
  font-size: 12px;
  color: var(--ink-2);
}
.summary .link {
  border: 0;
  background: none;
  color: var(--brand);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.center {
  display: block;
  margin: 60px auto;
}
.items {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 12px 0;
}
.item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.thumb {
  width: 72px;
  height: 96px;
  flex: 0 0 72px;
  border-radius: 10px;
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
.thumb .ph {
  color: var(--brand);
  font-weight: 800;
  font-size: 24px;
}
.info {
  flex: 1;
  min-width: 0;
}
.title {
  margin: 0 0 6px;
  font-size: 14px;
  line-height: 1.4;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.meta {
  margin: 0 0 4px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.price {
  color: var(--price, #e8541e);
  font-weight: 700;
  font-size: 15px;
}
.tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  background: #f2f4f8;
  color: #666;
}
.tag.on {
  background: #e6f7ee;
  color: #16a34a;
}
.tag.lock {
  background: #fff4e5;
  color: #d97706;
}
.tag.off {
  background: #f2f4f8;
  color: #64748b;
}
.tag.sold {
  background: #eef2ff;
  color: #4f46e5;
}
.muted {
  color: var(--ink-3, #94a3b8);
}
.ops {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}
.more {
  padding: 14px 16px 0;
}
.price-sheet {
  padding: 16px 0 4px;
}
.price-sheet h3 {
  margin: 0 0 4px;
  font-size: 15px;
  text-align: center;
}
.price-sheet > .muted {
  margin: 0 0 12px;
  text-align: center;
  font-size: 12px;
  padding: 0 16px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.price-actions {
  display: flex;
  gap: 10px;
  padding: 18px 16px 0;
}
</style>

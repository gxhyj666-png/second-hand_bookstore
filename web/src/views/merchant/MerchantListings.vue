<template>
  <div class="page mch-list">
    <van-nav-bar title="商品管理" left-arrow @click-left="router.back()">
      <template #right>
        <span class="nav-op" @click="selecting = !selecting">{{ selecting ? '取消' : '批量' }}</span>
      </template>
    </van-nav-bar>

    <van-search v-model="keyword" placeholder="搜索书名" @search="reload" @clear="reload" />

    <van-tabs v-model:active="status" sticky @change="reload">
      <van-tab v-for="t in tabs" :key="t.value" :title="t.text" :name="t.value" />
    </van-tabs>

    <div v-if="selecting" class="batch-bar">
      <span>已选 {{ selected.length }} 件</span>
      <div>
        <van-button size="mini" round plain @click="openPrice">批量改价</van-button>
        <van-button size="mini" round plain type="danger" :disabled="!selected.length" @click="offlineBatch">批量下架</van-button>
      </div>
    </div>

    <van-empty v-if="!loading && !list.length" description="这个状态下没有商品" />

    <div v-for="item in list" :key="item.id" class="row">
      <van-checkbox v-if="selecting" v-model="item._checked" class="pick" @change="syncSelected" />
      <img v-if="item.coverUrl" :src="item.coverUrl" alt="" class="cover" />
      <div v-else class="cover cover-empty">书</div>
      <div class="main" @click="selecting ? null : router.push(`/listing/${item.id}`)">
        <p class="title">{{ item.title }}</p>
        <p class="meta">
          <span class="price">{{ money(item.price) }}</span>
          <span>{{ conditionText(item.conditionLevel) }}</span>
          <span class="st" :class="item.status?.toLowerCase()">{{ listingStatusText(item.status) }}</span>
        </p>
        <p class="meta">
          <span>浏览 {{ item.viewCount || 0 }}</span>
          <span>收藏 {{ item.favoriteCount || 0 }}</span>
          <span>图片 {{ item.imageCount || 0 }}</span>
        </p>
      </div>
      <div class="ops">
        <van-button
          v-if="item.status === 'ON_SALE'"
          size="mini"
          round
          plain
          @click="toggle(item, false)"
        >下架</van-button>
        <van-button
          v-else-if="item.status === 'OFFLINE'"
          size="mini"
          round
          plain
          type="primary"
          @click="toggle(item, true)"
        >上架</van-button>
        <van-button size="mini" round plain @click="openPrice(item)">改价</van-button>
      </div>
    </div>

    <div v-if="list.length" class="more">
      <van-button size="small" plain :loading="loading" @click="loadMore">加载更多</van-button>
    </div>

    <van-dialog v-model:show="priceShow" title="批量改价" show-cancel-button @confirm="applyPrice">
      <div class="price-dialog">
        <van-radio-group v-model="priceMode" direction="horizontal">
          <van-radio name="SET">设为</van-radio>
          <van-radio name="PERCENT">按比例 %</van-radio>
          <van-radio name="DELTA">加减</van-radio>
        </van-radio-group>
        <van-field v-model="priceValue" type="number" label="数值" placeholder="如 20 / -10" />
        <p class="hint">
          设为：直接改成该价格；按比例：-10 表示降价 10%；加减：+2 表示加 2 元。
          影响 {{ priceTargets.length }} 件商品。
        </p>
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { merchantApi } from '@/api'
import { money, conditionText, listingStatusText } from '@/utils/format'

const router = useRouter()
const tabs = [
  { text: '全部', value: 'ALL' },
  { text: '在售', value: 'ON_SALE' },
  { text: '已下架', value: 'OFFLINE' },
  { text: '已售出', value: 'SOLD' }
]
const status = ref('ALL')
const keyword = ref('')
const list = ref([])
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)
const selecting = ref(false)
const selected = ref([])

function reload() {
  page.value = 1
  list.value = []
  total.value = 0
  loadMore()
}

async function loadMore() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await merchantApi.listings({ status: status.value, keyword: keyword.value, page: page.value, size })
    const rows = (res.records || []).map((r) => ({ ...r, _checked: false }))
    list.value = page.value === 1 ? rows : [...list.value, ...rows]
    total.value = Number(res.total || 0)
    page.value += 1
    syncSelected()
  } catch (e) {
    if (e?.code === 40300) showToast('请先完成商户入驻')
  } finally {
    loading.value = false
  }
}

function syncSelected() {
  selected.value = list.value.filter((i) => i._checked).map((i) => i.id)
}

async function toggle(item, online) {
  try {
    const res = await merchantApi.toggle(item.id, online)
    item.status = res?.status || (online ? 'ON_SALE' : 'OFFLINE')
    showToast(online ? '已上架' : '已下架')
  } catch {
    /* 请求层已提示 */
  }
}

async function offlineBatch() {
  if (!selected.value.length) return
  try {
    const res = await merchantApi.offline(selected.value)
    showToast(`已下架 ${res.count} 件`)
    selecting.value = false
    reload()
  } catch {
    /* 请求层已提示 */
  }
}

const priceShow = ref(false)
const priceMode = ref('SET')
const priceValue = ref('')
const priceTargets = ref([])

function openPrice(item) {
  priceTargets.value = item ? [item.id] : selected.value
  if (!priceTargets.value.length) {
    showToast('先勾选商品')
    return
  }
  priceMode.value = 'SET'
  priceValue.value = ''
  priceShow.value = true
}

async function applyPrice() {
  const value = Number(priceValue.value)
  if (!priceValue.value || Number.isNaN(value)) {
    showToast('请填写数值')
    return
  }
  try {
    const res = await merchantApi.price(priceTargets.value, value, priceMode.value)
    showToast(`已改价 ${res.count} 件`)
    selecting.value = false
    reload()
  } catch {
    /* 请求层已提示 */
  }
}

onMounted(reload)
</script>

<style scoped>
.mch-list { min-height: 100vh; background: #f5f6fa; padding-bottom: 24px; }
.nav-op { font-size: 14px; color: #4f7cff; }
.batch-bar { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; background: #eef2ff; font-size: 13px; color: #40527a; }
.batch-bar div { display: flex; gap: 8px; }
.row { display: flex; gap: 10px; align-items: center; margin: 10px 12px; padding: 10px; background: #fff; border-radius: 12px; }
.pick { flex: none; }
.cover { width: 56px; height: 76px; border-radius: 8px; object-fit: cover; background: #eef1f6; flex: none; }
.cover-empty { display: flex; align-items: center; justify-content: center; color: #b6bfd0; font-size: 13px; }
.main { flex: 1; min-width: 0; }
.title { margin: 0 0 6px; font-size: 14px; color: #1f2d3d; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { margin: 0 0 4px; font-size: 12px; color: #8a94a6; display: flex; gap: 10px; }
.price { color: #ef6c2f; font-weight: 600; }
.st.on_sale { color: #12a15c; }
.st.offline { color: #98a2b3; }
.st.sold { color: #4f7cff; }
.ops { display: flex; flex-direction: column; gap: 6px; flex: none; }
.more { padding: 12px; text-align: center; }
.price-dialog { padding: 12px 16px 4px; }
.price-dialog .hint { margin: 8px 0 0; font-size: 12px; color: #8a94a6; line-height: 1.5; }
</style>

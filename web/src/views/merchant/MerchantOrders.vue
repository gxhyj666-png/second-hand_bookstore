<template>
  <div class="page mch-orders">
    <van-nav-bar title="商户订单" left-arrow @click-left="router.back()" />
    <van-tabs v-model:active="status" sticky @change="reload">
      <van-tab v-for="t in tabs" :key="t.value" :title="t.text" :name="t.value" />
    </van-tabs>

    <van-empty v-if="!loading && !list.length" description="这个状态下没有订单" />

    <div v-for="o in list" :key="o.orderNo" class="row" @click="router.push(`/order/${o.orderNo}`)">
      <div class="head">
        <span class="no">{{ o.orderNo }}</span>
        <span class="st" :class="o.status?.toLowerCase()">{{ orderStatusText(o.status) }}</span>
      </div>
      <p class="title">{{ o.listingTitle || '（商品已删除）' }}</p>
      <p class="meta">
        <span>买家 {{ o.buyerName || '同学' }}</span>
        <span class="amount">{{ money(o.amount) }}</span>
        <span v-if="Number(o.commission) > 0">佣金 {{ money(o.commission) }}</span>
      </p>
      <p v-if="o.meetPlace || o.meetTime" class="meta">
        <span>约见 {{ o.meetPlace || '待定' }}</span>
        <span>{{ shortDate(o.meetTime) }}</span>
      </p>
      <div class="foot">
        <van-button
          v-if="o.status === 'WAIT_VERIFY'"
          size="mini"
          round
          type="primary"
          @click.stop="router.push(`/order/${o.orderNo}`)"
        >去核销</van-button>
        <span v-else class="time">{{ shortDate(o.createdAt) }}</span>
      </div>
    </div>

    <div v-if="list.length" class="more">
      <van-button size="small" plain :loading="loading" @click="loadMore">加载更多</van-button>
    </div>
    <MerchantTabbar />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { merchantApi } from '@/api'
import { money, shortDate, orderStatusText } from '@/utils/format'
import MerchantTabbar from '@/components/MerchantTabbar.vue'

const route = useRoute()
const router = useRouter()
const tabs = [
  { text: '全部', value: 'ALL' },
  { text: '待支付', value: 'PENDING_PAY' },
  { text: '待核销', value: 'WAIT_VERIFY' },
  { text: '已完成', value: 'COMPLETED' },
  { text: '售后中', value: 'REFUNDING' },
  { text: '已退款', value: 'REFUNDED' }
]
const status = ref(route.query.status || 'ALL')
const list = ref([])
const page = ref(1)
const loading = ref(false)

function reload() {
  page.value = 1
  list.value = []
  loadMore()
}

async function loadMore() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await merchantApi.orders({ status: status.value, page: page.value, size: 10 })
    const rows = res.records || []
    list.value = page.value === 1 ? rows : [...list.value, ...rows]
    page.value += 1
  } catch (e) {
    if (e?.code === 40300) showToast('请先完成商户入驻')
  } finally {
    loading.value = false
  }
}

onMounted(loadMore)
</script>

<style scoped>
.mch-orders { min-height: 100vh; background: #f5f6fa; padding-bottom: 24px; }
.row { margin: 10px 12px; padding: 12px; background: #fff; border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.no { font-size: 12px; color: #8a94a6; }
.st { font-size: 12px; color: #4f7cff; }
.st.completed { color: #12a15c; }
.st.refunded, .st.closed { color: #98a2b3; }
.st.refunding, .st.refund_agreed, .st.arbitrating { color: #ef6c2f; }
.title { margin: 0 0 6px; font-size: 14px; color: #1f2d3d; }
.meta { margin: 0 0 4px; font-size: 12px; color: #8a94a6; display: flex; gap: 12px; }
.amount { color: #ef6c2f; font-weight: 600; }
.foot { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-top: 6px; }
.time { font-size: 12px; color: #b6bfd0; }
.more { padding: 12px; text-align: center; }
</style>

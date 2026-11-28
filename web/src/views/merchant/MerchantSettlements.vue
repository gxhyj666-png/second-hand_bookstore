<template>
  <div class="page mch-settle">
    <van-nav-bar title="结算与佣金" left-arrow @click-left="router.back()" />

    <section class="sum">
      <p>累计成交</p>
      <strong>{{ money(summary.amount) }}</strong>
      <p class="sub">平台佣金 {{ money(summary.commission) }} · 待结算笔数 {{ summary.pending }}</p>
    </section>

    <van-empty v-if="!loading && !list.length" description="还没有结算记录，完成一笔交易后自动生成" />

    <div v-for="s in list" :key="s.id" class="row">
      <div class="line">
        <span class="no">{{ s.orderNo }}</span>
        <span class="st" :class="s.status?.toLowerCase()">{{ s.status === 'DONE' ? '已结算' : s.status }}</span>
      </div>
      <div class="line">
        <span class="amount">{{ money(s.amount) }}</span>
        <span class="meta">佣金 {{ money(s.commission) }}</span>
      </div>
      <p class="meta">{{ shortDate(s.settleAt || s.createdAt) }}</p>
    </div>

    <div v-if="list.length" class="more">
      <van-button size="small" plain :loading="loading" @click="loadMore">加载更多</van-button>
    </div>
    <MerchantTabbar />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { merchantApi } from '@/api'
import { money, shortDate } from '@/utils/format'
import MerchantTabbar from '@/components/MerchantTabbar.vue'

const router = useRouter()
const list = ref([])
const page = ref(1)
const loading = ref(false)
const summary = reactive({ amount: 0, commission: 0, pending: 0 })

function accumulate(rows) {
  rows.forEach((s) => {
    summary.amount += Number(s.amount || 0)
    summary.commission += Number(s.commission || 0)
    if (s.status !== 'DONE') summary.pending += 1
  })
}

async function loadMore() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await merchantApi.settlements({ page: page.value, size: 10 })
    const rows = res.records || []
    accumulate(rows)
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
.mch-settle { min-height: 100vh; background: #f5f6fa; padding-bottom: 24px; }
.sum { margin: 12px; padding: 16px; border-radius: 14px; background: linear-gradient(135deg, #4f7cff, #7aa2ff); color: #fff; }
.sum p { margin: 0; font-size: 12px; opacity: 0.85; }
.sum strong { display: block; margin: 6px 0; font-size: 26px; }
.sum .sub { opacity: 0.9; }
.row { margin: 10px 12px; padding: 12px; background: #fff; border-radius: 12px; }
.line { display: flex; align-items: center; justify-content: space-between; font-size: 13px; color: #40527a; }
.no { font-size: 12px; color: #8a94a6; }
.st { font-size: 12px; color: #12a15c; }
.amount { font-size: 16px; font-weight: 600; color: #ef6c2f; }
.meta { margin: 4px 0 0; font-size: 12px; color: #98a2b3; }
.more { padding: 12px; text-align: center; }
</style>

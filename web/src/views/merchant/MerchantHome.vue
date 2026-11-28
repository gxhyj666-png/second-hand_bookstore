<template>
  <div class="page mch-page">
    <van-nav-bar title="商户工作台" left-arrow @click-left="router.back()" />

    <div class="shop">
      <div>
        <h1>{{ data.shopName || '我的店铺' }}</h1>
        <p v-if="data.merchantId">商户编号 {{ data.merchantId }} · 书量大的商家走批量上下架</p>
        <p v-else>还没有商户身份</p>
      </div>
      <van-button v-if="data.merchantId" size="small" round plain type="primary" @click="router.push('/merchant/listings')">
        商品管理
      </van-button>
    </div>

    <template v-if="data.merchantId">
      <div class="grid">
        <div class="cell"><strong>{{ data.onSale ?? 0 }}</strong><span>在售商品</span></div>
        <div class="cell"><strong>{{ data.offline ?? 0 }}</strong><span>已下架</span></div>
        <div class="cell"><strong>{{ data.todayOrders ?? 0 }}</strong><span>今日订单</span></div>
        <div class="cell"><strong>{{ money(data.weekGmv) }}</strong><span>近 7 日成交</span></div>
        <div class="cell warn" @click="router.push('/merchant/orders?status=WAIT_VERIFY')">
          <strong>{{ data.waitVerify ?? 0 }}</strong><span>待核销</span>
        </div>
        <div class="cell warn" @click="router.push('/merchant/orders?status=REFUNDING')">
          <strong>{{ data.refunding ?? 0 }}</strong><span>售后处理中</span>
        </div>
      </div>

      <section class="card">
        <h2>近 7 日成交</h2>
        <div class="bars">
          <div v-for="s in data.series || []" :key="s.date" class="bar">
            <div class="bar-inner" :style="{ height: barHeight(s.gmv) }" />
            <span>{{ s.date }}</span>
          </div>
        </div>
        <p class="tip">共 {{ data.weekOrders ?? 0 }} 单 · 成交 {{ money(data.weekGmv) }}</p>
      </section>

      <van-cell-group inset class="links">
        <van-cell title="商品管理（分页 / 上下架 / 批量改价）" icon="goods-collect-o" is-link @click="router.push('/merchant/listings')" />
        <van-cell title="商户订单（核销 / 售后）" icon="orders-o" is-link @click="router.push('/merchant/orders')" />
        <van-cell title="结算与佣金" icon="balance-o" is-link @click="router.push('/merchant/settlements')" />
        <van-cell title="店铺资料 / 批量上架" icon="shop-o" is-link @click="router.push('/merchant')" />
      </van-cell-group>
    </template>

    <van-empty v-else description="还没有商户身份，提交入驻资料后即可使用">
      <van-button round type="primary" size="small" @click="router.replace('/merchant')">去申请入驻</van-button>
    </van-empty>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { merchantApi } from '@/api'
import { money } from '@/utils/format'

const router = useRouter()
const data = reactive({})
const loading = ref(false)

const maxGmv = computed(() => Math.max(1, ...(data.series || []).map((s) => Number(s.gmv) || 0)))

function barHeight(gmv) {
  const v = Number(gmv) || 0
  if (!v) return '2px'
  return `${Math.max(6, Math.round((v / maxGmv.value) * 100))}%`
}

async function load() {
  loading.value = true
  try {
    const res = await merchantApi.dashboard()
    Object.keys(data).forEach((k) => delete data[k])
    Object.assign(data, res || {})
  } catch (e) {
    if (e?.code === 40300) showToast('你还没有商户身份')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mch-page { padding-bottom: 24px; background: #f5f6fa; min-height: 100vh; }
.shop { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px; background: linear-gradient(135deg, #4f7cff, #7aa2ff); color: #fff; }
.shop h1 { margin: 0 0 4px; font-size: 18px; }
.shop p { margin: 0; font-size: 12px; opacity: 0.85; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; padding: 12px; }
.cell { background: #fff; border-radius: 12px; padding: 12px 8px; text-align: center; }
.cell strong { display: block; font-size: 17px; color: #1f2d3d; }
.cell span { font-size: 12px; color: #8a94a6; }
.cell.warn strong { color: #ef6c2f; }
.card { margin: 4px 12px 12px; background: #fff; border-radius: 12px; padding: 14px; }
.card h2 { margin: 0 0 10px; font-size: 14px; color: #1f2d3d; }
.bars { display: flex; align-items: flex-end; gap: 6px; height: 110px; }
.bar { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; height: 100%; }
.bar-inner { width: 60%; background: linear-gradient(180deg, #7aa2ff, #4f7cff); border-radius: 6px 6px 0 0; transition: height 0.3s; }
.bar span { margin-top: 6px; font-size: 10px; color: #98a2b3; }
.tip { margin: 8px 0 0; font-size: 12px; color: #6b7485; }
.links { margin: 0 12px; }
</style>

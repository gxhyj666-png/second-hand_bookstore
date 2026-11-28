<template>
  <nav class="mch-tabbar">
    <router-link
      v-for="t in tabs"
      :key="t.path"
      :to="t.path"
      class="tab"
      :class="{ active: isActive(t) }"
    >
      <van-icon :name="t.icon" size="20" />
      <span>{{ t.text }}</span>
    </router-link>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'

const route = useRoute()
const store = useUserStore()

const merchantId = computed(() => store.user?.merchantId || 0)

const tabs = computed(() => [
  { text: '工作台', icon: 'wap-home-o', path: '/merchant/workbench', prefix: '/merchant/workbench' },
  { text: '商品', icon: 'goods-collect-o', path: '/merchant/listings', prefix: '/merchant/listings' },
  { text: '订单', icon: 'orders-o', path: '/merchant/orders', prefix: '/merchant/orders' },
  { text: '结算', icon: 'balance-o', path: '/merchant/settlements', prefix: '/merchant/settlements' },
  {
    text: '店铺',
    icon: 'shop-o',
    path: merchantId.value ? `/shop/${merchantId.value}` : '/merchant',
    prefix: '/shop/'
  }
])

function isActive(t) {
  const p = route.path
  if (t.prefix === '/shop/') return p.startsWith('/shop/')
  return p === t.path || p.startsWith(`${t.prefix}/`)
}
</script>

<style scoped>
.mch-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  background: #fff;
  border-top: 1px solid var(--line);
  padding-bottom: env(safe-area-inset-bottom);
}
.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 7px 0 6px;
  font-size: 11px;
  color: #9aa4b2;
  text-decoration: none;
}
.tab.active {
  color: var(--brand);
  font-weight: 700;
}
</style>

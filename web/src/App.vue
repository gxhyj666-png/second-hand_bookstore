<template>
  <div class="app-shell">
    <router-view v-slot="{ Component }">
      <keep-alive :include="['HomeView']">
        <component :is="Component" />
      </keep-alive>
    </router-view>

    <van-tabbar
      v-if="showTabbar"
      :model-value="activeTab"
      active-color="#2f7d5a"
      inactive-color="#9aa0a6"
      safe-area-inset-bottom
      @change="onTabChange"
    >
      <van-tabbar-item icon="wap-home-o" name="/home">首页</van-tabbar-item>
      <van-tabbar-item icon="search" name="/search">找书</van-tabbar-item>
      <van-tabbar-item icon="plus" name="/publish">发布</van-tabbar-item>
      <van-tabbar-item icon="orders-o" name="/orders">订单</van-tabbar-item>
      <van-tabbar-item icon="user-o" name="/mine">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const TABS = ['/home', '/search', '/publish', '/orders', '/mine']
const showTabbar = computed(() => TABS.includes(route.path))
const activeTab = computed(() => (TABS.includes(route.path) ? route.path : '/home'))

function onTabChange(name) {
  if (name !== route.path) router.push(name)
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
</style>

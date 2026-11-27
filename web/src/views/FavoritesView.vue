<template>
  <div class="page favorites-page">
    <van-nav-bar title="我的收藏" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <div v-if="loading" class="grid-list">
        <div v-for="i in 4" :key="i" class="skeleton skeleton-card"></div>
      </div>

      <div v-else-if="error" class="card empty-wrap">
        <EmptyState :text="error" icon="⭐">
          <van-button size="small" round type="primary" @click="load">重新加载</van-button>
        </EmptyState>
      </div>

      <div v-else-if="!items.length" class="card empty-wrap">
        <EmptyState text="还没有收藏教材，遇到心仪的书先收藏起来" icon="⭐">
          <van-button size="small" round type="primary" @click="router.push('/search')">去找书</van-button>
        </EmptyState>
      </div>

      <template v-else>
        <p class="muted count">共 {{ items.length }} 本收藏，价格合适就下手吧</p>
        <div class="grid-list">
          <ListingCard v-for="item in items" :key="item.id" :item="item" />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listingApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import ListingCard from '@/components/ListingCard.vue'

const router = useRouter()

const items = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listingApi.favorites()
    const records = Array.isArray(res) ? res : res?.records || []
    // 后端字段是 conditionLevel，卡片组件统一读 condition
    items.value = records.map((x) => ({ ...x, condition: x.conditionLevel || x.condition }))
  } catch {
    items.value = []
    error.value = '收藏加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.favorites-page {
  background: var(--bg);
}
.count {
  margin: 12px 2px 10px;
  font-size: 12px;
}
.skeleton-card {
  height: 220px;
  border-radius: var(--radius);
}
.empty-wrap {
  margin-top: 16px;
  padding: 0;
}
.empty-wrap :deep(.empty) {
  padding: 44px 16px;
}
</style>

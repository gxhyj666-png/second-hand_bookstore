<template>
  <div class="page conversations-page">
    <van-nav-bar title="私信" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <div class="head between">
        <div>
          <h1>和同学聊聊</h1>
          <p class="muted">问品相、约时间和谈自提，都在这里</p>
        </div>
        <van-icon name="chat-o" size="24" class="head-icon" />
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          :finished-text="rows.length ? '没有更多会话了' : ''"
          :immediate-check="false"
          @load="loadMore"
        >
          <button
            v-for="c in rows"
            :key="c.id"
            type="button"
            class="conv-card card"
            @click="openChat(c)"
          >
            <div class="avatar">
              <img v-if="c.cover" :src="c.cover" :alt="c.listingTitle" @error="onImgError" />
              <span v-else>{{ (c.otherName || '同').slice(0, 1) }}</span>
            </div>
            <div class="conv-main">
              <div class="between">
                <strong class="name clamp-1">{{ c.otherName || '同校同学' }}</strong>
                <span class="muted time">{{ relTime(c.lastAt) }}</span>
              </div>
              <p class="book clamp-1">
                <van-icon name="bookmark-o" /> {{ c.listingTitle || '关联教材' }}
              </p>
              <p class="last clamp-1">{{ c.lastMessage || '还没有消息，打个招呼吧' }}</p>
            </div>
            <span v-if="c.unread > 0" class="unread">{{ c.unread > 99 ? '99+' : c.unread }}</span>
          </button>

          <EmptyState
            v-if="loadedOnce && !rows.length"
            :text="loadError || '还没有会话，去教材详情页点「聊一聊」开始吧'"
            icon="💬"
          >
            <van-button v-if="loadError" size="small" round type="primary" @click="reload">重新加载</van-button>
            <van-button v-else size="small" round plain type="primary" @click="router.push('/search')">去找书</van-button>
          </EmptyState>
        </van-list>
      </van-pull-refresh>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { conversationApi } from '@/api'
import { relTime } from '@/utils/format'
import { useUserStore } from '@/store/user'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()

const PAGE_SIZE = 20
const rows = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

function normalize(row) {
  // 后端返回 { conversation: {...}, listingTitle, listingCover, otherName, unread }
  const c = row?.conversation || row || {}
  const isBuyer = Number(c.buyerId) === Number(store.userId)
  return {
    id: c.id,
    listingId: c.listingId,
    listingTitle: row?.listingTitle || c.listingTitle || '',
    cover: row?.listingCover || c.listingCover || '',
    otherName: row?.otherName || c.otherName || '',
    lastMessage: c.lastMessage || '',
    lastAt: c.lastAt || c.updatedAt || c.createdAt,
    unread: Number(row?.unread ?? (isBuyer ? c.buyerUnread : c.sellerUnread) ?? 0)
  }
}

async function fetchPage(reset) {
  if (reset) {
    page.value = 1
    finished.value = false
    loadError.value = ''
  }
  loading.value = true
  try {
    const res = await conversationApi.list({ page: page.value, size: PAGE_SIZE })
    const records = recordsOf(res).map(normalize).filter((c) => c.id)
    rows.value = reset ? records : rows.value.concat(records)
    finished.value = records.length < PAGE_SIZE
    page.value += 1
    loadedOnce.value = true
  } catch {
    finished.value = true
    loadError.value = '会话加载失败，请稍后重试'
    loadedOnce.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function loadMore() {
  fetchPage(false)
}

function reload() {
  rows.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function onRefresh() {
  rows.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function openChat(c) {
  router.push({
    path: `/chat/${c.id}`,
    query: { listingId: c.listingId, title: c.listingTitle, name: c.otherName }
  })
}

function onImgError(e) {
  e.target.style.visibility = 'hidden'
}

onMounted(() => fetchPage(true))
</script>

<style scoped>
.conversations-page {
  background: var(--bg);
}
.head {
  padding: 18px 4px 10px;
}
.head h1 {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 800;
}
.head p {
  margin: 0;
  font-size: 12px;
}
.head-icon {
  color: var(--brand);
}
.conv-card {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  text-align: left;
  border: 0;
  cursor: pointer;
  position: relative;
  margin-bottom: 10px;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  overflow: hidden;
  flex: none;
  display: grid;
  place-items: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 20px;
  font-weight: 700;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.conv-main {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 15px;
  max-width: 60%;
}
.time {
  font-size: 11px;
  flex: none;
}
.book {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--brand);
  display: flex;
  align-items: center;
  gap: 3px;
}
.last {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted);
}
.unread {
  position: absolute;
  right: 12px;
  bottom: 12px;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
}
</style>

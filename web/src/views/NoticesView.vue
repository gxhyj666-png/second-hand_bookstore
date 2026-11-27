<template>
  <div class="page notices-page">
    <van-nav-bar title="消息" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()">
      <template #right>
        <span class="read-all" :class="{ disabled: !unreadCount || markingAll }" @click="readAll">
          {{ markingAll ? '处理中' : '全部已读' }}
        </span>
      </template>
    </van-nav-bar>

    <div class="container">
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          :finished-text="notices.length ? '没有更多消息了' : ''"
          :immediate-check="false"
          @load="loadMore"
        >
          <div
            v-for="n in notices"
            :key="n.id"
            class="card notice"
            :class="{ unread: n.readFlag === 0 }"
            @click="openNotice(n)"
          >
            <span class="dot" />
            <div class="notice-main">
              <div class="between">
                <strong class="clamp-1">{{ n.title || '系统通知' }}</strong>
                <span class="muted time">{{ relTime(n.createdAt) }}</span>
              </div>
              <p class="muted clamp-2">{{ n.content }}</p>
            </div>
            <van-icon name="arrow" class="arrow" />
          </div>

          <EmptyState v-if="loadedOnce && !notices.length" :text="loadError || '暂时没有消息'" icon="🔔">
            <van-button v-if="loadError" size="small" round type="primary" @click="reload">重新加载</van-button>
          </EmptyState>
        </van-list>
      </van-pull-refresh>
    </div>

    <van-popup v-model:show="detailShow" position="bottom" round :style="{ maxHeight: '70%' }">
      <div class="detail-popup">
        <div class="between detail-head">
          <strong>{{ current?.title || '消息详情' }}</strong>
          <van-icon name="cross" size="18" @click="detailShow = false" />
        </div>
        <p class="muted detail-time">{{ shortDate(current?.createdAt) }}</p>
        <div class="detail-body">{{ current?.content || '暂无内容' }}</div>
        <div class="detail-foot">
          <van-button block round plain type="primary" @click="detailShow = false">知道了</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { noticeApi } from '@/api'
import { useUserStore } from '@/store/user'
import { relTime, shortDate } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()

const PAGE_SIZE = 10

const notices = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')
const markingAll = ref(false)

const detailShow = ref(false)
const current = ref(null)

const unreadCount = computed(() => notices.value.filter((n) => n.readFlag === 0).length)

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

async function fetchPage(reset) {
  if (reset) {
    page.value = 1
    finished.value = false
    loadError.value = ''
  }
  loading.value = true
  try {
    const res = await noticeApi.page({ page: page.value, size: PAGE_SIZE })
    const records = recordsOf(res)
    notices.value = reset ? records : notices.value.concat(records)
    finished.value = records.length < PAGE_SIZE
    page.value += 1
    loadedOnce.value = true
  } catch {
    finished.value = true
    loadError.value = '消息加载失败，请稍后重试'
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
  notices.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function onRefresh() {
  notices.value = []
  loadedOnce.value = false
  fetchPage(true)
}

async function openNotice(n) {
  current.value = n
  detailShow.value = true
  if (n.readFlag === 1) return
  try {
    await noticeApi.read(n.id)
    n.readFlag = 1
    store.refreshUnread()
  } catch {
    // 已读标记失败不阻塞阅读
  }
}

async function readAll() {
  if (!unreadCount.value || markingAll.value) return
  markingAll.value = true
  try {
    await noticeApi.readAll()
    notices.value.forEach((n) => {
      n.readFlag = 1
    })
    store.refreshUnread()
    showToast('已全部标记为已读')
  } catch {
    // 请求层已经提示
  } finally {
    markingAll.value = false
  }
}

onMounted(() => {
  fetchPage(true)
  store.refreshUnread()
})
</script>

<style scoped>
.notices-page {
  background: var(--bg);
}
.read-all {
  color: var(--brand);
  font-size: 13px;
  cursor: pointer;
}
.read-all.disabled {
  color: var(--muted);
  cursor: default;
}
.notice {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  cursor: pointer;
}
.notice.unread {
  border-left: 3px solid var(--accent);
}
.notice .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: transparent;
  flex: none;
}
.notice.unread .dot {
  background: var(--accent);
}
.notice-main {
  flex: 1;
  min-width: 0;
}
.notice-main strong {
  font-size: 14px;
}
.time {
  font-size: 11px;
  flex: none;
  margin-left: 8px;
}
.notice-main p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.55;
}
.arrow {
  color: var(--line);
  flex: none;
}
.detail-popup {
  padding: 16px 16px calc(16px + env(safe-area-inset-bottom, 0px));
}
.detail-head {
  font-size: 16px;
}
.detail-time {
  margin: 6px 0 12px;
  font-size: 11px;
}
.detail-body {
  font-size: 14px;
  line-height: 1.75;
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 46vh;
  overflow-y: auto;
}
.detail-foot {
  padding-top: 18px;
}
</style>

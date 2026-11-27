<template>
  <div class="page chat-page">
    <van-nav-bar :title="title" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div ref="bodyRef" class="chat-body">
      <div v-if="loading && !messages.length" class="loading-wrap">
        <van-loading size="20">加载消息中</van-loading>
      </div>

      <EmptyState
        v-else-if="!messages.length"
        text="还没有消息，先打个招呼、约个时间吧"
        icon="💬"
      />

      <div v-for="m in messages" :key="m.id" class="msg" :class="{ mine: isMine(m) }">
        <div class="bubble" :class="{ card: isCard(m) }">
          <template v-if="isCard(m)">
            <div class="card-head">
              <van-icon :name="cardIcon(m)" size="15" />
              <strong>{{ cardTitle(m) }}</strong>
            </div>
            <p class="card-text">{{ m.content || '点击查看详情' }}</p>
            <p v-if="m.refId" class="card-ref">关联单号 #{{ m.refId }}</p>
          </template>
          <p v-else class="text">{{ m.content }}</p>
        </div>
        <span class="msg-time">{{ relTime(m.createdAt) }}</span>
      </div>
    </div>

    <div class="composer">
      <div class="composer-inner">
        <van-field
          v-model="draft"
          class="input"
          type="textarea"
          rows="1"
          autosize
          maxlength="500"
          placeholder="输入消息…"
          @keyup.enter.exact.prevent="send"
        />
        <van-button
          round
          type="primary"
          size="small"
          :loading="sending"
          :disabled="!draft.trim()"
          @click="send"
        >
          发送
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { conversationApi } from '@/api'
import { relTime } from '@/utils/format'
import { useUserStore } from '@/store/user'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const conversationId = String(route.params.id || '')
const listingId = route.query.listingId || ''
const title = computed(() => String(route.query.name || '聊天'))

const messages = ref([])
const loading = ref(false)
const sending = ref(false)
const draft = ref('')
const bodyRef = ref(null)
let timer = null
let polling = false

const lastId = computed(() => {
  if (!messages.value.length) return 0
  return messages.value.reduce((max, m) => Math.max(max, Number(m.id) || 0), 0)
})

function isMine(m) {
  return Number(m.senderId) === Number(store.userId)
}

function isCard(m) {
  return ['MEET_CARD', 'REFUND_CARD', 'SIGN_CARD'].includes(m.msgType)
}

function cardTitle(m) {
  return { MEET_CARD: '约见卡片', REFUND_CARD: '退款卡片', SIGN_CARD: '签到卡片' }[m.msgType] || '卡片消息'
}

function cardIcon(m) {
  return { MEET_CARD: 'calendar-o', REFUND_CARD: 'refund-o', SIGN_CARD: 'passed' }[m.msgType] || 'card'
}

async function scrollToBottom() {
  await nextTick()
  const el = bodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function fetchMessages(afterId = 0, replace = false) {
  const list = await conversationApi.messages(conversationId, afterId)
  const incoming = Array.isArray(list) ? list : list?.records || []
  if (replace) {
    messages.value = incoming
  } else {
    const known = new Set(messages.value.map((m) => Number(m.id)))
    messages.value = messages.value.concat(incoming.filter((m) => !known.has(Number(m.id))))
  }
  if (incoming.length) scrollToBottom()
}

async function init() {
  loading.value = true
  try {
    await fetchMessages(0, true)
  } catch {
    // 请求层已经提示
  } finally {
    loading.value = false
  }
  timer = setInterval(async () => {
    if (polling || document.hidden) return
    polling = true
    try {
      await fetchMessages(lastId.value, false)
    } catch {
      // 轮询失败静默重试，不打断输入
    } finally {
      polling = false
    }
  }, 5000)
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    const msg = await conversationApi.send(conversationId, { type: 'TEXT', content: text, extra: null })
    if (msg?.id) messages.value.push(msg)
    draft.value = ''
    scrollToBottom()
  } catch {
    // 请求层已经提示，保留输入
  } finally {
    sending.value = false
  }
}

function goListing() {
  if (listingId) router.push(`/listing/${listingId}`)
}

onMounted(init)
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  timer = null
})
</script>

<style scoped>
.chat-page {
  background: var(--bg);
  padding-bottom: 96px;
}
.chat-body {
  max-width: 1120px;
  margin: 0 auto;
  padding: 14px 12px 20px;
  min-height: calc(100vh - 160px);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.loading-wrap {
  padding: 40px 0;
  display: grid;
  place-items: center;
}
.msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 80%;
}
.msg.mine {
  align-self: flex-end;
  align-items: flex-end;
}
.bubble {
  background: #fff;
  border-radius: 12px 12px 12px 4px;
  padding: 9px 12px;
  box-shadow: var(--shadow);
  color: var(--ink);
}
.msg.mine .bubble {
  background: var(--brand);
  color: #fff;
  border-radius: 12px 12px 4px 12px;
}
.text {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble.card {
  min-width: 210px;
  background: #fff;
  border: 1px solid var(--brand);
  color: var(--ink);
}
.msg.mine .bubble.card {
  background: #fff;
  color: var(--ink);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--brand);
  font-size: 13px;
}
.card-text {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.card-ref {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--muted);
}
.msg-time {
  font-size: 10px;
  color: var(--muted);
  margin-top: 3px;
}
.composer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  border-top: 1px solid var(--line);
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom, 0px));
  z-index: 20;
}
.composer-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.input {
  flex: 1;
  background: var(--bg);
  border-radius: 18px;
  padding: 4px 12px;
}
.input :deep(.van-field__control) {
  max-height: 92px;
}
</style>

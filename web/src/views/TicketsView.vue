<template>
  <div class="page tickets-page">
    <van-nav-bar title="纠纷工单" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()">
      <template #right>
        <span class="nav-action" @click="openCreate">新建</span>
      </template>
    </van-nav-bar>

    <div class="container">
      <div class="intro card">
        <van-icon name="service-o" size="22" />
        <div>
          <strong>交易遇到问题？</strong>
          <p class="muted">发起工单后管理员会介入处理，请保留聊天与交易凭证。</p>
        </div>
      </div>

      <van-list
        v-model:loading="loading"
        :finished="finished"
        :finished-text="tickets.length ? '没有更多工单了' : ''"
        :immediate-check="false"
        @load="loadMore"
      >
        <div v-for="t in tickets" :key="t.id" class="card ticket" @click="openTicket(t)">
          <div class="between">
            <span class="tag muted">{{ typeText(t.type) }}</span>
            <span class="tag" :class="statusTone(t.status)">{{ statusText(t.status) }}</span>
          </div>
          <strong class="title clamp-2">{{ t.title || '未命名工单' }}</strong>
          <p class="muted desc clamp-2">{{ t.description || '暂无描述' }}</p>
          <div class="between foot">
            <span class="muted">{{ t.orderNo ? `订单 ${t.orderNo}` : '未关联订单' }}</span>
            <span class="muted">{{ shortDate(t.createdAt) }}</span>
          </div>
        </div>

        <EmptyState v-if="loadedOnce && !tickets.length" :text="loadError || '还没有提交过工单'" icon="🎫">
          <van-button v-if="loadError" size="small" round type="primary" @click="reload">重新加载</van-button>
          <van-button v-else size="small" round type="primary" @click="openCreate">发起工单</van-button>
        </EmptyState>
      </van-list>
    </div>

    <van-popup v-model:show="createPopup" position="bottom" round :style="{ maxHeight: '86%' }">
      <div class="panel">
        <div class="between panel-head">
          <strong>发起工单</strong>
          <van-icon name="cross" size="18" @click="createPopup = false" />
        </div>
        <div class="panel-body">
          <van-cell-group inset>
            <van-field v-model="createForm.orderNo" label="订单号" placeholder="可选，填写后可更快定位" clearable />
            <van-cell title="问题类型" :value="typeText(createForm.type)" is-link @click="typePopup = true" />
            <van-field v-model="createForm.title" label="标题" maxlength="40" placeholder="一句话描述问题" clearable />
            <van-field
              v-model="createForm.description"
              label="详细描述"
              type="textarea"
              rows="4"
              autosize
              maxlength="300"
              show-word-limit
              placeholder="请说明时间、地点与具体情况，便于管理员核实"
            />
          </van-cell-group>
        </div>
        <div class="panel-foot">
          <van-button block round type="primary" :loading="creating" loading-text="提交中" @click="submitCreate">
            提交工单
          </van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="typePopup" position="bottom" round>
      <van-picker
        title="问题类型"
        :columns="TYPE_OPTIONS"
        @confirm="onTypeConfirm"
        @cancel="typePopup = false"
      />
    </van-popup>

    <van-popup v-model:show="chatPopup" position="bottom" round :style="{ height: '82%' }">
      <div class="panel chat">
        <div class="between panel-head">
          <div class="chat-title">
            <strong class="clamp-1">{{ current?.title || '工单详情' }}</strong>
            <span class="tag" :class="statusTone(current?.status)">{{ statusText(current?.status) }}</span>
          </div>
          <van-icon name="cross" size="18" @click="chatPopup = false" />
        </div>

        <div ref="msgBox" class="msg-list">
          <div class="orig">
            <p class="muted">工单描述</p>
            <div class="orig-body">{{ current?.description || '无' }}</div>
            <p v-if="current?.result" class="result">处理结果：{{ current.result }}</p>
          </div>

          <div v-if="msgLoading" class="msg-loading"><van-loading size="20">加载消息</van-loading></div>
          <EmptyState v-else-if="!messages.length" text="还没有回复，等待管理员处理" icon="💬" />
          <div
            v-for="m in messages"
            :key="m.id"
            class="msg"
            :class="{ mine: m.senderRole !== 'ADMIN' }"
          >
            <div class="bubble">
              <p>{{ m.content }}</p>
              <span class="muted">{{ shortDate(m.createdAt) }}</span>
            </div>
          </div>
        </div>

        <div class="reply">
          <van-field
            v-model="replyText"
            placeholder="补充说明…"
            maxlength="300"
            :disabled="current?.status === 'RESOLVED' || current?.status === 'REJECTED'"
            @keyup.enter="sendReply"
          />
          <van-button
            size="small"
            round
            type="primary"
            :loading="replying"
            :disabled="current?.status === 'RESOLVED' || current?.status === 'REJECTED'"
            @click="sendReply"
          >
            发送
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { ticketApi } from '@/api'
import { shortDate } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const PAGE_SIZE = 10

const TYPE_OPTIONS = [
  { text: '退款纠纷', value: 'REFUND' },
  { text: '盗版/假书', value: 'FAKE_BOOK' },
  { text: '品相问题', value: 'CONDITION' },
  { text: '其他问题', value: 'OTHER' }
]

const TYPE_TEXT = TYPE_OPTIONS.reduce((acc, t) => ({ ...acc, [t.value]: t.text }), {})
const STATUS_TEXT = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  REJECTED: '已驳回'
}

const tickets = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')

const createPopup = ref(false)
const typePopup = ref(false)
const creating = ref(false)
const createForm = reactive({ orderNo: '', type: 'REFUND', title: '', description: '' })

const chatPopup = ref(false)
const current = ref(null)
const messages = ref([])
const msgLoading = ref(false)
const replyText = ref('')
const replying = ref(false)
const msgBox = ref(null)

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])
const typeText = (t) => TYPE_TEXT[t] || '其他问题'
const statusText = (s) => STATUS_TEXT[s] || '处理中'

function statusTone(s) {
  if (s === 'PENDING') return 'warn'
  if (s === 'REJECTED') return 'danger'
  if (s === 'RESOLVED') return 'muted'
  return ''
}

async function fetchPage(reset) {
  if (reset) {
    page.value = 1
    finished.value = false
    loadError.value = ''
  }
  loading.value = true
  try {
    const res = await ticketApi.mine({ page: page.value, size: PAGE_SIZE })
    const records = recordsOf(res)
    tickets.value = reset ? records : tickets.value.concat(records)
    finished.value = records.length < PAGE_SIZE
    page.value += 1
    loadedOnce.value = true
  } catch {
    finished.value = true
    loadError.value = '工单加载失败，请稍后重试'
    loadedOnce.value = true
  } finally {
    loading.value = false
  }
}

function loadMore() {
  fetchPage(false)
}

function reload() {
  tickets.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function openCreate() {
  createForm.orderNo = ''
  createForm.type = 'REFUND'
  createForm.title = ''
  createForm.description = ''
  createPopup.value = true
}

function onTypeConfirm({ selectedOptions }) {
  createForm.type = selectedOptions?.[0]?.value || 'OTHER'
  typePopup.value = false
}

async function submitCreate() {
  if (creating.value) return
  if (!createForm.title.trim()) return showToast('请填写工单标题')
  if (!createForm.description.trim()) return showToast('请填写问题描述')
  creating.value = true
  try {
    await ticketApi.create({
      orderNo: createForm.orderNo.trim() || null,
      type: createForm.type,
      title: createForm.title.trim(),
      description: createForm.description.trim()
    })
    showToast('工单已提交')
    createPopup.value = false
    reload()
  } catch {
    // 请求层已经提示
  } finally {
    creating.value = false
  }
}

async function openTicket(t) {
  current.value = t
  messages.value = []
  chatPopup.value = true
  msgLoading.value = true
  try {
    const res = await ticketApi.messages(t.id)
    messages.value = Array.isArray(res) ? res : res?.records || []
    await nextTick(scrollBottom)
  } catch {
    messages.value = []
  } finally {
    msgLoading.value = false
  }
}

function scrollBottom() {
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
}

async function sendReply() {
  if (replying.value) return
  const content = replyText.value.trim()
  if (!content) return showToast('请输入回复内容')
  if (!current.value) return
  replying.value = true
  try {
    const msg = await ticketApi.reply(current.value.id, content)
    if (msg && typeof msg === 'object') messages.value.push(msg)
    replyText.value = ''
    if (current.value.status === 'PENDING') current.value.status = 'PROCESSING'
    await nextTick(scrollBottom)
  } catch {
    // 请求层已经提示
  } finally {
    replying.value = false
  }
}

onMounted(() => fetchPage(true))
</script>

<style scoped>
.tickets-page {
  background: var(--bg);
}
.nav-action {
  color: var(--brand);
  font-size: 13px;
  cursor: pointer;
}
.intro {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  color: var(--brand);
}
.intro strong {
  color: var(--ink);
  font-size: 14px;
}
.intro p {
  margin: 3px 0 0;
  font-size: 11px;
  line-height: 1.6;
}
.ticket {
  margin-top: 10px;
  cursor: pointer;
}
.title {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.45;
}
.desc {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.55;
}
.foot {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  font-size: 11px;
}
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.panel-head {
  padding: 16px 16px 10px;
  font-size: 16px;
}
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 12px;
}
.panel-foot {
  padding: 10px 16px calc(14px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--line);
}
.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.chat-title strong {
  max-width: 200px;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 14px 10px;
}
.orig {
  background: var(--bg);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.orig p {
  margin: 0;
  font-size: 11px;
}
.orig-body {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink-2);
}
.result {
  margin-top: 8px !important;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  color: var(--brand) !important;
  font-size: 12px !important;
}
.msg-loading {
  padding: 20px 0;
  display: grid;
  place-items: center;
}
.msg {
  display: flex;
  margin-bottom: 10px;
}
.msg.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  background: #f2f4f5;
  border-radius: 12px;
  padding: 8px 12px;
}
.msg.mine .bubble {
  background: var(--brand);
  color: #fff;
}
.bubble p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble span {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  text-align: right;
}
.msg.mine .bubble span {
  color: rgba(255, 255, 255, 0.75);
}
.reply {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--line);
}
.reply :deep(.van-cell) {
  flex: 1;
  background: var(--bg);
  border-radius: 999px;
  padding: 6px 14px;
}
</style>

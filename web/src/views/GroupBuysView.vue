<template>
  <div class="page groups-page">
    <van-nav-bar title="班级接龙" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()">
      <template #right>
        <span class="nav-action" @click="openCreate">发起接龙</span>
      </template>
    </van-nav-bar>

    <div class="container">
      <div class="status-row">
        <button
          v-for="s in STATUS_TABS"
          :key="s.value"
          type="button"
          class="chip"
          :class="{ active: status === s.value }"
          @click="onStatusChange(s.value)"
        >
          {{ s.text }}
        </button>
      </div>

      <van-list
        v-model:loading="loading"
        :finished="finished"
        :finished-text="groups.length ? '没有更多接龙了' : ''"
        :immediate-check="false"
        @load="loadMore"
      >
        <div v-for="g in groups" :key="g.id" class="card group" @click="goDetail(g)">
          <div class="between">
            <strong class="title clamp-2">{{ g.title || '教材接龙' }}</strong>
            <span class="tag" :class="statusTone(g.status)">{{ statusText(g.status) }}</span>
          </div>
          <div class="progress-row">
            <div class="bar"><span :style="{ width: percent(g) + '%' }" /></div>
            <span class="muted count">{{ g.currentCount ?? 0 }} / {{ g.targetCount ?? 0 }} 册</span>
          </div>
          <div class="between foot">
            <span class="muted">
              <van-icon name="clock-o" />
              {{ deadlineText(g) }}
            </span>
            <span v-if="g.unitPrice" class="price">{{ money(g.unitPrice) }}/册</span>
          </div>
        </div>

        <EmptyState v-if="loadedOnce && !groups.length" :text="loadError || '还没有进行中的接龙'" icon="👥">
          <van-button v-if="loadError" size="small" round type="primary" @click="reload">重新加载</van-button>
          <van-button v-else size="small" round type="primary" @click="openCreate">发起第一个接龙</van-button>
        </EmptyState>
      </van-list>
    </div>

    <van-popup v-model:show="createPopup" position="bottom" round :style="{ maxHeight: '88%' }">
      <div class="panel">
        <div class="between panel-head">
          <strong>发起接龙</strong>
          <van-icon name="cross" size="18" @click="createPopup = false" />
        </div>
        <div class="panel-body">
          <van-cell-group inset>
            <van-field v-model="form.title" label="标题" maxlength="30" placeholder="例如：计网第 4 版团购" clearable />
            <van-cell title="课程" :value="course ? course.name : '不指定'" is-link @click="openCourse" />
            <van-cell title="教材" :value="book ? book.title : '不指定'" is-link @click="openBook" />
            <van-field v-model="form.targetCount" type="digit" label="目标册数" placeholder="例如 20" input-align="right" />
            <van-field v-model="form.unitPrice" type="number" label="单价" placeholder="0.00" input-align="right">
              <template #button><span class="unit">元/册</span></template>
            </van-field>
            <van-cell title="截止时间" :value="deadlineTextOf(form.deadlineDays)" is-link @click="deadlinePopup = true" />
          </van-cell-group>
          <p class="muted tip">接龙达到目标册数后会自动标记为已达成，团长可联系卖家统一收书。</p>
        </div>
        <div class="panel-foot">
          <van-button block round type="primary" :loading="creating" loading-text="创建中" @click="submitCreate">
            创建接龙
          </van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="coursePopup" position="bottom" round :style="{ height: '70%' }">
      <div class="panel">
        <div class="between panel-head">
          <strong>选择课程</strong>
          <van-icon name="cross" size="18" @click="coursePopup = false" />
        </div>
        <van-search
          v-model="courseKeyword"
          shape="round"
          background="transparent"
          placeholder="输入课程名或课程代码"
          @update:model-value="onCourseKeyword"
          @search="searchCourses"
        />
        <div class="panel-body">
          <button class="pick-row" type="button" @click="chooseCourse(null)">
            <strong>不指定课程</strong>
          </button>
          <div v-if="courseLoading" class="pick-loading"><van-loading size="20">搜索中</van-loading></div>
          <EmptyState v-else-if="!courses.length" text="没有搜到课程" icon="🎓" />
          <button v-for="c in courses" :key="c.id" class="pick-row" type="button" @click="chooseCourse(c)">
            <div class="pick-main">
              <strong>{{ c.name }}</strong>
              <p class="muted clamp-1">{{ [c.code, c.college, c.teacher].filter(Boolean).join(' · ') }}</p>
            </div>
          </button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="bookPopup" position="bottom" round :style="{ height: '76%' }">
      <div class="panel">
        <div class="between panel-head">
          <strong>选择教材</strong>
          <van-icon name="cross" size="18" @click="bookPopup = false" />
        </div>
        <van-search
          v-model="bookKeyword"
          shape="round"
          background="transparent"
          placeholder="输入书名、作者或出版社"
          @update:model-value="onBookKeyword"
          @search="searchBooks"
        />
        <div class="panel-body">
          <button class="pick-row" type="button" @click="chooseBook(null)">
            <strong>不指定教材</strong>
          </button>
          <div v-if="bookLoading" class="pick-loading"><van-loading size="20">搜索中</van-loading></div>
          <EmptyState v-else-if="!books.length" text="没有搜到教材" icon="📚" />
          <button v-for="b in books" :key="b.id" class="pick-row" type="button" @click="chooseBook(b)">
            <div class="pick-main">
              <strong class="clamp-2">{{ b.title }}</strong>
              <p class="muted clamp-1">{{ [b.author, b.publisher, b.edition].filter(Boolean).join(' · ') }}</p>
            </div>
          </button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="deadlinePopup" position="bottom" round>
      <van-picker
        title="截止时间"
        :columns="DEADLINE_OPTIONS"
        @confirm="onDeadlineConfirm"
        @cancel="deadlinePopup = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { catalogApi, groupBuyApi } from '@/api'
import { money, remainText } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const PAGE_SIZE = 10
const STATUS_TABS = [
  { text: '全部', value: '' },
  { text: '进行中', value: 'OPEN' },
  { text: '已达成', value: 'DONE' },
  { text: '已关闭', value: 'CLOSED' }
]
const DEADLINE_OPTIONS = [
  { text: '24 小时内', value: 1 },
  { text: '2 天后', value: 2 },
  { text: '3 天后', value: 3 },
  { text: '7 天后', value: 7 }
]

const groups = ref([])
const page = ref(1)
const status = ref('')
const loading = ref(false)
const finished = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')

const createPopup = ref(false)
const creating = ref(false)
const form = reactive({ title: '', targetCount: '', unitPrice: '', deadlineDays: 3 })
const course = ref(null)
const book = ref(null)

const coursePopup = ref(false)
const courseKeyword = ref('')
const courses = ref([])
const courseLoading = ref(false)
let courseTimer = null

const bookPopup = ref(false)
const bookKeyword = ref('')
const books = ref([])
const bookLoading = ref(false)
let bookTimer = null

const deadlinePopup = ref(false)

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])
const statusText = (s) => ({ OPEN: '进行中', DONE: '已达成', CLOSED: '已关闭' })[s] || '进行中'

function statusTone(s) {
  if (s === 'DONE') return 'warn'
  if (s === 'CLOSED') return 'muted'
  return ''
}

function percent(g) {
  const target = Number(g.targetCount) || 0
  if (!target) return 0
  return Math.min(100, Math.round(((Number(g.currentCount) || 0) / target) * 100))
}

function deadlineText(g) {
  if (!g.deadline) return '不限时'
  if (new Date(g.deadline).getTime() <= Date.now()) return '已截止'
  return remainText(g.deadline)
}

function deadlineTextOf(days) {
  return DEADLINE_OPTIONS.find((d) => d.value === days)?.text || '3 天后'
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

// 后端 deadline 是 LocalDateTime，用无时区的 ISO 字符串最稳
function deadlineISO(days) {
  const d = new Date(Date.now() + days * 24 * 3600 * 1000)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

async function fetchPage(reset) {
  if (reset) {
    page.value = 1
    finished.value = false
    loadError.value = ''
  }
  loading.value = true
  try {
    const res = await groupBuyApi.list({
      status: status.value || undefined,
      page: page.value,
      size: PAGE_SIZE
    })
    const records = recordsOf(res)
    groups.value = reset ? records : groups.value.concat(records)
    finished.value = records.length < PAGE_SIZE
    page.value += 1
    loadedOnce.value = true
  } catch {
    finished.value = true
    loadError.value = '接龙加载失败，请稍后重试'
    loadedOnce.value = true
  } finally {
    loading.value = false
  }
}

function loadMore() {
  fetchPage(false)
}

function reload() {
  groups.value = []
  loadedOnce.value = false
  fetchPage(true)
}

function onStatusChange(value) {
  if (status.value === value) return
  status.value = value
  reload()
}

function goDetail(g) {
  if (g.inviteCode) router.push(`/group-buys/${g.inviteCode}`)
}

function openCreate() {
  form.title = ''
  form.targetCount = ''
  form.unitPrice = ''
  form.deadlineDays = 3
  course.value = null
  book.value = null
  createPopup.value = true
}

function openCourse() {
  coursePopup.value = true
  if (!courses.value.length) searchCourses()
}

async function searchCourses() {
  courseLoading.value = true
  try {
    const res = await catalogApi.courses({ keyword: courseKeyword.value.trim() || undefined, page: 1, size: 20 })
    courses.value = recordsOf(res)
  } catch {
    courses.value = []
  } finally {
    courseLoading.value = false
  }
}

function onCourseKeyword(v) {
  courseKeyword.value = v
  clearTimeout(courseTimer)
  courseTimer = setTimeout(searchCourses, 350)
}

function chooseCourse(c) {
  course.value = c
  coursePopup.value = false
}

function openBook() {
  bookPopup.value = true
  if (!books.value.length) searchBooks()
}

async function searchBooks() {
  bookLoading.value = true
  try {
    const res = await catalogApi.textbooks({ keyword: bookKeyword.value.trim() || undefined, page: 1, size: 20 })
    books.value = recordsOf(res)
  } catch {
    books.value = []
  } finally {
    bookLoading.value = false
  }
}

function onBookKeyword(v) {
  bookKeyword.value = v
  clearTimeout(bookTimer)
  bookTimer = setTimeout(searchBooks, 350)
}

function chooseBook(b) {
  book.value = b
  bookPopup.value = false
  if (b && !form.title) form.title = `${b.title} 团购`
  if (b?.price && !form.unitPrice) form.unitPrice = (Number(b.price) * 0.4).toFixed(2)
}

function onDeadlineConfirm({ selectedOptions }) {
  form.deadlineDays = selectedOptions?.[0]?.value || 3
  deadlinePopup.value = false
}

async function submitCreate() {
  if (creating.value) return
  const targetCount = Number(form.targetCount)
  const unitPrice = Number(form.unitPrice)
  if (!form.title.trim()) return showToast('请填写接龙标题')
  if (!Number.isInteger(targetCount) || targetCount <= 0) return showToast('目标册数需为大于 0 的整数')
  if (!(unitPrice > 0)) return showToast('请填写大于 0 的单价')

  creating.value = true
  try {
    const res = await groupBuyApi.create({
      courseId: course.value?.id || null,
      textbookId: book.value?.id || null,
      title: form.title.trim(),
      targetCount,
      unitPrice,
      deadline: deadlineISO(form.deadlineDays)
    })
    showToast('接龙已创建，邀请同学来参与')
    createPopup.value = false
    if (res?.inviteCode) router.push(`/group-buys/${res.inviteCode}`)
    else reload()
  } catch {
    // 请求层已经提示
  } finally {
    creating.value = false
  }
}

onMounted(() => fetchPage(true))
</script>

<style scoped>
.groups-page {
  background: var(--bg);
}
.nav-action {
  color: var(--brand);
  font-size: 13px;
  cursor: pointer;
}
.status-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 12px 0;
}
.chip {
  flex: none;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-2);
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
}
.chip.active {
  border-color: var(--brand);
  background: var(--brand);
  color: #fff;
}
.group {
  margin-bottom: 10px;
  cursor: pointer;
}
.title {
  font-size: 15px;
  line-height: 1.45;
  flex: 1;
  margin-right: 8px;
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.bar {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: #eef1f0;
  overflow: hidden;
}
.bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #4fa37e, #2f7d5a);
}
.count {
  font-size: 11px;
  flex: none;
}
.foot {
  margin-top: 8px;
  font-size: 11px;
}
.foot .muted {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.unit {
  color: var(--muted);
  margin-left: 4px;
  font-size: 12px;
}
.tip {
  margin: 10px 20px 0;
  font-size: 11px;
  line-height: 1.6;
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
  padding-bottom: 14px;
}
.panel-foot {
  padding: 10px 16px calc(14px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--line);
}
.pick-row {
  width: 100%;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: none;
  text-align: left;
  padding: 12px 16px;
  cursor: pointer;
}
.pick-row:active {
  background: var(--bg);
}
.pick-main strong {
  font-size: 14px;
}
.pick-main p {
  margin: 2px 0 0;
  font-size: 12px;
}
.pick-loading {
  padding: 24px 0;
  display: grid;
  place-items: center;
}
</style>

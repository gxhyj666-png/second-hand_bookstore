<template>
  <div class="page search-page">
    <div class="topbar">
      <van-search
        v-model="keyword"
        shape="round"
        background="transparent"
        placeholder="搜书名、作者或关键词"
        show-action
        @search="doSearch"
        @clear="doSearch"
      >
        <template #action>
          <button class="search-btn" type="button" @click="doSearch">搜索</button>
        </template>
      </van-search>
    </div>

    <div v-if="mineMode" class="container mine-head">
      <div>
        <h1>我的发布</h1>
        <p class="muted">在售与已下架的教材都在这里</p>
      </div>
      <van-button size="small" type="primary" round icon="plus" @click="go('/publish')">发布</van-button>
    </div>

    <template v-else>
      <div class="container filter-row">
        <button class="chip" :class="{ active: courseId }" type="button" @click="openCourse">
          <span class="clamp-1">{{ currentCourseName || '全部课程' }}</span>
          <van-icon name="arrow-down" size="11" />
        </button>
        <button class="chip" :class="{ active: condition }" type="button" @click="conditionPopup = true">
          <span>{{ condition ? conditionLabel(condition) : '全部品相' }}</span>
          <van-icon name="arrow-down" size="11" />
        </button>
        <button v-if="hasFilter" class="chip reset" type="button" @click="clearFilters">重置</button>
      </div>

      <div class="container sort-row">
        <button
          v-for="s in SORTS"
          :key="s.value"
          class="sort"
          :class="{ active: sort === s.value }"
          type="button"
          @click="pickSort(s.value)"
        >
          {{ s.label }}
        </button>
      </div>
    </template>

    <div class="container result-head">
      <span class="muted">
        <template v-if="textbookId">指定教材 · </template>
        共 {{ total }} 本
      </span>
      <button v-if="textbookId" class="link" type="button" @click="clearTextbook">查看全部</button>
    </div>

    <div class="container">
      <div v-if="loading && !loadedOnce" class="grid-list">
        <div v-for="i in 4" :key="i" class="ghost">
          <div class="skeleton ghost-cover"></div>
          <div class="skeleton ghost-line"></div>
          <div class="skeleton ghost-line short"></div>
        </div>
      </div>

      <van-list
        v-else
        v-model:loading="loading"
        :finished="finished"
        :finished-text="list.length ? '没有更多教材了' : ''"
        @load="loadPage"
      >
        <div v-if="list.length" class="grid-list">
          <ListingCard v-for="item in list" :key="item.id" :item="item" />
        </div>
      </van-list>

      <div v-if="loadedOnce && !list.length && !loading" class="empty-wrap card">
        <EmptyState :text="emptyText" icon="🔍">
          <div class="empty-actions">
            <van-button v-if="failed" size="small" type="primary" round @click="reset">重新加载</van-button>
            <van-button v-else-if="hasFilter" size="small" round plain type="primary" @click="clearFilters">
              清除筛选
            </van-button>
            <van-button v-else-if="!mineMode" size="small" round plain type="primary" @click="go('/publish')">
              去发布教材
            </van-button>
          </div>
        </EmptyState>
      </div>
    </div>

    <van-popup v-model:show="coursePopup" position="bottom" round>
      <van-picker
        title="选择课程"
        :columns="courseColumns"
        @confirm="onCourseConfirm"
        @cancel="coursePopup = false"
      />
    </van-popup>

    <van-popup v-model:show="conditionPopup" position="bottom" round>
      <van-picker
        title="选择品相"
        :columns="conditionColumns"
        @confirm="onConditionConfirm"
        @cancel="conditionPopup = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { catalogApi, listingApi } from '@/api'
import { CONDITION_OPTIONS, conditionText } from '@/utils/format'
import ListingCard from '@/components/ListingCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 12
const SORTS = [
  { value: 'relevance', label: '综合' },
  { value: 'newest', label: '最新' },
  { value: 'price_asc', label: '价格升' },
  { value: 'price_desc', label: '价格降' },
  { value: 'hot', label: '最热' }
]

const keyword = ref('')
const courseId = ref(null)
const textbookId = ref(null)
const condition = ref('')
const sort = ref('relevance')
const mineMode = ref(false)

const courses = ref([])
const coursePopup = ref(false)
const conditionPopup = ref(false)

const list = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const loadedOnce = ref(false)
const failed = ref(false)
const total = ref(0)
// 每次重置自增；旧的请求回来时直接丢弃，避免筛选切换后串数据
let gen = 0
let skipWatch = false

const conditionLabel = (value) => conditionText(value)

const currentCourseName = computed(() => {
  if (!courseId.value) return ''
  const hit = courses.value.find((c) => Number(c.id) === Number(courseId.value))
  return hit ? hit.name : ''
})

const hasFilter = computed(() => Boolean(courseId.value || condition.value || keyword.value.trim()))

const emptyText = computed(() => {
  if (failed.value) return '暂时没能连上服务器，稍后再试试'
  if (mineMode.value) return '你还没有发布过教材'
  if (keyword.value.trim()) return `没有找到和「${keyword.value.trim()}」相关的教材`
  if (hasFilter.value) return '这个筛选条件下还没有教材'
  return '图书角还空着，发布第一本教材吧'
})

const courseColumns = computed(() => {
  const base = [{ text: '全部课程', value: '' }]
  return base.concat(
    courses.value.map((c) => ({
      text: c.name + (c.code ? `（${c.code}）` : ''),
      value: c.id
    }))
  )
})

const conditionColumns = computed(() => {
  const base = [{ text: '全部品相', value: '' }]
  return base.concat(CONDITION_OPTIONS.map((c) => ({ text: c.text, value: c.value })))
})

function go(path) {
  router.push(path)
}

function hydrate() {
  const q = route.query
  keyword.value = typeof q.keyword === 'string' ? q.keyword : ''
  courseId.value = q.courseId ? Number(q.courseId) : null
  textbookId.value = q.textbookId ? Number(q.textbookId) : null
  mineMode.value = q.mine === '1'
}

async function loadCourses() {
  try {
    const res = await catalogApi.courses({ size: 50 })
    courses.value = Array.isArray(res) ? res : res?.records || []
  } catch {
    courses.value = []
  }
}

function openCourse() {
  coursePopup.value = true
  if (!courses.value.length) loadCourses()
}

function onCourseConfirm({ selectedOptions }) {
  const picked = selectedOptions?.[0]
  courseId.value = picked?.value || null
  coursePopup.value = false
  reset()
}

function onConditionConfirm({ selectedOptions }) {
  condition.value = selectedOptions?.[0]?.value || ''
  conditionPopup.value = false
  reset()
}

function pickSort(value) {
  if (sort.value === value) return
  sort.value = value
  reset()
}

function clearFilters() {
  courseId.value = null
  condition.value = ''
  keyword.value = ''
  textbookId.value = null
  reset()
}

function clearTextbook() {
  textbookId.value = null
  if (route.query.textbookId) {
    skipWatch = true
    router.replace({ path: '/search', query: {} })
  }
  reset()
}

function doSearch() {
  reset()
}

// 后端列表里的品相字段叫 conditionLevel，卡片组件读的是 condition，这里做一次适配
const decorate = (item) => ({ ...item, condition: item.conditionLevel || item.condition })

async function loadPage() {
  const my = gen
  const currentPage = page.value
  loading.value = true
  try {
    let res
    if (mineMode.value) {
      res = await listingApi.mine({ page: currentPage, size: PAGE_SIZE })
    } else {
      const params = { page: currentPage, size: PAGE_SIZE, sort: sort.value }
      if (keyword.value.trim()) params.keyword = keyword.value.trim()
      if (courseId.value) params.courseId = courseId.value
      if (textbookId.value) params.textbookId = textbookId.value
      if (condition.value) params.condition = condition.value
      res = await listingApi.search(params)
    }
    if (my !== gen) return
    const records = Array.isArray(res) ? res : res?.records || []
    const rows = records.map(decorate)
    if (currentPage === 1) {
      list.value = rows
      total.value = Array.isArray(res) ? rows.length : Number(res?.total) || rows.length
    } else {
      list.value = list.value.concat(rows)
    }
    page.value = currentPage + 1
    finished.value = records.length < PAGE_SIZE
  } catch {
    if (my !== gen) return
    // 请求层已提示，这里只标记失败，防止 van-list 无限重试
    failed.value = true
    finished.value = true
  } finally {
    if (my === gen) {
      loading.value = false
      loadedOnce.value = true
    }
  }
}

function reset() {
  gen += 1
  page.value = 1
  list.value = []
  total.value = 0
  finished.value = false
  loadedOnce.value = false
  failed.value = false
  loading.value = false
  loadPage()
}

// 从课程页/我的发布跳进来时 query 会变，需要按新条件重新搜
watch(
  () => route.fullPath,
  () => {
    if (route.path !== '/search') return
    if (skipWatch) {
      skipWatch = false
      return
    }
    hydrate()
    reset()
  }
)

onMounted(() => {
  hydrate()
  loadCourses()
  reset()
})
</script>

<style scoped>
.search-page {
  background: var(--bg);
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #fff;
  border-bottom: 1px solid var(--line);
  padding: 2px 0;
}
.topbar :deep(.van-search) {
  padding: 6px 8px;
}
.topbar :deep(.van-search__content) {
  background: var(--bg);
}
.search-btn {
  border: 0;
  background: none;
  color: var(--brand);
  font-size: 14px;
  font-weight: 600;
  padding: 0 4px;
  cursor: pointer;
}
.search-btn:active {
  opacity: 0.7;
}
.mine-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 12px 6px;
}
.mine-head h1 {
  margin: 0 0 2px;
  font-size: 22px;
}
.mine-head p {
  margin: 0;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px 0;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 46%;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-2);
  font-size: 13px;
  height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  cursor: pointer;
}
.chip.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
}
.chip.reset {
  color: var(--muted);
  border-style: dashed;
}
.sort-row {
  display: flex;
  gap: 4px;
  padding: 10px 12px 2px;
  overflow-x: auto;
}
.sort {
  flex: none;
  border: 0;
  background: none;
  color: var(--muted);
  font-size: 13px;
  padding: 6px 10px;
  border-radius: 8px;
  cursor: pointer;
}
.sort.active {
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
}
.link {
  border: 0;
  background: none;
  color: var(--brand);
  font-size: 13px;
  cursor: pointer;
}
.ghost {
  background: var(--card);
  border-radius: var(--radius);
  padding-bottom: 12px;
  overflow: hidden;
}
.ghost-cover {
  aspect-ratio: 4 / 3;
  border-radius: 0;
}
.ghost-line {
  height: 12px;
  margin: 10px 12px 0;
}
.ghost-line.short {
  width: 50%;
}
.empty-wrap {
  margin-top: 8px;
  padding: 0;
}
.empty-wrap :deep(.empty) {
  padding: 40px 16px;
}
.empty-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}
</style>

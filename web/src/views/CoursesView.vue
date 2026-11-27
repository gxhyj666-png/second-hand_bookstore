<template>
  <div class="page courses-page">
    <van-nav-bar title="课程教材" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <van-search
        v-model="keyword"
        shape="round"
        placeholder="搜索课程名或课程代码"
        @search="onSearch"
        @clear="onSearch"
      />

      <div v-if="!list.length && loadedOnce && !loading" class="card empty-wrap">
        <EmptyState :text="error || '没有找到相关课程'" icon="📚">
          <van-button v-if="error" size="small" round type="primary" @click="onSearch">重新加载</van-button>
        </EmptyState>
      </div>

      <van-list
        v-else
        v-model:loading="loading"
        :finished="finished"
        :finished-text="list.length ? '没有更多课程了' : ''"
        :immediate-check="false"
        @load="loadMore"
      >
        <div v-for="c in list" :key="c.id" class="card course">
          <div class="course-head" @click="toggle(c)">
            <div class="course-main">
              <div class="between">
                <strong class="course-name">{{ c.name || '未命名课程' }}</strong>
                <van-icon :name="expandedId === c.id ? 'arrow-up' : 'arrow-down'" class="muted" />
              </div>
              <p class="muted meta">
                <span v-if="c.code">课程代码 {{ c.code }}</span>
                <span v-if="c.college"> · {{ c.college }}</span>
                <span v-if="c.teacher"> · {{ c.teacher }}</span>
              </p>
              <p class="muted meta">
                <span v-if="c.credit">学分 {{ c.credit }}</span>
                <span v-if="c.term"> · {{ c.term }}</span>
                <span v-if="c.major"> · 适用 {{ c.major }}</span>
              </p>
            </div>
          </div>

          <div v-if="expandedId === c.id" class="textbooks">
            <div v-if="detailLoading" class="tb-loading">
              <van-loading size="18">正在加载教材</van-loading>
            </div>
            <p v-else-if="!booksOf(c.id).length" class="muted no-book">
              这门课还没有绑定教材，去<router-link to="/search" class="link">找书</router-link>看看
            </p>
            <template v-else>
              <div
                v-for="b in booksOf(c.id)"
                :key="b.mappingId || b.textbookId"
                class="book"
                @click="goSearch(b)"
              >
                <div class="book-cover">
                  <img
                    v-if="b.coverUrl && !coverFailed[b.textbookId]"
                    :src="b.coverUrl"
                    :alt="b.title"
                    loading="lazy"
                    @error="coverFailed[b.textbookId] = true"
                  />
                  <span v-else class="book-placeholder">📘</span>
                </div>
                <div class="book-info">
                  <p class="book-title clamp-2">{{ b.title || '未命名教材' }}</p>
                  <p class="muted book-meta clamp-1">
                    {{ [b.author, b.publisher, b.edition].filter(Boolean).join(' · ') || '出版社信息待补充' }}
                  </p>
                  <div class="book-foot">
                    <span class="tag" :class="b.bindType === 'REQUIRED' ? 'warn' : 'muted'">
                      {{ bindText(b.bindType) }}
                    </span>
                    <span v-if="b.price" class="price">{{ money(b.price) }}</span>
                  </div>
                </div>
                <van-icon name="arrow" class="muted" />
              </div>
            </template>
          </div>
        </div>
      </van-list>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi } from '@/api'
import { money } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const PAGE_SIZE = 10

const keyword = ref('')
const list = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const loadedOnce = ref(false)
const error = ref('')

const expandedId = ref(null)
const detailLoading = ref(false)
const bookCache = reactive({})
const coverFailed = reactive({})

const booksOf = (id) => bookCache[id] || []

const bindText = (t) => ({ REQUIRED: '必修', REFERENCE: '参考' })[t] || '课程用书'

async function fetchPage() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await catalogApi.courses({
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: PAGE_SIZE
    })
    const records = res?.records || []
    list.value = page.value === 1 ? records : list.value.concat(records)
    finished.value = records.length < PAGE_SIZE
    loadedOnce.value = true
  } catch {
    // 请求层已提示，这里停止继续加载，避免触底循环重试
    error.value = '课程加载失败，请稍后重试'
    finished.value = true
  } finally {
    loading.value = false
  }
}

function loadMore() {
  page.value += 1
  fetchPage()
}

function onSearch() {
  page.value = 1
  finished.value = false
  list.value = []
  expandedId.value = null
  fetchPage()
}

async function toggle(c) {
  if (expandedId.value === c.id) {
    expandedId.value = null
    return
  }
  expandedId.value = c.id
  if (bookCache[c.id]) return
  detailLoading.value = true
  try {
    const res = await catalogApi.courseTextbooks(c.id)
    bookCache[c.id] = Array.isArray(res) ? res : res?.records || []
  } catch {
    bookCache[c.id] = []
  } finally {
    detailLoading.value = false
  }
}

function goSearch(b) {
  if (!b.textbookId) return
  router.push({ path: '/search', query: { textbookId: b.textbookId } })
}

onMounted(fetchPage)
</script>

<style scoped>
.courses-page {
  background: var(--bg);
}
.empty-wrap {
  margin-top: 16px;
  padding: 0;
}
.course {
  margin-top: 10px;
  padding: 12px 14px;
}
.course-head {
  cursor: pointer;
}
.course-name {
  font-size: 15px;
}
.meta {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.5;
}
.textbooks {
  margin-top: 10px;
  border-top: 1px dashed var(--line);
  padding-top: 8px;
}
.tb-loading {
  padding: 12px 0;
  display: grid;
  place-items: center;
}
.no-book {
  font-size: 12px;
  padding: 10px 0;
  margin: 0;
}
.link {
  color: var(--brand);
}
.book {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}
.book:last-child {
  border-bottom: 0;
}
.book-cover {
  width: 44px;
  height: 58px;
  border-radius: 6px;
  overflow: hidden;
  background: #f1f4f2;
  flex: none;
  display: grid;
  place-items: center;
}
.book-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.book-placeholder {
  font-size: 20px;
}
.book-info {
  flex: 1;
  min-width: 0;
}
.book-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
}
.book-meta {
  margin: 3px 0;
  font-size: 11px;
}
.book-foot {
  display: flex;
  align-items: center;
  gap: 8px;
}
.book-foot .tag {
  font-size: 10px;
}
.book-foot .price {
  font-size: 13px;
}
</style>

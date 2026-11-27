<template>
  <div class="page publish-page">
    <van-nav-bar title="发布教材" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <div class="intro">
        <h1>把用过的教材<br />传给下一届同学</h1>
        <p>填写真实品相与取书地点，成交更快。</p>
      </div>

      <van-cell-group inset class="form-group">
        <van-cell title="教材" :value="book ? book.title : '请选择'" is-link @click="openBookPopup" />
        <van-cell v-if="book" title="作者 / 出版社" :value="[book.author, book.publisher].filter(Boolean).join(' · ') || '--'" />
        <van-cell v-if="book" title="版次 / 定价" :value="[book.edition, book.price ? money(book.price) : ''].filter(Boolean).join(' · ') || '--'" />
        <van-cell title="课程（可选）" :value="course ? course.name : '不指定课程'" is-link @click="openCoursePopup" />
      </van-cell-group>

      <div v-if="book" class="price-hint">
        <span>参考定价</span>
        <strong v-if="pi && pi.suggest" class="price">{{ money(pi.suggest) }}</strong>
        <span v-else class="muted">暂无可参考成交价</span>
        <span v-if="pi && pi.basis" class="muted">· {{ priceBasisText(pi.basis) }}</span>
      </div>

      <van-cell-group inset class="form-group">
        <van-field
          v-model="form.price"
          type="number"
          label="售价"
          placeholder="0.00"
          input-align="right"
        >
          <template #button><span class="unit">元</span></template>
        </van-field>
        <van-field
          v-if="book && book.price"
          :model-value="money(book.price)"
          label="教材原价"
          readonly
          input-align="right"
        />
        <van-cell title="品相" :value="form.condition ? conditionText(form.condition) : '请选择'" is-link @click="conditionPopup = true" />
        <van-field
          v-model="form.description"
          label="成色描述"
          type="textarea"
          rows="3"
          autosize
          maxlength="200"
          show-word-limit
          placeholder="例如：只写了前三章笔记，无缺页，书角轻微磨损"
        />
        <van-field
          v-model="form.dorm"
          label="交易地点"
          maxlength="40"
          placeholder="例如：梅园 3 栋楼下 / 图书馆西门"
        />
      </van-cell-group>

      <section class="card image-card">
        <div class="between">
          <strong>教材图片</strong>
          <span class="muted">{{ images.length }} / 3</span>
        </div>
        <p class="muted image-tip">后端只保存图片地址，请粘贴以 http(s) 开头的图片链接，第一张作为封面。</p>
        <div v-for="(_, idx) in images" :key="idx" class="image-row">
          <van-field v-model="images[idx]" :placeholder="`图片 ${idx + 1} 链接`" />
          <button class="remove" type="button" aria-label="删除图片" @click="removeImage(idx)">
            <van-icon name="cross" size="16" />
          </button>
        </div>
        <van-button
          v-if="images.length < 3"
          size="small"
          round
          plain
          type="primary"
          icon="plus"
          class="add-image"
          @click="addImage"
        >
          添加图片链接
        </van-button>
      </section>

      <div class="submit-wrap">
        <van-button block round type="primary" :loading="submitting" loading-text="发布中" @click="submit">
          发布教材
        </van-button>
        <p class="muted agree">发布即表示你确认教材信息真实，且愿意在自提点当面交易。</p>
      </div>
    </div>

    <van-popup v-model:show="bookPopup" position="bottom" round :style="{ height: '76%' }">
      <div class="picker-panel">
        <div class="picker-head between">
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
        <div class="picker-body">
          <div v-if="bookLoading" class="picker-loading"><van-loading size="22">搜索中</van-loading></div>
          <EmptyState v-else-if="!books.length" text="没有搜到教材，换个关键词试试" icon="📚" />
          <button
            v-for="b in books"
            :key="b.id"
            class="pick-row"
            type="button"
            @click="chooseBook(b)"
          >
            <div class="pick-main">
              <strong class="clamp-2">{{ b.title }}</strong>
              <p class="muted clamp-1">{{ [b.author, b.publisher, b.edition].filter(Boolean).join(' · ') }}</p>
            </div>
            <span v-if="b.price" class="price">{{ money(b.price) }}</span>
          </button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="coursePopup" position="bottom" round :style="{ height: '70%' }">
      <div class="picker-panel">
        <div class="picker-head between">
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
        <div class="picker-body">
          <button class="pick-row" type="button" @click="chooseCourse(null)">
            <div class="pick-main">
              <strong>不指定课程</strong>
              <p class="muted">教材不对应具体课程时选择此项</p>
            </div>
          </button>
          <div v-if="courseLoading" class="picker-loading"><van-loading size="22">搜索中</van-loading></div>
          <EmptyState v-else-if="!courses.length" text="没有搜到课程" icon="🎓" />
          <button
            v-for="c in courses"
            :key="c.id"
            class="pick-row"
            type="button"
            @click="chooseCourse(c)"
          >
            <div class="pick-main">
              <strong>{{ c.name }}</strong>
              <p class="muted clamp-1">{{ [c.code, c.college, c.teacher].filter(Boolean).join(' · ') }}</p>
            </div>
          </button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="conditionPopup" position="bottom" round>
      <van-picker
        title="选择品相"
        :columns="CONDITION_OPTIONS.map((c) => ({ text: c.text, value: c.value }))"
        @confirm="onConditionConfirm"
        @cancel="conditionPopup = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { catalogApi, listingApi } from '@/api'
import { CONDITION_OPTIONS, conditionText, money } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const book = ref(null)
const course = ref(null)
const pi = ref(null)

const form = reactive({
  price: '',
  condition: '',
  description: '',
  dorm: ''
})

const images = ref([''])
const submitting = ref(false)

const bookPopup = ref(false)
const bookKeyword = ref('')
const books = ref([])
const bookLoading = ref(false)
let bookTimer = null

const coursePopup = ref(false)
const courseKeyword = ref('')
const courses = ref([])
const courseLoading = ref(false)
let courseTimer = null

const conditionPopup = ref(false)

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

function priceBasisText(basis) {
  return basis === 'MARKET' ? '基于近期成交' : '基于教材定价'
}

function onConditionConfirm({ selectedOptions }) {
  form.condition = selectedOptions?.[0]?.value || ''
  conditionPopup.value = false
}

function openBookPopup() {
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

function onBookKeyword(value) {
  bookKeyword.value = value
  clearTimeout(bookTimer)
  bookTimer = setTimeout(searchBooks, 350)
}

async function chooseBook(b) {
  book.value = b
  bookPopup.value = false
  pi.value = null
  // 二手教材常见按定价四折起步，用户仍可修改
  if (!form.price && b.price) form.price = (Number(b.price) * 0.4).toFixed(2)
  loadPriceIndex(b.id)
}

async function loadPriceIndex(bookId) {
  try {
    pi.value = await catalogApi.priceIndex(bookId)
    if (pi.value?.suggest && !form.price) form.price = String(pi.value.suggest)
  } catch {
    pi.value = null
  }
}

function openCoursePopup() {
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

function onCourseKeyword(value) {
  courseKeyword.value = value
  clearTimeout(courseTimer)
  courseTimer = setTimeout(searchCourses, 350)
}

function chooseCourse(c) {
  course.value = c
  coursePopup.value = false
}

function addImage() {
  if (images.value.length < 3) images.value.push('')
}

function removeImage(idx) {
  images.value.splice(idx, 1)
  if (!images.value.length) images.value.push('')
}

async function submit() {
  if (submitting.value) return
  if (!book.value) return showToast('请先选择教材')
  const price = Number(form.price)
  if (!(price > 0)) return showToast('请填写大于 0 的售价')
  if (!form.condition) return showToast('请选择教材品相')

  const urls = images.value.map((u) => String(u || '').trim()).filter(Boolean)
  if (urls.some((u) => !/^https?:\/\//i.test(u))) {
    return showToast('图片链接需以 http:// 或 https:// 开头')
  }

  submitting.value = true
  try {
    const res = await listingApi.publish({
      textbookId: book.value.id,
      courseId: course.value?.id || null,
      price,
      condition: form.condition,
      description: form.description.trim(),
      dorm: form.dorm.trim(),
      images: urls
    })
    showToast('发布成功')
    if (res?.id) router.replace(`/listing/${res.id}`)
    else router.replace('/search?mine=1')
  } catch {
    // 请求层已经提示，保留表单内容方便重试
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  searchBooks()
})
</script>

<style scoped>
.publish-page {
  background: var(--bg);
}
.intro {
  padding: 18px 4px 10px;
}
.intro h1 {
  margin: 0 0 6px;
  font-size: 24px;
  line-height: 1.35;
  font-weight: 800;
  color: var(--ink);
}
.intro p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.form-group {
  margin: 10px 0 0;
  overflow: hidden;
}
.form-group :deep(.van-cell__value) {
  color: var(--ink-2);
}
.unit {
  color: var(--muted);
  margin-left: 4px;
}
.price-hint {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 10px 20px 0;
  font-size: 12px;
  color: var(--muted);
}
.price-hint strong {
  font-size: 15px;
}
.image-card {
  margin-top: 12px;
}
.image-card strong {
  font-size: 14px;
}
.image-tip {
  margin: 6px 0 10px;
  line-height: 1.5;
}
.image-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.image-row :deep(.van-cell) {
  flex: 1;
  padding: 8px 10px;
  background: var(--bg);
  border-radius: 8px;
}
.remove {
  border: 0;
  background: #f4f5f6;
  color: var(--muted);
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  cursor: pointer;
  flex: none;
}
.add-image {
  margin-top: 2px;
}
.submit-wrap {
  padding: 18px 0 24px;
}
.agree {
  margin: 10px 8px 0;
  text-align: center;
  font-size: 11px;
  line-height: 1.6;
}
.picker-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.picker-head {
  padding: 16px 16px 4px;
  font-size: 16px;
}
.picker-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 20px;
}
.picker-loading {
  padding: 30px 0;
  display: grid;
  place-items: center;
}
.pick-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: none;
  padding: 12px 6px;
  cursor: pointer;
}
.pick-row:active {
  background: var(--bg);
}
.pick-main {
  flex: 1;
  min-width: 0;
}
.pick-main strong {
  font-size: 14px;
  font-weight: 600;
}
.pick-main p {
  margin: 2px 0 0;
  font-size: 12px;
}
</style>

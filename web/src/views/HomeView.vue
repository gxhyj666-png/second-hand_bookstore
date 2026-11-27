<template>
  <div class="page home">
    <div class="hero">
      <div class="container">
        <div class="between brand-row">
          <div class="brand">同窗书<span class="brand-dot">·</span><small>校园教材循环</small></div>
          <button class="msg-btn" type="button" @click="go('/notices')">
            <van-icon name="chat-o" size="20" />
            <span v-if="store.isLogin && store.unread" class="badge">{{ badgeText }}</span>
          </button>
        </div>

        <h1 class="greet">{{ greeting }}，{{ store.isLogin ? store.nickname : '同学' }}</h1>
        <p class="sub">
          <template v-if="store.isLogin">
            信用分 <strong>{{ store.creditScore }}</strong>
            <span v-if="store.verified" class="tag">已认证</span>
            <span v-else class="muted">· 完成学号认证更可信</span>
          </template>
          <template v-else>校内实名交易，毕业季把教材传给下一届</template>
        </p>

        <div class="search-pill" role="button" tabindex="0" @click="go('/search')" @keyup.enter="go('/search')">
          <van-icon name="search" size="18" />
          <span>搜课程、书名或 ISBN</span>
          <span class="search-go">找书</span>
        </div>
      </div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="container content">
        <nav class="tiles">
          <router-link v-for="t in tiles" :key="t.path" class="tile" :to="t.path">
            <span class="tile-icon" :style="{ background: t.bg, color: t.color }">
              <van-icon :name="t.icon" size="20" />
              <span v-if="t.badge" class="tile-badge">{{ badgeText }}</span>
            </span>
            <span class="tile-text">{{ t.text }}</span>
          </router-link>
        </nav>

        <SectionTitle title="为你推荐" to="/search" more="更多教材" />

        <template v-if="loading">
          <div class="grid-list">
            <div v-for="i in 4" :key="i" class="ghost">
              <div class="skeleton ghost-cover"></div>
              <div class="skeleton ghost-line"></div>
              <div class="skeleton ghost-line short"></div>
            </div>
          </div>
        </template>
        <template v-else-if="recommend.length">
          <div class="grid-list">
            <ListingCard v-for="item in recommend" :key="item.id" :item="item" />
          </div>
        </template>
        <div v-else class="card empty-card">
          <EmptyState :text="failed ? '暂时没能连上图书角，稍后再试试' : '还没有在售教材，去发布第一本吧'" icon="📖">
            <van-button size="small" type="primary" round @click="failed ? loadAll() : go('/publish')">
              {{ failed ? '重新加载' : '去发布' }}
            </van-button>
          </EmptyState>
        </div>

        <SectionTitle title="班级接龙" to="/group-buys" more="全部接龙" />

        <div v-if="groupLoading" class="group-strip">
          <div v-for="i in 2" :key="i" class="skeleton ghost-group"></div>
        </div>
        <div v-else-if="groups.length" class="group-strip">
          <article
            v-for="g in groups"
            :key="g.id"
            class="group-card"
            role="button"
            tabindex="0"
            @click="go(`/group-buys/${g.inviteCode}`)"
            @keyup.enter="go(`/group-buys/${g.inviteCode}`)"
          >
            <div class="between">
              <strong class="clamp-1">{{ g.title || '班级接龙' }}</strong>
              <span class="tag" :class="groupTone(g.status)">{{ groupStatusText(g.status) }}</span>
            </div>
            <p class="muted clamp-1">{{ g.inviteCode ? `邀请码 ${g.inviteCode}` : '班级统一收书' }}</p>
            <div class="progress">
              <i :style="{ width: percent(g) + '%' }"></i>
            </div>
            <div class="between group-foot">
              <span class="muted">{{ g.currentCount || 0 }} / {{ g.targetCount || 0 }} 册</span>
              <span v-if="g.unitPrice" class="price">{{ money(g.unitPrice) }}</span>
            </div>
            <p class="muted deadline">
              <van-icon name="clock-o" /> 截止 {{ shortDate(g.deadline) || '待定' }}
            </p>
          </article>
        </div>
        <div v-else class="card empty-card">
          <EmptyState text="还没有进行中的接龙，发起一个吧" icon="🤝">
            <van-button size="small" round plain type="primary" @click="go('/group-buys')">去看看</van-button>
          </EmptyState>
        </div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi, groupBuyApi } from '@/api'
import { useUserStore } from '@/store/user'
import { money, shortDate } from '@/utils/format'
import ListingCard from '@/components/ListingCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import SectionTitle from '@/components/SectionTitle.vue'

defineOptions({ name: 'HomeView' })

const router = useRouter()
const store = useUserStore()

const recommend = ref([])
const groups = ref([])
const loading = ref(true)
const groupLoading = ref(true)
const refreshing = ref(false)
const failed = ref(false)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const badgeText = computed(() => (store.unread > 99 ? '99+' : String(store.unread)))

const tiles = computed(() => [
  { text: '找书', path: '/search', icon: 'search', bg: '#eaf4ef', color: '#2f7d5a' },
  { text: '发布', path: '/publish', icon: 'plus', bg: '#fff1ec', color: '#e6532f' },
  { text: '接龙', path: '/group-buys', icon: 'friends-o', bg: '#eef3ff', color: '#3b6fd4' },
  { text: '自提点', path: '/pickup', icon: 'location-o', bg: '#f3eefe', color: '#7a4fd0' },
  { text: '收藏', path: '/favorites', icon: 'star-o', bg: '#fff7e6', color: '#b26a00' },
  {
    text: '消息',
    path: '/notices',
    icon: 'chat-o',
    bg: '#eaf4ef',
    color: '#2f7d5a',
    badge: store.isLogin && store.unread > 0
  }
])

// 后端列表里的品相字段叫 conditionLevel，卡片组件读的是 condition，这里做一次适配
const decorate = (item) => ({ ...item, condition: item.conditionLevel || item.condition })

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

function groupStatusText(s) {
  return { OPEN: '进行中', DONE: '已成团', CLOSED: '已关闭' }[s] || s || '未知'
}

function groupTone(s) {
  if (s === 'CLOSED') return 'muted'
  if (s === 'DONE') return 'warn'
  return ''
}

function percent(g) {
  const target = Number(g.targetCount) || 0
  if (!target) return 0
  return Math.min(100, Math.round(((Number(g.currentCount) || 0) / target) * 100))
}

function go(path) {
  router.push(path)
}

async function loadRecommend() {
  loading.value = true
  try {
    const res = await catalogApi.recommend(8)
    recommend.value = recordsOf(res).map(decorate)
  } catch {
    // 网络层已经 toast 过，这里只负责降级为空态，避免白屏
    recommend.value = []
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  groupLoading.value = true
  try {
    // 后端接龙状态用 OPEN，不是任务描述里的 ACTIVE；传 ACTIVE 会过滤掉全部数据
    const res = await groupBuyApi.list({ status: 'OPEN', page: 1, size: 3 })
    groups.value = recordsOf(res)
  } catch {
    groups.value = []
  } finally {
    groupLoading.value = false
  }
}

async function loadAll() {
  failed.value = false
  await Promise.allSettled([loadRecommend(), loadGroups()])
}

async function onRefresh() {
  await loadAll()
  if (store.isLogin) await store.refreshUnread()
  refreshing.value = false
}

let activatedOnce = false
onMounted(() => {
  loadAll()
  if (store.isLogin) store.refreshUnread()
})
onActivated(() => {
  // 首次挂载时 activated 与 mounted 同时触发，跳过以免重复请求
  if (!activatedOnce) {
    activatedOnce = true
    return
  }
  if (store.isLogin) store.refreshUnread()
})
</script>

<style scoped>
.home {
  background: var(--bg);
}
.hero {
  padding: 16px 0 20px;
  background: linear-gradient(160deg, #2f7d5a 0%, #3f8f6c 58%, #56a381 100%);
  color: #fff;
  border-radius: 0 0 22px 22px;
}
.brand-row {
  align-items: flex-start;
}
.brand {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 1px;
}
.brand small {
  font-size: 11px;
  font-weight: 400;
  opacity: 0.75;
  margin-left: 4px;
  letter-spacing: 0;
}
.brand-dot {
  margin: 0 2px;
  opacity: 0.6;
}
.msg-btn {
  position: relative;
  border: 0;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  cursor: pointer;
}
.badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
}
.greet {
  margin: 14px 0 4px;
  font-size: 24px;
  line-height: 1.3;
  font-weight: 700;
}
.sub {
  margin: 0 0 14px;
  font-size: 13px;
  opacity: 0.88;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.sub strong {
  font-size: 15px;
}
.sub .tag {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.sub .muted {
  color: rgba(255, 255, 255, 0.75);
}
.search-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 8px 0 14px;
  border-radius: 999px;
  background: #fff;
  color: var(--muted);
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(12, 44, 30, 0.16);
}
.search-pill span:nth-child(2) {
  flex: 1;
}
.search-go {
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
  padding: 5px 14px;
  border-radius: 999px;
}
.content {
  min-height: 50vh;
  padding-bottom: 16px;
}
.tiles {
  margin: 16px 0 2px;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 4px;
}
.tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  border-radius: 12px;
}
.tile:active {
  background: rgba(0, 0, 0, 0.03);
}
.tile-icon {
  position: relative;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
}
.tile-badge {
  position: absolute;
  top: -5px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}
.tile-text {
  font-size: 12px;
  color: var(--ink-2);
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
.ghost-group {
  min-width: 240px;
  height: 132px;
  border-radius: var(--radius);
}
.empty-card {
  padding: 0;
}
.empty-card :deep(.empty) {
  padding: 30px 16px;
}
.group-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 2px 2px 6px;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
}
.group-card {
  flex: 0 0 250px;
  scroll-snap-align: start;
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px 14px;
  cursor: pointer;
}
.group-card .progress {
  margin: 10px 0 6px;
}
.progress {
  height: 8px;
  border-radius: 999px;
  background: #f0f2f3;
  overflow: hidden;
}
.progress i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #7fb69b, var(--brand));
  transition: width 0.3s ease;
}
.group-foot {
  font-size: 12px;
}
.deadline {
  margin: 8px 0 0;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}
@media (max-width: 420px) {
  .tiles {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    row-gap: 10px;
  }
}
</style>

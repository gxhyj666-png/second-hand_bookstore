<template>
  <div class="page mine-page">
    <header class="profile-hero">
      <div v-if="store.isLogin" class="profile">
        <div class="avatar">
          <img v-if="store.user?.avatar && !avatarError" :src="store.user.avatar" alt="头像" @error="avatarError = true" />
          <span v-else>{{ store.nickname.slice(0, 1) }}</span>
        </div>
        <div class="who">
          <div class="name-row">
            <h1>{{ store.nickname }}</h1>
            <span v-if="store.verified" class="verify-tag">已认证</span>
          </div>
          <p>{{ [store.user?.college, store.user?.major].filter(Boolean).join(' · ') || '还没有填写学院和专业' }}</p>
        </div>
        <van-icon name="setting-o" size="20" class="setting" @click="router.push('/profile')" />
      </div>

      <div v-else class="guest">
        <div class="avatar guest-avatar">?</div>
        <div class="who">
          <h1>还没有登录</h1>
          <p>登录后可以发布教材、参与接龙和查看订单</p>
        </div>
      </div>

      <div v-if="store.isLogin" class="stats">
        <button type="button" class="stat" @click="router.push('/credit')">
          <strong>{{ store.creditScore }}</strong>
          <span>信用分</span>
        </button>
        <button type="button" class="stat" @click="router.push('/verify')">
          <strong class="small">{{ verifyText }}</strong>
          <span>学号认证</span>
        </button>
        <button type="button" class="stat" @click="router.push('/favorites')">
          <strong>收藏</strong>
          <span>想买的书</span>
        </button>
      </div>

      <div v-else class="auth-actions">
        <van-button round type="primary" @click="router.push('/login')">登录</van-button>
        <van-button round plain class="ghost-btn" @click="router.push('/register')">注册</van-button>
      </div>
    </header>

    <div class="container">
      <section v-if="store.isLogin && !store.verified" class="verify-banner" @click="router.push('/verify')">
        <van-icon name="idcard" size="22" />
        <div>
          <strong>{{ store.user?.verifyState === 'PENDING' ? '学生认证审核中' : '完成学号认证' }}</strong>
          <p class="muted">
            {{ store.user?.verifyState === 'PENDING' ? '已提交，等待管理员审核' : '认证后交易更受信任，快去认证吧' }}
          </p>
        </div>
        <van-icon name="arrow" />
      </section>

      <div class="quick-grid">
        <button v-for="q in QUICK" :key="q.path" type="button" class="quick" @click="go(q)">
          <span class="quick-icon">
            <van-icon :name="q.icon" size="20" />
            <em v-if="q.path === '/notices' && store.unread" class="badge">{{ store.unread > 99 ? '99+' : store.unread }}</em>
          </span>
          <span>{{ q.text }}</span>
        </button>
      </div>

      <van-cell-group inset class="menu-group">
        <van-cell
          v-for="m in MENU"
          :key="m.path"
          :title="m.text"
          :icon="m.icon"
          is-link
          @click="go(m)"
        >
          <template #value>
            <span v-if="m.path === '/notices' && store.unread" class="unread">{{ store.unread }} 条未读</span>
            <span v-else-if="m.path === '/pickup' && defaultPointName" class="muted">{{ defaultPointName }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <div v-if="store.isLogin" class="logout-wrap">
        <van-button block round plain type="danger" :loading="loggingOut" @click="logout">退出登录</van-button>
      </div>

      <p class="muted foot">教材循环 · 让每一本书都多读一届</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const avatarError = ref(false)
const loggingOut = ref(false)
const defaultPointName = ref('')

const QUICK = [
  { text: '我的发布', path: '/search?mine=1', icon: 'notes-o' },
  { text: '我的收藏', path: '/favorites', icon: 'star-o' },
  { text: '消息', path: '/notices', icon: 'chat-o', badge: true },
  { text: '班级接龙', path: '/group-buys', icon: 'friends-o' }
]

const MENU = [
  { text: '我的发布', path: '/search?mine=1', icon: 'notes-o' },
  { text: '信用记录', path: '/credit', icon: 'chart-trending-o' },
  { text: '纠纷工单', path: '/tickets', icon: 'service-o' },
  { text: '班级接龙', path: '/group-buys', icon: 'friends-o' },
  { text: '自提点', path: '/pickup', icon: 'location-o' },
  { text: '个人资料', path: '/profile', icon: 'contact-o' },
  { text: '学号认证', path: '/verify', icon: 'idcard' }
]

const verifyText = computed(
  () =>
    ({
      VERIFIED: '已认证',
      PENDING: '审核中',
      REJECTED: '未通过'
    })[store.user?.verifyState] || '未认证'
)

function go(item) {
  router.push(item.path)
}

async function load() {
  if (!store.isLogin) return
  try {
    await store.fetchMe()
  } catch {
    // token 失效时请求层会跳登录，这里保持页面可用
  }
  store.refreshUnread()
  try {
    const raw = localStorage.getItem('tl_default_point')
    if (raw) defaultPointName.value = JSON.parse(raw)?.name || ''
  } catch {
    defaultPointName.value = ''
  }
}

async function logout() {
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '退出后需要重新登录才能查看订单，确定退出吗？',
      confirmButtonText: '退出'
    })
  } catch {
    return
  }
  loggingOut.value = true
  store.logout()
  loggingOut.value = false
  showToast('已退出登录')
  router.replace('/home')
}

onMounted(load)
</script>

<style scoped>
.mine-page {
  background: var(--bg);
}
.profile-hero {
  background: linear-gradient(150deg, #245f45 0%, #2f7d5a 60%, #4fa37e 100%);
  color: #fff;
  padding: 30px 18px 58px;
  border-radius: 0 0 20px 20px;
}
.profile {
  display: flex;
  align-items: center;
  gap: 14px;
}
.avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  overflow: hidden;
  flex: none;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.45);
  font-size: 24px;
  font-weight: 800;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.guest-avatar {
  color: rgba(255, 255, 255, 0.8);
}
.who {
  flex: 1;
  min-width: 0;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.who h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
}
.who p {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.85;
}
.verify-tag {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.45);
  padding: 1px 6px;
  border-radius: 999px;
}
.setting {
  opacity: 0.9;
  cursor: pointer;
}
.stats {
  margin-top: 20px;
  display: flex;
  background: rgba(255, 255, 255, 0.13);
  border-radius: 12px;
  overflow: hidden;
}
.stat {
  flex: 1;
  border: 0;
  background: none;
  color: #fff;
  padding: 10px 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
}
.stat + .stat {
  border-left: 1px solid rgba(255, 255, 255, 0.18);
}
.stat strong {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.2;
}
.stat strong.small {
  font-size: 14px;
  padding: 4px 0;
}
.stat span {
  font-size: 11px;
  opacity: 0.85;
}
.auth-actions {
  margin-top: 22px;
  display: flex;
  gap: 10px;
}
.auth-actions :deep(.van-button) {
  flex: 1;
}
.ghost-btn {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.7);
  background: transparent;
}
.container {
  margin-top: -36px;
  position: relative;
}
.verify-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff8e8;
  border: 1px solid #f5e2b8;
  border-radius: 12px;
  padding: 12px;
  color: #a9741b;
  margin-bottom: 12px;
  cursor: pointer;
}
.verify-banner strong {
  font-size: 13px;
  color: var(--ink);
}
.verify-banner p {
  margin: 2px 0 0;
  font-size: 11px;
  color: #a9741b;
}
.verify-banner div {
  flex: 1;
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}
.quick {
  border: 0;
  background: #fff;
  border-radius: 12px;
  padding: 12px 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-2);
  cursor: pointer;
  box-shadow: var(--shadow);
}
.quick-icon {
  position: relative;
  color: var(--brand);
  display: inline-flex;
}
.badge {
  position: absolute;
  top: -6px;
  right: -10px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 9px;
  font-style: normal;
  line-height: 15px;
  text-align: center;
}
.menu-group {
  overflow: hidden;
}
.unread {
  color: var(--accent);
  font-size: 12px;
}
.logout-wrap {
  padding: 22px 0 10px;
}
.foot {
  text-align: center;
  font-size: 11px;
  padding: 14px 0 8px;
}
</style>

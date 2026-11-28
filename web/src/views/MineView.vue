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
            <span class="trade-tag" :class="{ off: !store.canTrade }">
              {{ store.canTrade ? '可交易' : '未认证' }}
            </span>
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
        <button type="button" class="stat" @click="router.push('/verify-center')">
          <strong class="small">{{ verifyText }}</strong>
          <span>认证状态</span>
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
      <section v-if="store.isLogin && !store.canTrade" class="verify-banner" @click="router.push('/verify-center')">
        <van-icon :name="store.user?.verifyState === 'PENDING' ? 'underway-o' : 'idcard'" size="22" />
        <div>
          <strong>{{ store.user?.verifyState === 'PENDING' ? '认证审核中' : '完成认证后才能交易' }}</strong>
          <p class="muted">
            {{ store.user?.verifyState === 'PENDING' ? '资料已提交，审核通过后即可交易' : verifyHint }}
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
import { verifyApi } from '@/api'

const router = useRouter()
const store = useUserStore()

const avatarError = ref(false)
const loggingOut = ref(false)
const defaultPointName = ref('')

const QUICK = [
  { text: '我的发布', path: '/search?mine=1', icon: 'notes-o' },
  { text: '我的收藏', path: '/favorites', icon: 'star-o' },
  { text: '私信', path: '/messages', icon: 'chat-o' },
  { text: '消息通知', path: '/notices', icon: 'volume-o', badge: true }
]

const MENU = computed(() => {
  const role = store.user?.role
  const list = [
    { text: '实名 / 学生认证', path: '/verify-center', icon: 'idcard' },
    { text: '我的发布', path: '/search?mine=1', icon: 'notes-o' },
    { text: '私信会话', path: '/messages', icon: 'chat-o' }
  ]
  if (role === 'OPERATOR') {
    list.push({ text: '运营中心', path: '/operator', icon: 'friends-o' })
  }
  if (!role || role === 'STUDENT' || role === 'LEADER') {
    list.push({ text: '进阶商户 / 运营', path: '/upgrade', icon: 'upgrade' })
  }
  if (role === 'MERCHANT' || role === 'ADMIN') {
    list.push({ text: '商户中心', path: '/merchant', icon: 'shop-o' })
  }
  if (store.user?.merchantId) {
    list.push({ text: '商户工作台', path: '/merchant/workbench', icon: 'apps-o' })
  }
  list.push(
    { text: '信用记录', path: '/credit', icon: 'chart-trending-o' },
    { text: '纠纷工单', path: '/tickets', icon: 'service-o' },
    { text: '班级接龙', path: '/group-buys', icon: 'friends-o' },
    { text: '自提点', path: '/pickup', icon: 'location-o' },
    { text: '个人资料', path: '/profile', icon: 'contact-o' }
  )
  return list
})

const verifyText = computed(() => {
  if (store.canTrade) return '可交易'
  if (store.user?.idVerified || store.user?.studentVerified) return '待完善'
  return '未认证'
})

const verifyHint = computed(() => {
  if (!store.user?.idVerified) return '第 1 步：实名认证（姓名 + 身份证）'
  if (!store.user?.studentVerified) return '第 2 步：学生认证（学校 + 学号）'
  if (!store.user?.faceVerified) return '第 3 步：人脸核验'
  return '认证完成，可以正常买卖教材'
})

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
    const vs = await verifyApi.status()
    if (vs) {
      store.setUser({
        ...(store.user || {}),
        idVerified: vs.idVerified,
        studentVerified: vs.studentVerified,
        faceVerified: vs.faceVerified,
        verifyState: vs.verifyState || store.user?.verifyState
      })
    }
  } catch {
    // 认证状态查询失败不影响页面其它信息展示
  }
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
.trade-tag {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--brand);
  padding: 1px 7px;
  border-radius: 999px;
  font-weight: 700;
}
.trade-tag.off {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.4);
  font-weight: 400;
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
.verify-banner.pending {
  background: #eef4ff;
  border-color: #d3e2fb;
  color: #3d6ea8;
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

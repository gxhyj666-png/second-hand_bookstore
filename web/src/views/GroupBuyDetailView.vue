<template>
  <div class="page group-detail-page">
    <van-nav-bar title="接龙详情" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div v-if="loading" class="container loading-wrap">
      <div class="skeleton line-lg"></div>
      <div class="skeleton line"></div>
      <div class="skeleton line short"></div>
    </div>

    <div v-else-if="!group" class="container">
      <div class="card empty-wrap">
        <EmptyState :text="loadError || '接龙不存在或已关闭'" icon="👥">
          <van-button size="small" round type="primary" @click="load">重新加载</van-button>
        </EmptyState>
      </div>
    </div>

    <template v-else>
      <div class="container">
        <section class="hero">
          <div class="between">
            <span class="tag invite-tag" @click="copyCode">
              {{ group.inviteCode }}
              <van-icon name="description" />
            </span>
            <span class="tag" :class="statusTone">{{ statusText }}</span>
          </div>
          <h1>{{ group.title || '教材接龙' }}</h1>
          <p class="hero-sub">
            {{ [courseName, textbookTitle].filter(Boolean).join(' · ') || '教材接龙' }}
          </p>
          <div class="hero-foot">
            <span v-if="group.unitPrice" class="price unit">{{ money(group.unitPrice) }}/册</span>
            <span class="deadline">
              <van-icon name="clock-o" />
              {{ deadlineText }}
            </span>
          </div>
        </section>

        <section class="card">
          <div class="between">
            <h2 class="card-title">接龙进度</h2>
            <span class="muted">{{ group.currentCount ?? 0 }} / {{ group.targetCount ?? 0 }} 册</span>
          </div>
          <div class="bar"><span :style="{ width: progress + '%' }" /></div>
          <p class="muted progress-tip">
            {{ progress >= 100 ? '已达成目标，团长可以统一收书了' : `还差 ${Math.max(0, (group.targetCount || 0) - (group.currentCount || 0))} 册达成目标` }}
          </p>
          <div class="leader">
            <van-icon name="manager-o" />
            <span>团长：{{ leaderName || '同学' }}</span>
            <span v-if="courseName" class="muted">· {{ courseName }}</span>
          </div>
        </section>

        <section class="card">
          <div class="between">
            <h2 class="card-title">参与同学</h2>
            <span class="muted">{{ participants.length }} 人</span>
          </div>
          <div v-if="!participants.length" class="muted empty-participants">还没有人参与，分享邀请码给同学吧</div>
          <ul v-else class="people">
            <li v-for="(p, idx) in participants" :key="p.userId || idx">
              <div class="avatar">{{ (p.nickname || '同').slice(0, 1) }}</div>
              <span class="name">
                {{ p.nickname || '同学' }}
                <span v-if="Number(p.userId) === Number(group.leaderId)" class="tag muted">团长</span>
              </span>
              <span class="qty">{{ p.quantity || 1 }} 册</span>
            </li>
          </ul>
        </section>

        <p class="muted note">
          接龙仅用于统计人数，教材交易仍需在教材详情页下单，由平台担保交易。
        </p>
      </div>

      <div v-if="canOperate" class="action-bar">
        <div class="action-inner">
          <template v-if="isLeader">
            <van-button
              v-if="group.status === 'OPEN'"
              block
              round
              plain
              type="danger"
              :loading="closing"
              @click="closeGroup"
            >
              结束接龙
            </van-button>
          </template>
          <template v-else>
            <van-button block round type="primary" :disabled="!joinable" @click="openJoin">
              {{ joinButtonText }}
            </van-button>
          </template>
        </div>
      </div>
    </template>

    <van-popup v-model:show="joinPopup" position="bottom" round :style="{ maxHeight: '70%' }">
      <div class="join-panel">
        <div class="between panel-head">
          <strong>参与接龙</strong>
          <van-icon name="cross" size="18" @click="joinPopup = false" />
        </div>
        <van-cell-group inset>
          <van-cell title="需要册数" center>
            <template #right-icon>
              <van-stepper v-model="joinForm.quantity" :min="1" :max="99" integer />
            </template>
          </van-cell>
          <van-field
            v-model="joinForm.contact"
            label="联系方式"
            maxlength="40"
            placeholder="微信号或手机号，方便团长联系"
          />
        </van-cell-group>
        <p v-if="group.unitPrice" class="muted sum">
          预估金额 <strong class="price">{{ money(Number(group.unitPrice) * joinForm.quantity) }}</strong>
        </p>
        <div class="panel-foot">
          <van-button block round type="primary" :loading="joining" loading-text="提交中" @click="submitJoin">
            确认参与
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { groupBuyApi } from '@/api'
import { useUserStore } from '@/store/user'
import { money, remainText } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const inviteCode = String(route.params.inviteCode || '')
const detail = ref(null)
const loading = ref(true)
const loadError = ref('')

const joinPopup = ref(false)
const joining = ref(false)
const closing = ref(false)
const joinForm = reactive({ quantity: 1, contact: '' })

const group = computed(() => detail.value?.group || null)
const participants = computed(() => detail.value?.participants || [])
const courseName = computed(() => detail.value?.courseName || '')
const textbookTitle = computed(() => detail.value?.textbookTitle || '')
const leaderName = computed(() => detail.value?.leaderName || '')
const isLeader = computed(
  () => Boolean(store.userId) && Number(group.value?.leaderId) === Number(store.userId)
)

const progress = computed(() => {
  if (typeof detail.value?.progress === 'number') return Math.min(100, detail.value.progress)
  const target = Number(group.value?.targetCount) || 0
  if (!target) return 0
  return Math.min(100, Math.round(((Number(group.value?.currentCount) || 0) / target) * 100))
})

const statusText = computed(
  () => ({ OPEN: '进行中', DONE: '已达成', CLOSED: '已关闭' })[group.value?.status] || '进行中'
)

const statusTone = computed(() => {
  if (group.value?.status === 'DONE') return 'warn'
  if (group.value?.status === 'CLOSED') return 'muted'
  return ''
})

const expired = computed(
  () => Boolean(group.value?.deadline) && new Date(group.value.deadline).getTime() <= Date.now()
)

const joinable = computed(() => group.value?.status === 'OPEN' && !expired.value)
const joinButtonText = computed(() => {
  if (group.value?.status === 'DONE') return '已达成目标'
  if (group.value?.status === 'CLOSED') return '接龙已关闭'
  if (expired.value) return '接龙已截止'
  return '我要参与'
})
const canOperate = computed(() => Boolean(group.value) && (isLeader.value || joinable.value))

const deadlineText = computed(() => {
  if (!group.value?.deadline) return '不限时'
  if (expired.value) return '已截止'
  return remainText(group.value.deadline)
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await groupBuyApi.detail(inviteCode)
  } catch {
    detail.value = null
    loadError.value = '接龙不存在或已关闭'
  } finally {
    loading.value = false
  }
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(group.value?.inviteCode || inviteCode)
    showToast('邀请码已复制')
  } catch {
    showToast(`邀请码：${group.value?.inviteCode || inviteCode}`)
  }
}

function openJoin() {
  if (!store.isLogin) {
    return router.push({ path: '/login', query: { redirect: route.fullPath } })
  }
  if (!joinable.value) return
  joinForm.quantity = 1
  joinForm.contact = ''
  joinPopup.value = true
}

async function submitJoin() {
  if (joining.value) return
  joining.value = true
  try {
    await groupBuyApi.join(inviteCode, {
      quantity: joinForm.quantity,
      contact: joinForm.contact.trim()
    })
    joinPopup.value = false
    showToast('已加入接龙')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    joining.value = false
  }
}

async function closeGroup() {
  try {
    await showConfirmDialog({
      title: '结束接龙',
      message: '结束后同学将无法继续参与，确定结束吗？',
      confirmButtonText: '结束接龙'
    })
  } catch {
    return
  }
  closing.value = true
  try {
    await groupBuyApi.close(inviteCode)
    showToast('接龙已结束')
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    closing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.group-detail-page {
  background: var(--bg);
  padding-bottom: 92px;
}
.loading-wrap {
  padding-top: 14px;
}
.line-lg,
.line,
.line.short {
  height: 16px;
  margin-bottom: 12px;
}
.line-lg {
  height: 70px;
}
.line.short {
  width: 50%;
}
.empty-wrap {
  margin-top: 16px;
  padding: 0;
}
.hero {
  margin-top: 12px;
  border-radius: var(--radius);
  padding: 16px;
  background: linear-gradient(140deg, #245f45, #3d8a66);
  color: #fff;
}
.invite-tag {
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.4);
  color: #fff;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.hero h1 {
  margin: 12px 0 6px;
  font-size: 21px;
  font-weight: 800;
  line-height: 1.35;
}
.hero-sub {
  margin: 0;
  font-size: 12px;
  opacity: 0.86;
}
.hero-foot {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
}
.unit {
  color: #fff;
  font-size: 18px;
  font-weight: 800;
}
.deadline {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  opacity: 0.9;
}
.card-title {
  margin: 0;
  font-size: 15px;
}
.bar {
  margin-top: 12px;
  height: 10px;
  border-radius: 999px;
  background: #eef1f0;
  overflow: hidden;
}
.bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #4fa37e, #2f7d5a);
  transition: width 0.3s;
}
.progress-tip {
  margin: 8px 0 0;
  font-size: 11px;
}
.leader {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--ink-2);
}
.empty-participants {
  padding: 18px 0;
  text-align: center;
  font-size: 12px;
}
.people {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
}
.people li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
}
.people li:last-child {
  border-bottom: 0;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--brand-weak);
  color: var(--brand);
  display: grid;
  place-items: center;
  font-size: 14px;
  font-weight: 700;
  flex: none;
}
.name {
  flex: 1;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.qty {
  font-size: 13px;
  color: var(--ink-2);
}
.note {
  margin: 14px 4px;
  font-size: 11px;
  line-height: 1.6;
}
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  border-top: 1px solid var(--line);
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom, 0px));
  z-index: 20;
}
.action-inner {
  max-width: 1120px;
  margin: 0 auto;
}
.join-panel {
  padding: 16px 0 calc(14px + env(safe-area-inset-bottom, 0px));
}
.panel-head {
  padding: 0 16px 12px;
  font-size: 16px;
}
.sum {
  margin: 12px 20px 0;
  font-size: 12px;
}
.sum strong {
  font-size: 16px;
}
.panel-foot {
  padding: 16px 16px 0;
}
</style>

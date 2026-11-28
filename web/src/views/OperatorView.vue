<template>
  <div class="page operator-page">
    <van-nav-bar title="运营中心" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section class="card promo-card">
        <div class="between">
          <span class="muted">我的推广码</span>
          <span class="tag info">OPERATOR</span>
        </div>
        <div class="promo-code">{{ overview.promoCode || '—' }}</div>
        <div class="promo-actions">
          <van-button size="small" round plain type="primary" @click="copyCode">复制推广码</van-button>
          <van-button size="small" round plain @click="copyLink">复制推广链接</van-button>
        </div>
        <p class="muted tip-line">把推广码或链接发给同学，对方注册后自动成为你的下线。</p>
      </section>

      <section class="grid metrics">
        <div class="metric">
          <span class="muted">下线人数</span>
          <strong>{{ overview.inviteeCount || 0 }}</strong>
        </div>
        <div class="metric">
          <span class="muted">下线成交</span>
          <strong>{{ overview.orderCount || 0 }}</strong>
        </div>
        <div class="metric">
          <span class="muted">下线 GMV</span>
          <strong>{{ money(overview.gmv || 0) }}</strong>
        </div>
        <div class="metric">
          <span class="muted">累计提成</span>
          <strong>{{ money(overview.commissionTotal || 0) }}</strong>
        </div>
      </section>

      <van-tabs v-model:active="tab" shrink @change="loadTab">
        <van-tab title="下线">
          <div class="list">
            <p v-if="!invitees.length" class="muted empty">还没有下线，先推广起来吧。</p>
            <div v-for="u in invitees" :key="u.userId" class="row">
              <div class="row-main">
                <strong>{{ u.nickname }}</strong>
                <span class="muted small">{{ u.phone }} · {{ u.college || '未填写学院' }}</span>
              </div>
              <div class="row-side">
                <span>{{ u.orderCount }} 单</span>
                <span class="muted small">{{ money(u.gmv || 0) }}</span>
              </div>
            </div>
          </div>
        </van-tab>

        <van-tab title="提成明细">
          <div class="list">
            <p v-if="!commissions.length" class="muted empty">暂无提成，下线完成交易后自动计提。</p>
            <div v-for="c in commissions" :key="c.id" class="row">
              <div class="row-main">
                <strong>{{ c.orderNo }}</strong>
                <span class="muted small">成交 {{ money(c.orderAmount) }} · 比例 {{ (Number(c.rate) * 100).toFixed(2) }}%</span>
              </div>
              <div class="row-side">
                <span class="amount">{{ money(c.amount) }}</span>
                <span class="tag" :class="c.status === 'SETTLED' ? 'done' : c.status === 'CANCELLED' ? 'muted' : 'warn'">
                  {{ COMMISSION_STATUS[c.status] || c.status }}
                </span>
              </div>
            </div>
          </div>
        </van-tab>

        <van-tab title="投诉处理">
          <div class="list">
            <p v-if="!complaints.length" class="muted empty">暂无待处理投诉。</p>
            <div v-for="t in complaints" :key="t.id" class="row block">
              <div class="between">
                <strong>{{ t.title || '交易投诉' }}</strong>
                <span class="tag warn">{{ t.status }}</span>
              </div>
              <p class="muted small">{{ t.description }}</p>
              <van-button size="mini" round type="primary" @click="resolveComplaint(t)">标记已协调</van-button>
            </div>
          </div>
        </van-tab>

        <van-tab title="线下认证">
          <div class="list">
            <p v-if="!offlineList.length" class="muted empty">暂无待核实材料。</p>
            <div v-for="v in offlineList" :key="v.id" class="row block">
              <div class="between">
                <strong>{{ VERIFY_TYPE[v.verifyType] || v.verifyType }}</strong>
                <span class="tag warn">{{ v.status }}</span>
              </div>
              <p class="muted small">{{ v.applicantNote || '用户已提交材料' }}</p>
              <div class="offline-actions">
                <van-button size="mini" round plain type="danger" @click="reviewOffline(v, false)">不通过</van-button>
                <van-button size="mini" round type="primary" @click="reviewOffline(v, true)">核验通过</van-button>
              </div>
            </div>
          </div>
        </van-tab>
      </van-tabs>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { operatorApi } from '@/api'
import { money } from '@/utils/format'

const router = useRouter()
const tab = ref(0)
const overview = ref({})
const invitees = ref([])
const commissions = ref([])
const complaints = ref([])
const offlineList = ref([])

const COMMISSION_STATUS = { PENDING: '待结算', SETTLED: '已结算', CANCELLED: '已取消' }
const VERIFY_TYPE = { ID_CARD: '身份证核实', STUDENT: '学生证核实', MERCHANT: '商户资质核实' }

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

async function loadOverview() {
  try {
    overview.value = (await operatorApi.overview()) || {}
  } catch {
    // 请求层已经提示
  }
}

async function loadInvitees() {
  invitees.value = recordsOf(await operatorApi.invitees({ page: 1, size: 50 }))
}

async function loadCommissions() {
  commissions.value = recordsOf(await operatorApi.commissions({ page: 1, size: 50 }))
}

async function loadComplaints() {
  complaints.value = recordsOf(await operatorApi.complaints({ page: 1, size: 50 }))
}

async function loadOffline() {
  offlineList.value = recordsOf(await operatorApi.offlineVerifies({ page: 1, size: 50 }))
}

function loadTab() {
  const loaders = [loadInvitees, loadCommissions, loadComplaints, loadOffline]
  loaders[tab.value]?.()
}

async function copyCode() {
  if (!overview.value.promoCode) return showToast('推广码还未生成')
  try {
    await navigator.clipboard.writeText(overview.value.promoCode)
    showToast('推广码已复制')
  } catch {
    showToast(`推广码：${overview.value.promoCode}`)
  }
}

async function copyLink() {
  const link = `${location.origin}${location.pathname}#/upgrade?promo=${overview.value.promoCode || ''}`
  try {
    await navigator.clipboard.writeText(link)
    showToast('推广链接已复制')
  } catch {
    showToast(link)
  }
}

async function resolveComplaint(t) {
  try {
    await showConfirmDialog({ title: '处理投诉', message: '确认已协调处理并关闭该投诉？' })
  } catch {
    return
  }
  await operatorApi.handleComplaint(t.id, { status: 'RESOLVED', result: '运营已协调处理' })
  showToast('已处理')
  loadComplaints()
}

async function reviewOffline(v, passed) {
  await operatorApi.reviewOffline(v.id, { passed, remark: passed ? '线下核验通过' : '材料不清晰' })
  showToast(passed ? '已通过' : '已驳回')
  loadOffline()
  loadOverview()
}

onMounted(() => {
  loadOverview()
  loadInvitees()
})
</script>

<style scoped>
.operator-page {
  padding-bottom: 40px;
}
.promo-card {
  margin-top: 12px;
  background: var(--brand-weak);
}
.promo-code {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 4px;
  color: var(--brand);
  margin: 8px 0;
}
.promo-actions {
  display: flex;
  gap: 8px;
}
.tip-line {
  margin: 10px 0 0;
  font-size: 12px;
}
.grid.metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin: 12px 0;
}
.metric {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.metric strong {
  font-size: 18px;
  color: var(--ink);
}
.list {
  padding: 4px 0;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}
.row.block {
  display: block;
}
.row-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.row-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  white-space: nowrap;
}
.small {
  font-size: 12px;
}
.empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
}
.offline-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}
.amount {
  color: var(--brand);
  font-weight: 600;
}
</style>

<template>
  <div class="page merchant-page">
    <van-nav-bar title="商户中心" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section v-if="!isMerchantAccount" class="card guide-card">
        <h2 class="card-title">商户是独立账号</h2>
        <p class="muted">
          当前登录的是学生账号，不能在这里开店。请退出后，在登录页切到「商户登录」，
          用手机号验证码登录（首次会自动创建商户账号），再回来填写店铺资料等运营审核。
        </p>
        <van-button block round plain type="primary" class="guide-btn" @click="goMerchantLogin">
          去商户登录
        </van-button>
      </section>

      <section v-if="isMerchantAccount" class="hero" :class="statusTone">
        <div class="hero-icon">
          <van-icon name="shop-o" size="26" />
        </div>
        <div class="hero-main">
          <h1>{{ merchant ? merchant.shopName : '校园商户入驻' }}</h1>
          <p>{{ statusDesc }}</p>
        </div>
        <span v-if="merchant" class="status-tag">{{ statusText }}</span>
      </section>

      <section v-if="isMerchantAccount && merchant && merchant.status === 'APPROVED'" class="stats card">
        <div class="stat">
          <strong>{{ stats?.onSale ?? merchant.listingCount ?? 0 }}</strong>
          <span>在架教材</span>
        </div>
        <div class="stat">
          <strong>{{ stats?.total ?? '--' }}</strong>
          <span>累计发布</span>
        </div>
        <div class="stat">
          <strong>{{ merchant.contact || '--' }}</strong>
          <span>联系方式</span>
        </div>
      </section>

      <!-- 入驻申请 -->
      <section v-if="isMerchantAccount && showApply" class="card">
        <div class="between">
          <h2 class="card-title">{{ merchant ? '重新提交申请' : '提交入驻申请' }}</h2>
          <span class="muted small">审核通过后可批量上架</span>
        </div>
        <p v-if="merchant?.rejectReason" class="reject-tip">
          <van-icon name="warning-o" /> 上次未通过：{{ merchant.rejectReason }}
        </p>
        <van-cell-group inset class="form">
          <van-field v-model="applyForm.shopName" label="店铺名称" maxlength="40" placeholder="例如：梅园旧书店" />
          <van-field v-model="applyForm.contact" label="联系方式" maxlength="40" placeholder="手机号 / 微信号" />
        </van-cell-group>
        <div class="license-wrap">
          <p class="field-label">营业执照 / 校园代理授权</p>
          <DemoUploader v-model="applyForm.licenseUrl" label="上传证照" file-key="merchant-license" />
        </div>
        <van-button
          block
          round
          type="primary"
          class="submit-btn"
          :loading="applying"
          loading-text="提交中"
          @click="submitApply"
        >
          提交申请
        </van-button>
      </section>

      <!-- 批量上架 -->
      <section v-if="isMerchantAccount && isApproved" class="card batch-card">
        <div class="between">
          <h2 class="card-title">批量上架</h2>
          <span class="muted small">单批 ≤ 100 本，整批事务</span>
        </div>

        <div v-for="(row, idx) in drafts" :key="idx" class="draft-row">
          <div class="between draft-head">
            <strong>第 {{ idx + 1 }} 本</strong>
            <button type="button" class="remove" @click="removeDraft(idx)">
              <van-icon name="cross" size="14" />
            </button>
          </div>
          <van-field v-model="row.title" label="书名" maxlength="80" placeholder="必填" />
          <van-field v-model="row.price" type="number" label="售价" placeholder="0.00">
            <template #button><span class="unit">元</span></template>
          </van-field>
          <van-field v-model="row.originalPrice" type="number" label="原价" placeholder="选填" />
          <van-field
            :model-value="conditionText(row.conditionLevel)"
            label="品相"
            readonly
            is-link
            placeholder="请选择"
            @click="openCondition(idx)"
          />
          <van-field v-model="row.textbookId" type="number" label="教材 ID" placeholder="选填" />
          <van-field v-model="row.courseId" type="number" label="课程 ID" placeholder="选填" />
          <van-field v-model="row.dorm" label="取书地点" maxlength="40" placeholder="选填" />
          <van-field v-model="row.imageCount" type="number" label="图片数量" placeholder="0" />
          <van-field
            v-model="row.description"
            type="textarea"
            rows="2"
            autosize
            maxlength="200"
            label="描述"
            placeholder="选填"
          />
          <div class="draft-cover">
            <DemoUploader v-model="row.coverUrl" label="封面" :file-key="`merchant-${idx}`" />
          </div>
        </div>

        <van-button size="small" round plain type="primary" icon="plus" @click="addDraft">添加一本</van-button>

        <div class="batch-foot">
          <span class="muted small">待上架 {{ drafts.length }} 本</span>
          <van-button
            round
            type="primary"
            :loading="batching"
            loading-text="上架中"
            :disabled="!drafts.length"
            @click="submitBatch"
          >
            批量上架
          </van-button>
        </div>
      </section>

      <!-- 批量下架 -->
      <section v-if="isMerchantAccount && isApproved" class="card">
        <div class="between">
          <h2 class="card-title">批量下架</h2>
          <van-button size="mini" round plain :loading="mineLoading" @click="loadMine">刷新</van-button>
        </div>
        <p class="muted small">勾选需要下架的在售教材。</p>
        <van-checkbox-group v-model="offlineIds">
          <label v-for="l in myListings" :key="l.id" class="listing-row">
            <van-checkbox :name="l.id" />
            <div class="listing-main">
              <strong class="clamp-1">{{ l.title || '未命名教材' }}</strong>
              <span class="muted small">{{ money(l.price) }} · {{ l.status }}</span>
            </div>
          </label>
        </van-checkbox-group>
        <EmptyState v-if="!myListings.length" text="还没有发布过教材" icon="📚" />
        <van-button
          block
          round
          plain
          type="danger"
          class="submit-btn"
          :loading="offlining"
          :disabled="!offlineIds.length"
          @click="submitOffline"
        >
          下架选中（{{ offlineIds.length }}）
        </van-button>
      </section>

      <div class="more">
        <van-cell title="单本发布教材" is-link icon="plus" @click="router.push('/publish')" />
        <van-cell title="我的教材" is-link icon="notes-o" @click="router.push('/search?mine=1')" />
      </div>

      <p class="muted foot">商户功能用于批量运营，普通同学可在发布页单本出售。</p>
    </div>

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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listingApi, merchantApi } from '@/api'
import { CONDITION_OPTIONS, conditionText, money } from '@/utils/format'
import DemoUploader from '@/components/DemoUploader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

/** 商户是独立账号：只有 role=MERCHANT 的账号能在这里提交入驻资料 */
const isMerchantAccount = computed(() => store.hasRole('MERCHANT'))

function goMerchantLogin() {
  store.logout()
  router.replace({ path: '/login', query: { loginType: 'MERCHANT' } })
}

const loading = ref(false)
const applying = ref(false)
const batching = ref(false)
const offlining = ref(false)
const mineLoading = ref(false)

const merchant = ref(null)
const stats = ref(null)

const applyForm = reactive({ shopName: '', licenseUrl: '', contact: '' })

const drafts = ref([])
const conditionPopup = ref(false)
const conditionIndex = ref(0)

const myListings = ref([])
const offlineIds = ref([])

const isApproved = computed(() => merchant.value?.status === 'APPROVED')
const showApply = computed(() => !merchant.value || merchant.value.status === 'REJECTED')

const STATUS_TEXT = { PENDING: '审核中', APPROVED: '已入驻', REJECTED: '未通过', FROZEN: '已冻结' }
const statusText = computed(() => STATUS_TEXT[merchant.value?.status] || '未知')
const statusTone = computed(() => {
  const s = merchant.value?.status
  if (s === 'APPROVED') return 'ok'
  if (s === 'REJECTED' || s === 'FROZEN') return 'danger'
  return 'warn'
})
const statusDesc = computed(() => {
  const s = merchant.value?.status
  if (!s) return '提交资料，审核通过后可批量上架教材'
  return (
    {
      PENDING: '资料已提交，等待平台审核',
      APPROVED: '店铺运营中，可批量上架与下架',
      REJECTED: '申请未通过，修改资料后可重新提交',
      FROZEN: '店铺已被冻结，请联系平台处理'
    }[s] || '状态更新中'
  )
})

const recordsOf = (res) => (Array.isArray(res) ? res : res?.records || [])

function newDraft() {
  return {
    title: '',
    price: '',
    originalPrice: '',
    conditionLevel: 'GOOD',
    textbookId: '',
    courseId: '',
    description: '',
    coverUrl: '',
    imageCount: '',
    dorm: ''
  }
}

function addDraft() {
  if (drafts.value.length >= 100) return showToast('单批最多 100 本')
  drafts.value.push(newDraft())
}

function removeDraft(idx) {
  drafts.value.splice(idx, 1)
}

function openCondition(idx) {
  conditionIndex.value = idx
  conditionPopup.value = true
}

function onConditionConfirm({ selectedOptions }) {
  const row = drafts.value[conditionIndex.value]
  if (row) row.conditionLevel = selectedOptions?.[0]?.value || 'GOOD'
  conditionPopup.value = false
}

async function loadMine() {
  mineLoading.value = true
  try {
    const res = await listingApi.mine({ page: 1, size: 50 })
    myListings.value = recordsOf(res)
  } catch {
    myListings.value = []
  } finally {
    mineLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    merchant.value = await merchantApi.my()
    if (merchant.value?.shopName) applyForm.shopName = merchant.value.shopName
    if (merchant.value?.contact) applyForm.contact = merchant.value.contact
    if (merchant.value?.licenseUrl) applyForm.licenseUrl = merchant.value.licenseUrl
    if (merchant.value?.status === 'APPROVED') {
      try {
        stats.value = await merchantApi.stats()
      } catch {
        stats.value = null
      }
    }
  } catch {
    merchant.value = null
  } finally {
    loading.value = false
  }
  if (isApproved.value) loadMine()
}

async function submitApply() {
  if (applying.value) return
  if (!applyForm.shopName.trim()) return showToast('请输入店铺名称')
  if (!applyForm.contact.trim()) return showToast('请输入联系方式')
  if (!applyForm.licenseUrl) return showToast('请上传营业执照或授权证明')
  applying.value = true
  try {
    merchant.value = await merchantApi.apply({
      shopName: applyForm.shopName.trim(),
      licenseUrl: applyForm.licenseUrl,
      contact: applyForm.contact.trim()
    })
    showToast('已提交，等待平台审核')
  } catch {
    // 请求层已经提示
  } finally {
    applying.value = false
  }
}

async function submitBatch() {
  if (batching.value) return
  if (!drafts.value.length) return showToast('请先添加要上架的教材')
  const payload = []
  for (let i = 0; i < drafts.value.length; i += 1) {
    const r = drafts.value[i]
    if (!r.title.trim()) return showToast(`第 ${i + 1} 本缺少书名`)
    const price = Number(r.price)
    if (!(price > 0)) return showToast(`第 ${i + 1} 本售价需大于 0`)
    payload.push({
      title: r.title.trim(),
      price,
      originalPrice: r.originalPrice ? Number(r.originalPrice) : null,
      conditionLevel: r.conditionLevel || 'GOOD',
      textbookId: r.textbookId ? Number(r.textbookId) : null,
      courseId: r.courseId ? Number(r.courseId) : null,
      description: r.description.trim(),
      coverUrl: r.coverUrl,
      imageCount: r.imageCount ? Number(r.imageCount) : r.coverUrl ? 1 : 0,
      dorm: r.dorm.trim()
    })
  }
  batching.value = true
  try {
    const res = await merchantApi.batch(payload)
    showToast(`已上架 ${res?.count ?? payload.length} 本`)
    drafts.value = []
    await load()
  } catch {
    // 请求层已经提示
  } finally {
    batching.value = false
  }
}

async function submitOffline() {
  if (offlining.value || !offlineIds.value.length) return
  offlining.value = true
  try {
    const res = await merchantApi.offline(offlineIds.value)
    showToast(`已下架 ${res?.count ?? offlineIds.value.length} 本`)
    offlineIds.value = []
    await load()
    await loadMine()
  } catch {
    // 请求层已经提示
  } finally {
    offlining.value = false
  }
}

onMounted(() => {
  addDraft()
  load()
})
</script>

<style scoped>
.guide-card {
  margin-top: 12px;
}
.guide-card .card-title {
  margin-bottom: 6px;
}
.guide-btn {
  margin-top: 12px;
}
.merchant-page {
  background: var(--bg);
}
.hero {
  margin-top: 12px;
  padding: 16px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  gap: 12px;
  color: #fff;
  background: linear-gradient(135deg, #e08a2e, #e6a23c);
}
.hero.ok {
  background: linear-gradient(135deg, #245f45, #3d8a66);
}
.hero.danger {
  background: linear-gradient(135deg, #b54a3a, #d2644f);
}
.hero-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: grid;
  place-items: center;
  flex: none;
}
.hero-main {
  flex: 1;
  min-width: 0;
}
.hero-main h1 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 800;
}
.hero-main p {
  margin: 0;
  font-size: 12px;
  opacity: 0.9;
}
.status-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.4);
  flex: none;
}
.stats {
  margin-top: 12px;
  display: flex;
}
.stat {
  flex: 1;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px 0;
}
.stat + .stat {
  border-left: 1px solid var(--line);
}
.stat strong {
  font-size: 17px;
  color: var(--brand);
}
.stat span {
  font-size: 11px;
  color: var(--muted);
}
.card {
  margin-top: 12px;
}
.card-title {
  margin: 0;
  font-size: 15px;
}
.small {
  font-size: 11px;
}
.form {
  margin: 10px 0 0;
  overflow: hidden;
}
.license-wrap {
  margin-top: 12px;
  max-width: 180px;
}
.field-label {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--muted);
}
.submit-btn {
  margin-top: 12px;
}
.reject-tip {
  margin: 10px 0 0;
  padding: 8px 10px;
  background: #fdecea;
  color: #b54a3a;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
}
.batch-card :deep(.van-field) {
  padding-left: 0;
  padding-right: 0;
}
.draft-row {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  margin: 12px 0;
}
.draft-head {
  margin-bottom: 4px;
}
.draft-head strong {
  font-size: 13px;
}
.remove {
  border: 0;
  background: var(--bg);
  color: var(--muted);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  cursor: pointer;
}
.draft-cover {
  max-width: 150px;
  margin-top: 8px;
}
.unit {
  color: var(--muted);
  margin-left: 4px;
}
.batch-foot {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.listing-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--line);
}
.listing-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.listing-main strong {
  font-size: 13px;
}
.more {
  margin-top: 12px;
  overflow: hidden;
  border-radius: var(--radius);
}
.foot {
  margin: 16px 4px;
  text-align: center;
  font-size: 11px;
  line-height: 1.6;
}
</style>

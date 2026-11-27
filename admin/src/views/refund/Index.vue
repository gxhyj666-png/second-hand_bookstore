<template>
  <div>
    <div class="page-title">
      <div>
        <h2>纠纷仲裁</h2>
        <p>处理买家与卖家无法协商一致的退款纠纷，平台判定结果将通知双方</p>
      </div>
      <el-tag type="danger" effect="plain" round>仲裁中 {{ arbitratingCount }} 单</el-tag>
    </div>

    <div class="rule-box">
      <el-icon class="rule-icon"><Warning /></el-icon>
      <div class="rule-text">
        <p>判定「买家胜」：强制退款，钱款原路退回买家，商品恢复在售；</p>
        <p>判定「卖家胜」：订单回到已完成，交易维持原状。</p>
        <span class="muted">仅订单状态为「仲裁中（ARBITRATING）」的退款单可以提交判定，判定说明会同步给买卖双方。</span>
      </div>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="orderNoInput"
          placeholder="输入订单号查询，如 TB20261124183004"
          clearable
          style="width: 320px"
          :prefix-icon="Search"
          @keyup.enter="handleLookup"
        />
        <el-button type="primary" :loading="lookupLoading" @click="handleLookup">查询订单</el-button>
        <el-button @click="orderNoInput = ''">清空</el-button>
        <div class="spacer"></div>
        <span class="muted">查询接口：GET /v1/orders/{orderNo}</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border>
        <el-table-column prop="orderNo" label="订单号" width="185" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="orderStatusType(row.status)">{{ orderStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退款原因" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.refundReason || refundOf(row)?.reason">{{ row.refundReason || refundOf(row)?.reason }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="退款单" width="150" align="center">
          <template #default="{ row }">
            <template v-if="refundOf(row)">
              <el-tag size="small" effect="plain" :type="refundStatusType(refundOf(row).status)">
                {{ refundStatusText(refundOf(row).status) }}
              </el-tag>
            </template>
            <span v-else class="muted">暂无记录</span>
          </template>
        </el-table-column>
        <el-table-column label="买家 / 卖家" width="150">
          <template #default="{ row }">
            <span>{{ row.buyer || `#${row.buyerId}` }} / {{ row.seller || `#${row.sellerId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="danger" :disabled="row.status !== 'ARBITRATING'" @click="openJudge(row)">
              判定
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !rows.length" description="当前筛选条件下没有退款订单" :image-size="72" />

      <div class="pagination-wrap list-footer">
        <span class="muted">共 {{ total }} 条 · 单次最多加载 50 条</span>
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="退款订单详情" size="560px">
      <template v-if="currentOrder">
        <div class="detail-head">
          <div>
            <div class="detail-no">{{ currentOrder.orderNo }}</div>
            <div class="muted">{{ fmt(currentOrder.createdAt) }}</div>
          </div>
          <el-tag :type="orderStatusType(currentOrder.status)" effect="dark">
            {{ orderStatusText(currentOrder.status) }}
          </el-tag>
        </div>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单金额">
            <span class="money">¥{{ Number(currentOrder.amount || 0).toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="退款单 ID">{{ currentRefund?.id ?? '接口未返回' }}</el-descriptions-item>
          <el-descriptions-item label="买家">{{ currentOrder.buyer || `#${currentOrder.buyerId}` }}</el-descriptions-item>
          <el-descriptions-item label="卖家">{{ currentOrder.seller || `#${currentOrder.sellerId}` }}</el-descriptions-item>
          <el-descriptions-item label="交易地点">{{ fmt(currentOrder.meetPlace) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ fmt(currentOrder.completedAt) }}</el-descriptions-item>
          <el-descriptions-item label="退款原因" :span="2">
            {{ currentOrder.refundReason || currentRefund?.reason || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">退款申请</el-divider>
        <template v-if="currentRefund">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="退款单状态">
              <el-tag size="small" :type="refundStatusType(currentRefund.status)">
                {{ refundStatusText(currentRefund.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="退款金额">
              ¥{{ Number(currentRefund.amount || currentOrder.amount || 0).toFixed(2) }}
            </el-descriptions-item>
            <el-descriptions-item label="申请时间" :span="2">{{ fmt(currentRefund.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="平台结论" :span="2">
              {{ verdictText(currentRefund.platformVerdict) }}
            </el-descriptions-item>
            <el-descriptions-item label="平台说明" :span="2">
              {{ currentRefund.platformRemark || '—' }}
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="currentRefund.evidenceUrl" class="evidence">
            <span class="muted">凭证照片：</span>
            <el-image
              :src="currentRefund.evidenceUrl"
              :preview-src-list="[currentRefund.evidenceUrl]"
              fit="cover"
              class="evidence-img"
              preview-teleported
            />
          </div>
        </template>
        <el-empty v-else description="订单接口未返回退款记录" :image-size="60" />

        <div class="drawer-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>
            当前后端暂未提供「按订单查询退款记录」的接口，若页面提示“接口未返回”，请在下方的判定弹窗中手动填写退款单 ID。
          </span>
        </div>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="danger"
          :disabled="currentOrder?.status !== 'ARBITRATING'"
          @click="openJudge(currentOrder)"
        >
          发起判定
        </el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="judgeVisible" title="平台判定" width="520px" @closed="resetJudge">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="判定后不可撤销：买家胜将强制退款并恢复商品在售，卖家胜则订单回到已完成。"
        class="judge-alert"
      />
      <el-form ref="judgeFormRef" :model="judgeForm" :rules="judgeRules" label-width="96px">
        <el-form-item label="订单号">
          <span>{{ judgeOrder?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="退款单 ID" prop="refundId">
          <el-input v-model="judgeForm.refundId" placeholder="后端未返回时请手动填写退款申请 ID" />
        </el-form-item>
        <el-form-item label="判定结果" prop="buyerWin">
          <el-radio-group v-model="judgeForm.buyerWin">
            <el-radio :value="true">买家胜 · 强制退款</el-radio>
            <el-radio :value="false">卖家胜 · 维持完成</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="判定说明" prop="note">
          <el-input
            v-model="judgeForm.note"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请填写判定依据，将同步给买卖双方"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleJudge">提交判定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getOrderDetail, getOrders, judgeRefund } from '@/api/modules'

const statusOptions = [
  { label: '仲裁中（可判定）', value: 'ARBITRATING' },
  { label: '退款中', value: 'REFUNDING' },
  { label: '同意退款', value: 'REFUND_AGREED' },
  { label: '已退款', value: 'REFUNDED' }
]

const orderStatusMap = {
  PENDING_PAY: { text: '待支付', type: 'warning' },
  WAIT_VERIFY: { text: '待核销', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  REFUNDING: { text: '退款中', type: 'warning' },
  REFUND_AGREED: { text: '同意退款', type: 'warning' },
  REFUNDED: { text: '已退款', type: 'danger' },
  ARBITRATING: { text: '仲裁中', type: 'danger' },
  CLOSED: { text: '已关闭', type: 'info' }
}

const refundStatusMap = {
  PENDING: { text: '待卖家处理', type: 'warning' },
  AGREED: { text: '卖家已同意', type: 'primary' },
  REJECTED: { text: '卖家已拒绝', type: 'danger' },
  ARBITRATING: { text: '平台仲裁中', type: 'danger' },
  ARBITRATED: { text: '已判定', type: 'success' }
}

const verdictMap = {
  SELLER_FAULT: '卖家责任 · 支持买家',
  PLATFORM_OK: '货物无问题 · 支持卖家',
  REFUNDED: '已退款'
}

const loading = ref(false)
const lookupLoading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const status = ref('ARBITRATING')
const orderNoInput = ref('')

const detailVisible = ref(false)
const currentOrder = ref(null)

const judgeVisible = ref(false)
const judgeOrder = ref(null)
const judgeFormRef = ref(null)
const judgeForm = reactive({ refundId: '', buyerWin: true, note: '' })
const judgeRules = {
  refundId: [
    { required: true, message: '请填写退款单 ID', trigger: 'blur' },
    { pattern: /^\d+$/, message: '退款单 ID 必须是数字', trigger: 'blur' }
  ],
  note: [
    { required: true, message: '请填写判定说明', trigger: 'blur' },
    { min: 4, message: '判定说明至少 4 个字', trigger: 'blur' }
  ]
}

const arbitratingCount = computed(() => rows.value.filter((row) => row.status === 'ARBITRATING').length)
const currentRefund = computed(() => refundOf(currentOrder.value))

function refundOf(order) {
  if (!order) return null
  if (order.refund) return order.refund
  if (order.refundId || order.refundStatus) {
    return {
      id: order.refundId ?? null,
      status: order.refundStatus || '',
      reason: order.refundReason || '',
      amount: order.amount,
      evidenceUrl: order.refundEvidenceUrl || '',
      platformVerdict: order.platformVerdict || '',
      platformRemark: '',
      createdAt: order.refundCreatedAt || ''
    }
  }
  return null
}

function fmt(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ')
}

function orderStatusText(value) {
  return (orderStatusMap[value] && orderStatusMap[value].text) || value || '—'
}

function orderStatusType(value) {
  return (orderStatusMap[value] && orderStatusMap[value].type) || 'info'
}

function refundStatusText(value) {
  return (refundStatusMap[value] && refundStatusMap[value].text) || value || '—'
}

function refundStatusType(value) {
  return (refundStatusMap[value] && refundStatusMap[value].type) || 'info'
}

function verdictText(value) {
  return verdictMap[value] || value || '暂未判定'
}

async function load() {
  loading.value = true
  try {
    const res = await getOrders({ status: status.value, page: 1, size: 50 })
    const list = Array.isArray(res) ? res : res?.records || []
    rows.value = list
    total.value = typeof res?.total === 'number' ? res.total : list.length
  } catch (e) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function lookupByNo(orderNo) {
  const no = String(orderNo || '').trim()
  if (!no) {
    ElMessage.warning('请输入订单号')
    return
  }
  lookupLoading.value = true
  try {
    const order = await getOrderDetail(no)
    if (!order || !order.orderNo) {
      ElMessage.error('未查询到该订单')
      return
    }
    currentOrder.value = order
    detailVisible.value = true
  } finally {
    lookupLoading.value = false
  }
}

function handleLookup() {
  lookupByNo(orderNoInput.value)
}

function openDetail(row) {
  currentOrder.value = row
  detailVisible.value = true
}

function openJudge(row) {
  if (!row) return
  judgeOrder.value = row
  judgeForm.refundId = refundOf(row)?.id ? String(refundOf(row).id) : ''
  judgeForm.buyerWin = true
  judgeForm.note = ''
  judgeVisible.value = true
}

function resetJudge() {
  judgeFormRef.value?.clearValidate()
  judgeForm.refundId = ''
  judgeForm.note = ''
  judgeForm.buyerWin = true
  judgeOrder.value = null
}

async function handleJudge() {
  if (!judgeFormRef.value) return
  const valid = await judgeFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await judgeRefund(Number(judgeForm.refundId), {
      buyerWin: judgeForm.buyerWin,
      note: judgeForm.note
    })
    ElMessage.success(
      judgeForm.buyerWin ? '已判定买家胜，退款将原路退回，商品恢复在售' : '已判定卖家胜，订单回到已完成'
    )
    judgeVisible.value = false
    detailVisible.value = false
    await load()
    if (orderNoInput.value.trim()) {
      await lookupByNo(orderNoInput.value)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.rule-box {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 18px;
  margin-bottom: 14px;
  border: 1px solid #fde3c8;
  background: #fff8ef;
  border-radius: 10px;
}

.rule-icon {
  color: #e08a3c;
  font-size: 18px;
  margin-top: 2px;
}

.rule-text p {
  margin: 0 0 4px;
  font-size: 13px;
  color: #7a4a12;
  line-height: 1.7;
}

.rule-text .muted {
  display: inline-block;
  margin-top: 2px;
}

.list-footer {
  justify-content: flex-start;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 8px;
  background: #f7f9fd;
  margin-bottom: 18px;
}

.detail-no {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.evidence {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
}

.evidence-img {
  width: 96px;
  height: 72px;
  border-radius: 6px;
  border: 1px solid var(--border-soft);
}

.drawer-tip {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f4f6fb;
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.7;
}

.judge-alert {
  margin-bottom: 16px;
}
</style>

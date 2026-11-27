<template>
  <div>
    <div class="page-title">
      <div>
        <h2>订单管理</h2>
        <p>查看平台全部交易订单，点击任意一行可查看订单详情、面交信息与状态流转</p>
      </div>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 190px" @change="handleSearch">
          <el-option label="全部状态" value="" />
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <span class="muted">共 {{ total }} 笔订单 · 点击行查看详情</span>
      </div>

      <el-table
        :data="rows"
        v-loading="loading"
        row-key="id"
        border
        :row-class-name="rowClassName"
        @row-click="openDetail"
      >
        <el-table-column prop="orderNo" label="订单号" width="185" />
        <el-table-column prop="title" label="教材" min-width="200" show-overflow-tooltip />
        <el-table-column label="买家" width="110">
          <template #default="{ row }">{{ row.buyer || `#${row.buyerId}` }}</template>
        </el-table-column>
        <el-table-column label="卖家" width="110">
          <template #default="{ row }">{{ row.seller || `#${row.sellerId}` }}</template>
        </el-table-column>
        <el-table-column label="金额" width="105" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面交点" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.meetPlace || row.point || '—' }}</template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" title="订单详情" size="540px">
      <template v-if="currentOrder">
        <div class="detail-head">
          <div>
            <div class="detail-no">{{ currentOrder.orderNo }}</div>
            <div class="muted">{{ fmt(currentOrder.createdAt) }}</div>
          </div>
          <el-tag :type="statusType(currentOrder.status)" effect="dark">{{ statusText(currentOrder.status) }}</el-tag>
        </div>

        <div class="detail-list">
          <span class="k">教材</span><span>{{ currentOrder.title || '—' }}</span>
          <span class="k">买家</span><span>{{ currentOrder.buyer || `#${currentOrder.buyerId}` }}</span>
          <span class="k">卖家</span><span>{{ currentOrder.seller || `#${currentOrder.sellerId}` }}</span>
          <span class="k">订单金额</span><span class="money">¥{{ Number(currentOrder.amount || 0).toFixed(2) }}</span>
          <span class="k">面交地点</span><span>{{ currentOrder.meetPlace || currentOrder.point || '—' }}</span>
          <span class="k">面交时间</span><span>{{ fmt(currentOrder.meetTime) }}</span>
          <span class="k">完成时间</span><span>{{ fmt(currentOrder.completedAt) }}</span>
          <span class="k">签收照片</span>
          <span>
            <el-image
              v-if="currentOrder.signPhotoUrl"
              :src="currentOrder.signPhotoUrl"
              :preview-src-list="[currentOrder.signPhotoUrl]"
              fit="cover"
              class="sign-img"
              preview-teleported
            />
            <span v-else class="muted">未上传</span>
          </span>
          <span class="k">支付方式</span><span>微信支付（Mock）</span>
          <template v-if="currentOrder.refundReason">
            <span class="k">退款原因</span><span class="refund-reason">{{ currentOrder.refundReason }}</span>
          </template>
        </div>

        <el-divider content-position="left">状态流转</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in currentOrder.timeline || []"
            :key="index"
            :timestamp="fmt(item.time)"
            :type="timelineType(item.status)"
            placement="top"
          >
            <div class="timeline-title">{{ item.text }}</div>
          </el-timeline-item>
        </el-timeline>

        <el-empty v-if="!currentOrder.timeline || !currentOrder.timeline.length" description="暂无状态记录" :image-size="60" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getOrders } from '@/api/modules'

const statusOptions = [
  { label: '待支付', value: 'PENDING_PAY' },
  { label: '待核销', value: 'WAIT_VERIFY' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '退款中', value: 'REFUNDING' },
  { label: '同意退款', value: 'REFUND_AGREED' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '仲裁中', value: 'ARBITRATING' },
  { label: '已关闭', value: 'CLOSED' }
]

const statusMap = {
  PENDING_PAY: { text: '待支付', type: 'warning' },
  WAIT_VERIFY: { text: '待核销', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  REFUNDING: { text: '退款中', type: 'warning' },
  REFUND_AGREED: { text: '同意退款', type: 'primary' },
  REFUNDED: { text: '已退款', type: 'danger' },
  ARBITRATING: { text: '仲裁中', type: 'danger' },
  CLOSED: { text: '已关闭', type: 'info' }
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')

const drawerVisible = ref(false)
const currentOrder = ref(null)

function fmt(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ')
}

function statusText(value) {
  return (statusMap[value] && statusMap[value].text) || value || '—'
}

function statusType(value) {
  return (statusMap[value] && statusMap[value].type) || 'info'
}

function timelineType(value) {
  if (value === 'COMPLETED' || value === 'REFUND_AGREED') return 'success'
  if (value === 'REFUNDED' || value === 'ARBITRATING' || value === 'CLOSED') return 'danger'
  return 'primary'
}

function rowClassName() {
  return 'clickable-row'
}

async function load() {
  loading.value = true
  try {
    const res = await getOrders({ status: status.value || undefined, page: page.value, size: size.value })
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function handleReset() {
  status.value = ''
  page.value = 1
  load()
}

function handleSizeChange() {
  page.value = 1
  load()
}

function openDetail(row) {
  currentOrder.value = row
  drawerVisible.value = true
}

onMounted(load)
</script>

<style scoped>
:deep(.clickable-row) {
  cursor: pointer;
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

.sign-img {
  width: 120px;
  height: 80px;
  border-radius: 6px;
  border: 1px solid var(--border-soft);
  vertical-align: middle;
}

.refund-reason {
  color: #c2410c;
}

.timeline-title {
  font-size: 14px;
}
</style>

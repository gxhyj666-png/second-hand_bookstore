<template>
  <div>
    <div class="page-title">
      <div>
        <h2>订单管理</h2>
        <p>查看平台全部交易订单，点击任意一行可查看订单详情与状态流转</p>
      </div>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 180px" @change="handleSearch">
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
        <el-table-column prop="title" label="教材" min-width="210" show-overflow-tooltip />
        <el-table-column prop="buyer" label="买家" width="120" />
        <el-table-column prop="seller" label="卖家" width="120" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ Number(row.amount).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="point" label="自提点" width="170" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="下单时间" width="170" />
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

    <el-drawer v-model="drawerVisible" title="订单详情" size="520px">
      <template v-if="currentOrder">
        <div class="detail-head">
          <div>
            <div class="detail-no">{{ currentOrder.orderNo }}</div>
            <div class="muted">{{ currentOrder.createdAt }}</div>
          </div>
          <el-tag :type="statusType(currentOrder.status)" effect="dark">{{ statusText(currentOrder.status) }}</el-tag>
        </div>

        <div class="detail-list">
          <span class="k">教材</span><span>{{ currentOrder.title }}</span>
          <span class="k">买家</span><span>{{ currentOrder.buyer }}</span>
          <span class="k">卖家</span><span>{{ currentOrder.seller }}</span>
          <span class="k">订单金额</span><span class="money">¥{{ Number(currentOrder.amount).toFixed(2) }}</span>
          <span class="k">自提点</span><span>{{ currentOrder.point }}</span>
          <span class="k">支付方式</span><span>微信支付（Mock）</span>
        </div>

        <el-divider content-position="left">状态流转</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in currentOrder.timeline || []"
            :key="index"
            :timestamp="item.time"
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
  { label: '已支付冻结', value: 'FROZEN' },
  { label: '待核销', value: 'WAIT_VERIFY' },
  { label: '已核销', value: 'VERIFIED' },
  { label: '已结算', value: 'SETTLED' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '已关闭', value: 'CLOSED' }
]

const statusMap = {
  PENDING_PAY: { text: '待支付', type: 'warning' },
  FROZEN: { text: '已支付冻结', type: 'primary' },
  WAIT_VERIFY: { text: '待核销', type: 'warning' },
  VERIFIED: { text: '已核销', type: 'success' },
  SETTLED: { text: '已结算', type: 'success' },
  REFUNDED: { text: '已退款', type: 'danger' },
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

function statusText(value) {
  return (statusMap[value] && statusMap[value].text) || value
}

function statusType(value) {
  return (statusMap[value] && statusMap[value].type) || 'info'
}

function timelineType(value) {
  if (value === 'SETTLED' || value === 'VERIFIED') return 'success'
  if (value === 'REFUNDED' || value === 'CLOSED') return 'danger'
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

.timeline-title {
  font-size: 14px;
}
</style>

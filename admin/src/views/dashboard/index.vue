<template>
  <div>
    <div class="page-title">
      <div>
        <h2>数据看板</h2>
        <p>平台累计指标 · 近 7 日趋势与交易漏斗（数据来自后端 /v1/admin/dashboard 实时聚合）</p>
      </div>
      <el-tag type="success" effect="plain" round>实时数据</el-tag>
    </div>

    <div class="todo-strip">
      <el-tag type="warning" effect="plain" round>待审核商品 {{ data.pendingListings }}</el-tag>
      <el-tag type="danger" effect="plain" round>处理中工单 {{ data.pendingTickets }}</el-tag>
      <el-tag type="info" effect="plain" round>进行中接龙 {{ data.openGroups }}</el-tag>
      <span class="todo-hint">趋势与漏斗按近 7 日聚合，品相分布为当前在库快照</span>
    </div>

    <div class="stat-grid" v-loading="loading">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <div>
          <div class="label">{{ card.label }}</div>
          <div class="value">
            <small v-if="card.prefix">{{ card.prefix }}</small>{{ card.value }}
          </div>
        </div>
        <div class="icon-wrap" :style="{ background: card.bg, color: card.color }">
          <el-icon :size="22"><component :is="card.icon" /></el-icon>
        </div>
      </div>
    </div>

    <div class="chart-grid" v-loading="loading">
      <div class="chart-box">
        <h3>近 7 日 GMV 与订单数</h3>
        <div class="chart-tip">左轴 GMV（元），右轴订单数（单）</div>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="chart-box">
        <h3>交易漏斗（近 7 日埋点）</h3>
        <div class="chart-tip">浏览 → 详情 → 发布 → 下单 → 支付 → 核销</div>
        <div ref="funnelRef" class="chart"></div>
      </div>
    </div>

    <div class="chart-box cond-box" v-loading="loading">
      <h3>在库商品品相分布</h3>
      <div class="cond-list">
        <span v-for="c in data.conditionDist" :key="c.name" class="cond-item">
          {{ CONDITION_TEXT[c.name] || c.name }} <strong>{{ c.value }}</strong>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { getDashboard } from '@/api/modules'

const loading = ref(false)
const trendRef = ref(null)
const funnelRef = ref(null)

const data = reactive({
  userCount: 0,
  onSaleCount: 0,
  orderCount: 0,
  gmv: 0,
  pendingTickets: 0,
  openGroups: 0,
  pendingListings: 0,
  series: [],
  funnel: [],
  conditionDist: []
})

const CONDITION_TEXT = { NEW: '全新', LIKE_NEW: '几乎全新', GOOD: '良好', FAIR: '一般', POOR: '较差' }
const FUNNEL_TEXT = { VIEW_LISTING: '浏览', DETAIL: '详情', PUBLISH: '发布', CREATE_ORDER: '下单', PAY: '支付', VERIFY: '核销' }
const FUNNEL_COLORS = ['#3d5afe', '#5b7cff', '#7b93ff', '#0ea5a4', '#38b6b4', '#f0a04b']

let trendChart = null
let funnelChart = null

const statCards = computed(() => [
  {
    label: '累计 GMV',
    value: Number(data.gmv || 0).toFixed(2),
    prefix: '¥',
    icon: 'Money',
    color: '#3d5afe',
    bg: '#eef2ff'
  },
  { label: '订单总数', value: data.orderCount || 0, icon: 'Tickets', color: '#e08a3c', bg: '#fdf3e7' },
  { label: '在售商品', value: data.onSaleCount || 0, icon: 'Goods', color: '#0ea5a4', bg: '#e6f7f6' },
  { label: '注册用户', value: data.userCount || 0, icon: 'UserFilled', color: '#a05ac0', bg: '#f7ecfb' }
])

function renderTrend() {
  if (!trendRef.value) return
  trendChart = echarts.init(trendRef.value)
  const dates = data.series.map((item) => item.date)
  const gmv = data.series.map((item) => Number(item.gmv || 0))
  const order = data.series.map((item) => Number(item.orders || 0))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { right: 4, top: 0, itemWidth: 14, textStyle: { color: '#6b7a90' } },
    grid: { left: 6, right: 6, bottom: 0, top: 44, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dfe6f0' } },
      axisLabel: { color: '#6b7a90' }
    },
    yAxis: [
      {
        type: 'value',
        name: 'GMV（元）',
        nameTextStyle: { color: '#9aa6b8', padding: [0, 0, 0, 10] },
        splitLine: { lineStyle: { color: '#eef2f8' } },
        axisLabel: { color: '#6b7a90' }
      },
      {
        type: 'value',
        name: '订单数',
        nameTextStyle: { color: '#9aa6b8', padding: [0, 10, 0, 0] },
        splitLine: { show: false },
        axisLabel: { color: '#6b7a90' }
      }
    ],
    series: [
      {
        name: 'GMV（元）',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: gmv,
        itemStyle: { color: '#3d5afe' },
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(61, 90, 254, 0.30)' },
            { offset: 1, color: 'rgba(61, 90, 254, 0.02)' }
          ])
        }
      },
      {
        name: '订单数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 6,
        data: order,
        itemStyle: { color: '#0ea5a4' },
        lineStyle: { width: 2, type: 'dashed' }
      }
    ]
  })
}

function renderFunnel() {
  if (!funnelRef.value) return
  funnelChart = echarts.init(funnelRef.value)
  const rows = (data.funnel || []).map((row, i) => ({
    name: FUNNEL_TEXT[row.stage] || row.stage,
    value: Number(row.count || 0),
    itemStyle: { color: FUNNEL_COLORS[i % FUNNEL_COLORS.length] }
  }))
  funnelChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.name}：${params.value} 次`
    },
    series: [
      {
        type: 'funnel',
        left: '10%',
        right: '10%',
        top: 16,
        bottom: 12,
        minSize: '28%',
        maxSize: '100%',
        sort: 'descending',
        gap: 3,
        label: {
          show: true,
          position: 'inside',
          color: '#fff',
          fontSize: 12,
          formatter: (params) => `${params.name} ${params.value}`
        },
        itemStyle: { borderColor: '#fff', borderWidth: 1 },
        data: rows
      }
    ]
  })
}

function resizeCharts() {
  if (trendChart) trendChart.resize()
  if (funnelChart) funnelChart.resize()
}

onMounted(async () => {
  loading.value = true
  try {
    const res = (await getDashboard()) || {}
    for (const key of ['userCount', 'onSaleCount', 'orderCount', 'pendingTickets', 'openGroups', 'pendingListings']) {
      data[key] = Number(res[key] || 0)
    }
    data.gmv = Number(res.gmv || 0)
    data.series = res.series || []
    data.funnel = res.funnel || []
    data.conditionDist = res.conditionDist || []
  } catch {
    // 请求层已弹提示；看板保持空态，图表仍渲染“暂无数据”的骨架
  } finally {
    loading.value = false
  }
  renderTrend()
  renderFunnel()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  if (trendChart) trendChart.dispose()
  if (funnelChart) funnelChart.dispose()
})
</script>

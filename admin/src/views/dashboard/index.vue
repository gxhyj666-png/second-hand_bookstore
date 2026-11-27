<template>
  <div>
    <div class="page-title">
      <div>
        <h2>数据看板</h2>
        <p>平台核心交易指标与转化漏斗（数据口径：当日 00:00 至现在）</p>
      </div>
      <el-tag type="success" effect="plain" round>实时 Mock 数据</el-tag>
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
        <h3>交易漏斗</h3>
        <div class="chart-tip">上架 → 浏览 → 下单 → 支付 → 核销</div>
        <div ref="funnelRef" class="chart"></div>
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
  today: { gmv: 0, orderCount: 0, verifyCount: 0, newUserCount: 0 },
  trend: [],
  funnel: { publish: 0, view: 0, order: 0, pay: 0, verify: 0 }
})

let trendChart = null
let funnelChart = null

const statCards = computed(() => [
  {
    label: '今日 GMV',
    value: Number(data.today.gmv || 0).toFixed(2),
    prefix: '¥',
    icon: 'Money',
    color: '#3d5afe',
    bg: '#eef2ff'
  },
  { label: '今日订单数', value: data.today.orderCount || 0, icon: 'Tickets', color: '#e08a3c', bg: '#fdf3e7' },
  { label: '今日核销数', value: data.today.verifyCount || 0, icon: 'CircleCheck', color: '#0ea5a4', bg: '#e6f7f6' },
  { label: '今日新增用户', value: data.today.newUserCount || 0, icon: 'UserFilled', color: '#a05ac0', bg: '#f7ecfb' }
])

function renderTrend() {
  if (!trendRef.value) return
  trendChart = echarts.init(trendRef.value)
  const dates = data.trend.map((item) => item.date.slice(5))
  const gmv = data.trend.map((item) => item.gmv)
  const order = data.trend.map((item) => item.orderCount)
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
  const f = data.funnel
  funnelChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.name}：${params.value}`
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
        data: [
          { name: '上架', value: f.publish, itemStyle: { color: '#3d5afe' } },
          { name: '浏览', value: f.view, itemStyle: { color: '#5b7cff' } },
          { name: '下单', value: f.order, itemStyle: { color: '#7b93ff' } },
          { name: '支付', value: f.pay, itemStyle: { color: '#0ea5a4' } },
          { name: '核销', value: f.verify, itemStyle: { color: '#f0a04b' } }
        ]
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
    const res = await getDashboard()
    data.today = res.today || data.today
    data.trend = res.trend || []
    data.funnel = res.funnel || data.funnel
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

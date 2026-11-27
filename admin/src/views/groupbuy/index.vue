<template>
  <div>
    <div class="page-title">
      <div>
        <h2>班级接龙</h2>
        <p>查看各班委发起的教材团购接龙，掌握成团进度与截止时间</p>
      </div>
      <el-tag type="success" effect="plain" round>进行中 {{ openCount }} 个</el-tag>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-button type="primary" :icon="Refresh" @click="load">刷新</el-button>
        <div class="spacer"></div>
        <span class="muted">共 {{ total }} 个接龙 · 邀请码供同学加入使用</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border>
        <el-table-column prop="title" label="接龙标题" min-width="250" show-overflow-tooltip />
        <el-table-column prop="leader" label="团长" width="120" />
        <el-table-column label="目标册数" width="100" align="center">
          <template #default="{ row }">{{ row.targetCount }} 册</template>
        </el-table-column>
        <el-table-column label="已登记" width="190">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="percent(row)"
                :stroke-width="8"
                :status="percent(row) >= 100 ? 'success' : undefined"
                :show-text="false"
                style="flex: 1"
              />
              <span class="progress-text">{{ row.currentCount }} / {{ row.targetCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="预期单价" width="110" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ Number(row.unitPrice).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止时间" width="170" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="邀请码" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info" class="invite-code">{{ row.inviteCode }}</el-tag>
          </template>
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getGroupBuys } from '@/api/modules'

const statusMap = {
  OPEN: { text: '进行中', type: 'primary' },
  CLOSED: { text: '已截止', type: 'warning' },
  DONE: { text: '已成团', type: 'success' },
  CANCELED: { text: '已取消', type: 'info' }
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const openCount = computed(() => rows.value.filter((item) => item.status === 'OPEN').length)

function statusText(value) {
  return (statusMap[value] && statusMap[value].text) || value
}

function statusType(value) {
  return (statusMap[value] && statusMap[value].type) || 'info'
}

function percent(row) {
  if (!row.targetCount) return 0
  return Math.min(100, Math.round((row.currentCount / row.targetCount) * 100))
}

async function load() {
  loading.value = true
  try {
    const res = await getGroupBuys({ page: page.value, size: size.value })
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.progress-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-text {
  font-size: 12px;
  color: #6b7a90;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.invite-code {
  letter-spacing: 1px;
  font-family: 'SFMono-Regular', Consolas, monospace;
}
</style>

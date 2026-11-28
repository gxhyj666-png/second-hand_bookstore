<template>
  <div>
    <div class="page-title">
      <div>
        <h2>运营人员</h2>
        <p>由个体用户使用运营进阶码升级而来；可查看推广码、下线人数与提成累计</p>
      </div>
      <el-tag type="success" effect="plain" round>共 {{ total }} 位运营</el-tag>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="load">刷新</el-button>
        <div class="spacer"></div>
        <span class="muted">下线订单核销完成后按成交额 1% 计提提成（app.promoter-rate 可调）</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="userId" border>
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <div class="user-cell">
              <strong>{{ row.nickname }}</strong>
              <span class="muted">#{{ row.userId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="推广码" width="130">
          <template #default="{ row }">
            <span class="mono">{{ row.promoCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inviteeCount" label="下线人数" width="110" align="center" />
        <el-table-column label="累计提成" width="130" align="right">
          <template #default="{ row }">¥{{ Number(row.commissionTotal || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="creditScore" label="信誉分" width="100" align="center" />
        <el-table-column prop="createdAt" label="注册时间" width="180" />
      </el-table>

      <el-empty v-if="!loading && !rows.length" description="暂无运营人员，可先在进阶码管理生成运营码" :image-size="72" />

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getOperators } from '@/api/modules'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

async function load() {
  loading.value = true
  try {
    const res = await getOperators({ page: page.value, size: size.value })
    rows.value = res?.records || []
    total.value = res?.total || 0
  } catch {
    // 请求层已经提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.user-cell .muted {
  font-size: 12px;
}
.mono {
  font-family: var(--font-mono, monospace);
  letter-spacing: 1px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>

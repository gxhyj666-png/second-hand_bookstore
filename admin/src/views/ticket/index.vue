<template>
  <div>
    <div class="page-title">
      <div>
        <h2>纠纷工单</h2>
        <p>处理买家与卖家之间的退款、盗版、品相纠纷，处理结果将通知双方</p>
      </div>
      <el-tag type="warning" effect="plain" round>待处理 {{ pendingCount }} 条</el-tag>
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
        <span class="muted">共 {{ total }} 条工单</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border>
        <el-table-column prop="ticketNo" label="工单号" width="170" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="typeTagType(row.type)">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="关联订单" width="180" />
        <el-table-column prop="title" label="问题标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="ticketStatusType(row.status)">{{ ticketStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="initiator" label="发起人" width="120" />
        <el-table-column prop="createdAt" label="发起时间" width="170" />
        <el-table-column label="处理结果" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.result">{{ row.result }}</span>
            <span v-else class="muted">待处理</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="row.status === 'RESOLVED' || row.status === 'REJECTED'"
              @click="openHandle(row)"
            >
              处理
            </el-button>
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

    <el-dialog v-model="handleVisible" title="处理工单" width="520px" @closed="resetForm">
      <template v-if="currentRow">
        <el-descriptions :column="1" border size="small" class="ticket-desc">
          <el-descriptions-item label="工单号">{{ currentRow.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="关联订单">{{ currentRow.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="问题标题">{{ currentRow.title }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ currentRow.initiator }} · {{ currentRow.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="ticket-form">
          <el-form-item label="处理结论" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio value="RESOLVED">判定成立 · 已解决</el-radio>
              <el-radio value="REJECTED">判定不成立 · 驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="处理说明" prop="result">
            <el-input
              v-model="form.result"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              placeholder="请填写处理说明，将同步给买卖双方"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交处理结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTickets, resolveTicket } from '@/api/modules'

const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已驳回', value: 'REJECTED' }
]

const typeMap = {
  REFUND: '退款纠纷',
  FAKE_BOOK: '盗版/假书',
  CONDITION: '品相问题',
  OTHER: '其他问题'
}

const statusMap = {
  PENDING: { text: '待处理', type: 'warning' },
  PROCESSING: { text: '处理中', type: 'primary' },
  RESOLVED: { text: '已解决', type: 'success' },
  REJECTED: { text: '已驳回', type: 'danger' }
}

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')

const handleVisible = ref(false)
const currentRow = ref(null)
const formRef = ref(null)
const form = reactive({ status: 'RESOLVED', result: '' })
const rules = {
  status: [{ required: true, message: '请选择处理结论', trigger: 'change' }],
  result: [{ required: true, message: '请填写处理说明', trigger: 'blur' }, { min: 4, message: '处理说明至少 4 个字', trigger: 'blur' }]
}

const pendingCount = computed(() => rows.value.filter((item) => item.status === 'PENDING').length)

function typeText(value) {
  return typeMap[value] || value
}

function typeTagType(value) {
  if (value === 'FAKE_BOOK') return 'danger'
  if (value === 'CONDITION') return 'warning'
  return 'info'
}

function ticketStatusText(value) {
  return (statusMap[value] && statusMap[value].text) || value
}

function ticketStatusType(value) {
  return (statusMap[value] && statusMap[value].type) || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await getTickets({ status: status.value || undefined, page: page.value, size: size.value })
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

function openHandle(row) {
  currentRow.value = row
  form.status = 'RESOLVED'
  form.result = ''
  handleVisible.value = true
}

function resetForm() {
  formRef.value?.clearValidate()
  form.result = ''
  form.status = 'RESOLVED'
  currentRow.value = null
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await resolveTicket(currentRow.value.id, { status: form.status, result: form.result })
    ElMessage.success('工单已处理')
    handleVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ticket-desc {
  margin-bottom: 18px;
}

.ticket-form {
  margin-top: 4px;
}
</style>

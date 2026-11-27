<template>
  <div>
    <div class="page-title">
      <div>
        <h2>商品审核</h2>
        <p>审核学生发布的二手教材，通过后进入在售列表，驳回需填写理由</p>
      </div>
      <el-tag type="warning" effect="plain" round>待审核 {{ pendingCount }} 条</el-tag>
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
        <span class="muted">共 {{ total }} 条商品</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border>
        <el-table-column label="封面" width="76" align="center">
          <template #default="{ row }">
            <el-image :src="row.coverUrl" fit="cover" class="cover-thumb" :preview-src-list="[row.coverUrl]" preview-teleported>
              <template #error>
                <div class="cover-thumb cover-fallback">书</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="230" show-overflow-tooltip />
        <el-table-column prop="seller" label="卖家" width="130" />
        <el-table-column label="售价" width="110" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ Number(row.price).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="品相" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ conditionText(row.condition) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="上架时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="success"
              link
              :disabled="row.status !== 'PENDING'"
              @click="handleApprove(row)"
            >
              通过
            </el-button>
            <el-button
              type="danger"
              link
              :disabled="row.status !== 'PENDING'"
              @click="openReject(row)"
            >
              驳回
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

    <el-dialog v-model="rejectVisible" title="驳回商品" width="480px" @closed="resetReject">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="80px">
        <el-form-item label="商品">
          <span>{{ currentRow?.title }}</span>
        </el-form-item>
        <el-form-item label="驳回理由" prop="reason">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请说明驳回原因，如：图片与实物不符 / 疑似盗版 / 信息填写错误"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveListing, getListings, rejectListing } from '@/api/modules'

const statusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '在售', value: 'ON_SALE' },
  { label: '交易中', value: 'LOCKED' },
  { label: '已售出', value: 'SOLD' },
  { label: '已下架', value: 'OFFLINE' },
  { label: '已驳回', value: 'REJECTED' }
]

const statusTextMap = {
  PENDING: '待审核',
  ON_SALE: '在售',
  LOCKED: '交易中',
  SOLD: '已售出',
  OFFLINE: '已下架',
  REJECTED: '已驳回'
}

const conditionMap = {
  NEW: '全新',
  LIKE_NEW: '九成新',
  GOOD: '八成新',
  FAIR: '有笔记',
  POOR: '品相一般'
}

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')

const rejectVisible = ref(false)
const rejectFormRef = ref(null)
const currentRow = ref(null)
const rejectForm = reactive({ reason: '' })
const rejectRules = {
  reason: [
    { required: true, message: '请填写驳回理由', trigger: 'blur' },
    { min: 4, message: '驳回理由至少 4 个字', trigger: 'blur' }
  ]
}

const pendingCount = computed(() => rows.value.filter((item) => item.status === 'PENDING').length)

function statusText(value) {
  return statusTextMap[value] || value
}

function statusType(value) {
  if (value === 'PENDING') return 'warning'
  if (value === 'ON_SALE') return 'success'
  if (value === 'REJECTED') return 'danger'
  return 'info'
}

function conditionText(value) {
  return conditionMap[value] || value
}

async function load() {
  loading.value = true
  try {
    const res = await getListings({ status: status.value || undefined, page: page.value, size: size.value })
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

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(`确认通过《${row.title}》的审核并上架？`, '审核通过', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'success'
    })
  } catch (e) {
    return
  }
  await approveListing(row.id)
  ElMessage.success('已通过审核，商品进入在售状态')
  load()
}

function openReject(row) {
  currentRow.value = row
  rejectForm.reason = ''
  rejectVisible.value = true
}

function resetReject() {
  rejectFormRef.value?.clearValidate()
  rejectForm.reason = ''
  currentRow.value = null
}

async function handleReject() {
  if (!rejectFormRef.value) return
  const valid = await rejectFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await rejectListing(currentRow.value.id, rejectForm.reason)
    ElMessage.success('已驳回该商品')
    rejectVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  color: #3d5afe;
  font-size: 16px;
  border-radius: 4px;
}
</style>

<template>
  <div>
    <div class="page-title">
      <div>
        <h2>商户审核</h2>
        <p>审核商户入驻申请，通过后账号升级为商户、可批量上下架教材，驳回需填写理由</p>
      </div>
      <el-tag type="warning" effect="plain" round>待审核 {{ pendingCount }} 家</el-tag>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 180px" @change="load">
          <el-option label="全部状态" value="" />
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <span class="muted">共 {{ total }} 家商户 · 该接口返回全量数组，不分页</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border>
        <el-table-column label="资质图" width="104" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.licenseUrl"
              :src="row.licenseUrl"
              fit="cover"
              class="license-thumb"
              :preview-src-list="[row.licenseUrl]"
              preview-teleported
            >
              <template #error>
                <div class="license-thumb license-fallback">无图</div>
              </template>
            </el-image>
            <div v-else class="license-thumb license-fallback">未上传</div>
          </template>
        </el-table-column>
        <el-table-column prop="shopName" label="店铺名" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请人" width="110" align="center">
          <template #default="{ row }">#{{ row.userId }}</template>
        </el-table-column>
        <el-table-column prop="contact" label="联系电话" width="150" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.rejectReason" class="reject-reason">{{ row.rejectReason }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
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

      <el-empty v-if="!loading && !rows.length" description="暂无商户申请" :image-size="72" />
    </el-card>

    <el-dialog v-model="rejectVisible" title="驳回商户申请" width="480px" @closed="resetReject">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="88px">
        <el-form-item label="店铺">
          <span>{{ currentRow?.shopName }}</span>
        </el-form-item>
        <el-form-item label="驳回理由" prop="reason">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请说明驳回原因，如：营业执照模糊 / 店铺名称不规范 / 联系方式无效"
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
import { getMerchants, reviewMerchant } from '@/api/modules'

const statusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已冻结', value: 'FROZEN' }
]

const statusMap = {
  PENDING: { text: '待审核', type: 'warning' },
  APPROVED: { text: '已通过', type: 'success' },
  REJECTED: { text: '已驳回', type: 'danger' },
  FROZEN: { text: '已冻结', type: 'info' }
}

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const status = ref('PENDING')

const rejectVisible = ref(false)
const rejectFormRef = ref(null)
const currentRow = ref(null)
const rejectForm = reactive({ reason: '' })
const rejectRules = {
  reason: [
    { required: true, message: '请填写驳回理由', trigger: 'blur' },
    { min: 2, message: '驳回理由至少 2 个字', trigger: 'blur' }
  ]
}

const pendingCount = computed(() => rows.value.filter((item) => item.status === 'PENDING').length)

function statusText(value) {
  return (statusMap[value] && statusMap[value].text) || value || '—'
}

function statusType(value) {
  return (statusMap[value] && statusMap[value].type) || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await getMerchants({ status: status.value || undefined })
    // 后端 /v1/admin/merchants 返回数组；兼容将来改为分页对象的可能
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

function handleReset() {
  status.value = 'PENDING'
  load()
}

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(`确认通过「${row.shopName}」的入驻申请？通过后该账号将升级为商户。`, '审核通过', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'success'
    })
  } catch (e) {
    return
  }
  await reviewMerchant(row.id, { approved: true, reason: '' })
  ElMessage.success('已通过商户审核')
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
    await reviewMerchant(currentRow.value.id, { approved: false, reason: rejectForm.reason })
    ElMessage.success('已驳回该商户申请')
    rejectVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.license-thumb {
  width: 76px;
  height: 54px;
  border-radius: 5px;
  display: block;
  margin: 0 auto;
}

.license-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  color: #3d5afe;
  font-size: 12px;
  border: 1px dashed #c7d2fe;
}

.reject-reason {
  color: #c2410c;
  font-size: 13px;
}
</style>

<template>
  <div>
    <div class="page-title">
      <div>
        <h2>进阶码管理</h2>
        <p>进阶码只用于升级<strong>运营身份</strong>；学生与商户账号都能用它进阶，商户身份与功能保留</p>
      </div>
      <el-tag type="warning" effect="plain" round>本页未使用 {{ unusedCount }} 个</el-tag>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-select v-model="query.targetRole" placeholder="全部类型" clearable style="width: 160px" @change="load">
          <el-option label="全部类型" value="" />
          <el-option label="运营码" value="OPERATOR" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <el-option label="全部状态" value="" />
          <el-option label="未使用" value="UNUSED" />
          <el-option label="已使用" value="USED" />
          <el-option label="已作废" value="REVOKED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button type="primary" plain @click="dialogVisible = true">批量生成</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border>
        <el-table-column label="进阶码" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.code }}</span>
            <el-button link type="primary" size="small" @click="copy(row.code)">复制</el-button>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.targetRole === 'OPERATOR' ? 'success' : 'warning'">
              {{ row.targetRole === 'OPERATOR' ? '运营' : row.targetRole }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="170" />
        <el-table-column label="使用者" width="100" align="center">
          <template #default="{ row }">{{ row.usedBy ? `#${row.usedBy}` : '—' }}</template>
        </el-table-column>
        <el-table-column prop="expireAt" label="有效期至" width="170">
          <template #default="{ row }">{{ row.expireAt || '永久' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="row.status !== 'UNUSED'" @click="revoke(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="批量生成进阶码" width="440px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="进阶类型">
          <el-radio-group v-model="form.targetRole">
            <el-radio value="OPERATOR">运营码（商户身份请走「商户登录 + 入驻审核」）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生成数量">
          <el-input-number v-model="form.count" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="有效天数">
          <el-input-number v-model="form.expireDays" :min="0" :max="365" />
          <span class="muted hint">0 表示永久有效</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="generate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { generateUpgradeCodes, getUpgradeCodes, revokeUpgradeCode } from '@/api/modules'

const loading = ref(false)
const generating = ref(false)
const rows = ref([])
const total = ref(0)
const dialogVisible = ref(false)

const query = reactive({ targetRole: '', status: '', page: 1, size: 10 })
const form = reactive({ targetRole: 'OPERATOR', count: 5, expireDays: 30 })

const unusedCount = computed(() => rows.value.filter((r) => r.status === 'UNUSED').length)

const statusText = (s) => ({ UNUSED: '未使用', USED: '已使用', REVOKED: '已作废' }[s] || s)
const statusType = (s) => ({ UNUSED: 'success', USED: 'info', REVOKED: 'danger' }[s] || 'info')

async function load() {
  loading.value = true
  try {
    const res = await getUpgradeCodes({ ...query })
    rows.value = res?.records || []
    total.value = res?.total || 0
  } catch {
    // 请求层已经提示
  } finally {
    loading.value = false
  }
}

async function generate() {
  generating.value = true
  try {
    const codes = await generateUpgradeCodes({ ...form })
    dialogVisible.value = false
    ElMessage.success(`已生成 ${codes?.length || 0} 个运营进阶码`)
    load()
  } catch {
    // 请求层已经提示
  } finally {
    generating.value = false
  }
}

async function revoke(row) {
  try {
    await revokeUpgradeCode(row.id)
    ElMessage.success('已作废')
    load()
  } catch {
    // 请求层已经提示
  }
}

async function copy(code) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success(`已复制 ${code}`)
  } catch {
    ElMessage.info(code)
  }
}

onMounted(load)
</script>

<style scoped>
.mono {
  font-family: var(--font-mono, monospace);
  letter-spacing: 1px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.hint {
  margin-left: 8px;
  font-size: 12px;
}
</style>

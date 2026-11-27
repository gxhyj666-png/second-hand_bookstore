<template>
  <div>
    <div class="page-title">
      <div>
        <h2>用户管理</h2>
        <p>平台学生、团长、店员与管理员账号，可对异常账号执行冻结</p>
      </div>
      <el-tag type="info" effect="plain" round>共 {{ total }} 个账号</el-tag>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="昵称 / 学号 / 学院"
          clearable
          style="width: 280px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <span class="muted">冻结后该用户将无法发布商品与下单</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border>
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="studentNo" label="学号" width="130" />
        <el-table-column prop="college" label="学院" min-width="150" show-overflow-tooltip />
        <el-table-column label="信用分" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.creditScore >= 100 ? 'success' : row.creditScore >= 80 ? 'warning' : 'danger'" effect="plain">
              {{ row.creditScore }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="roleType(row.role)">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="认证状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="verifyType(row.verifyState)" effect="plain">{{ verifyText(row.verifyState) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">
              {{ row.status === 'NORMAL' ? '正常' : '已冻结' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'NORMAL'"
              type="danger"
              link
              :disabled="row.role === 'ADMIN'"
              @click="handleToggle(row)"
            >
              冻结
            </el-button>
            <el-button v-else type="success" link @click="handleToggle(row)">解冻</el-button>
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
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { freezeUser, getUsers } from '@/api/modules'

const roleMap = {
  ADMIN: { text: '管理员', type: 'danger' },
  LEADER: { text: '团长', type: 'warning' },
  CLERK: { text: '店员', type: 'success' },
  STUDENT: { text: '学生', type: 'info' }
}

const verifyMap = {
  UNVERIFIED: { text: '未认证', type: 'info' },
  PENDING: { text: '待审核', type: 'warning' },
  VERIFIED: { text: '已认证', type: 'success' },
  REJECTED: { text: '已驳回', type: 'danger' }
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

function roleText(value) {
  return (roleMap[value] && roleMap[value].text) || value
}

function roleType(value) {
  return (roleMap[value] && roleMap[value].type) || 'info'
}

function verifyText(value) {
  return (verifyMap[value] && verifyMap[value].text) || value
}

function verifyType(value) {
  return (verifyMap[value] && verifyMap[value].type) || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await getUsers({ keyword: keyword.value, page: page.value, size: size.value })
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
  keyword.value = ''
  page.value = 1
  load()
}

function handleSizeChange() {
  page.value = 1
  load()
}

async function handleToggle(row) {
  const frozen = row.status === 'NORMAL'
  const action = frozen ? '冻结' : '解冻'
  try {
    await ElMessageBox.confirm(`确认${action}用户「${row.nickname}」吗？`, `${action}用户`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: frozen ? 'warning' : 'info'
    })
  } catch (e) {
    return
  }
  await freezeUser(row.id, frozen)
  ElMessage.success(`已${action}用户「${row.nickname}」`)
  load()
}

onMounted(load)
</script>

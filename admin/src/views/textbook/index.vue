<template>
  <div>
    <div class="page-title">
      <div>
        <h2>教材库</h2>
        <p>维护平台教材主数据，展开行可查看教材关联的课程</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增教材</el-button>
    </div>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="书名 / ISBN / 出版社 / 作者"
          clearable
          style="width: 300px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="spacer"></div>
        <span class="muted">共 {{ total }} 本教材</span>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" border @expand-change="handleExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-panel">
              <div class="expand-title">关联课程 · {{ row.title }}</div>
              <el-table v-if="coursesMap[row.id] && coursesMap[row.id].length" :data="coursesMap[row.id]" size="small" border>
                <el-table-column prop="name" label="课程名" min-width="220" />
                <el-table-column prop="teacher" label="授课教师" width="130" />
                <el-table-column label="绑定类型" width="120">
                  <template #default="{ row: course }">
                    <el-tag size="small" :type="course.bindType === 'REQUIRED' ? 'danger' : 'info'">
                      {{ course.bindType === 'REQUIRED' ? '必修' : '参考' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="semester" label="学期" width="150" />
              </el-table>
              <el-empty v-else description="暂未关联课程" :image-size="56" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="封面" width="72" align="center">
          <template #default="{ row }">
            <el-image :src="row.coverUrl" fit="cover" class="cover-thumb" :preview-src-list="[row.coverUrl]" preview-teleported>
              <template #error>
                <div class="cover-thumb cover-fallback">书</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="230" show-overflow-tooltip />
        <el-table-column prop="isbn13" label="ISBN" width="140" />
        <el-table-column prop="publisher" label="出版社" min-width="170" show-overflow-tooltip />
        <el-table-column prop="edition" label="版次" width="100" />
        <el-table-column label="定价" width="100" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ Number(row.price).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
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

    <el-dialog v-model="dialogVisible" title="新增教材" width="600px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="书名" prop="title">
          <el-input v-model="form.title" placeholder="如 高等数学（第七版）上册" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="ISBN" prop="isbn13">
          <el-input v-model="form.isbn13" placeholder="13 位 ISBN，如 9787040396638" maxlength="13" />
        </el-form-item>
        <el-form-item label="作者" prop="author">
          <el-input v-model="form.author" placeholder="如 同济大学数学系" />
        </el-form-item>
        <el-form-item label="出版社" prop="publisher">
          <el-input v-model="form.publisher" placeholder="如 高等教育出版社" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="版次" prop="edition">
              <el-input v-model="form.edition" placeholder="如 第七版" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出版年" prop="publishYear">
              <el-input-number v-model="form.publishYear" :min="1900" :max="2100" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="定价" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
                <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createTextbook, getTextbookCourses, getTextbooks } from '@/api/modules'

const categories = ['公共课', '专业基础', '专业核心', '通识']

const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const coursesMap = reactive({})
const dialogVisible = ref(false)
const formRef = ref(null)

const form = reactive({
  title: '',
  isbn13: '',
  author: '',
  publisher: '',
  edition: '',
  publishYear: 2024,
  price: 0,
  category: '公共课',
  status: 'ENABLED'
})

const rules = {
  title: [{ required: true, message: '请输入书名', trigger: 'blur' }],
  isbn13: [
    { required: true, message: '请输入 ISBN', trigger: 'blur' },
    { pattern: /^\d{13}$/, message: 'ISBN 应为 13 位数字', trigger: 'blur' }
  ],
  price: [{ required: true, message: '请输入定价', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    const res = await getTextbooks({ keyword: keyword.value, page: page.value, size: size.value })
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

async function handleExpand(row, expandedRows) {
  const expanded = expandedRows.some((item) => item.id === row.id)
  if (!expanded || coursesMap[row.id]) return
  try {
    coursesMap[row.id] = await getTextbookCourses(row.id)
  } catch (e) {
    coursesMap[row.id] = []
  }
}

function openCreate() {
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, {
    title: '',
    isbn13: '',
    author: '',
    publisher: '',
    edition: '',
    publishYear: 2024,
    price: 0,
    category: '公共课',
    status: 'ENABLED'
  })
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createTextbook({ ...form })
    ElMessage.success('教材已新增（Mock 演示，仅保存在内存中）')
    dialogVisible.value = false
    page.value = 1
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.expand-panel {
  padding: 10px 18px 14px;
}

.expand-title {
  font-size: 13px;
  color: #6b7a90;
  margin-bottom: 10px;
}

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

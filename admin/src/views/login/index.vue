<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <div class="badge">书</div>
        <h1>同窗书 · 管理后台</h1>
        <p>校园教材循环平台 · 运营管理端</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
        <el-form-item prop="account">
          <el-input v-model="form.account" placeholder="管理员账号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="登录密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleSubmit">
          登 录
        </el-button>
      </el-form>

      <div class="login-tip">
        演示账号：<b>admin</b> / <b>admin123</b><br />
        当前开发环境已启用 Mock 数据，无需启动后端即可浏览全部界面。
      </div>
      <div class="login-footer">© 2026 同窗书 · 毕业设计演示项目</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ account: 'admin', password: 'admin123' })

const rules = {
  account: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功，欢迎回来')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (e) {
    // 错误提示已由 request.js 统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-btn {
  width: 100%;
  letter-spacing: 4px;
  margin-top: 4px;
}
</style>

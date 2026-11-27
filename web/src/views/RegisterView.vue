<template>
  <div class="page register-page">
    <van-nav-bar title="注册账号" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <div class="intro">
        <h1>加入教材循环</h1>
        <p>用校园里的闲置教材，换一杯咖啡的钱。</p>
      </div>

      <van-cell-group inset class="form-group">
        <van-field
          v-model="form.phone"
          label="手机号"
          type="tel"
          maxlength="11"
          placeholder="11 位手机号，登录时使用"
          clearable
        />
        <van-field
          v-model="form.nickname"
          label="昵称"
          maxlength="16"
          placeholder="同学们看到的名字"
          clearable
        />
        <van-field
          v-model="form.password"
          label="密码"
          :type="showPwd ? 'text' : 'password'"
          maxlength="20"
          placeholder="至少 6 位"
        >
          <template #right-icon>
            <van-icon :name="showPwd ? 'eye-o' : 'closed-eye'" class="eye" @click="showPwd = !showPwd" />
          </template>
        </van-field>
        <van-field
          v-model="form.confirm"
          label="确认密码"
          :type="showPwd ? 'text' : 'password'"
          maxlength="20"
          placeholder="再输入一次密码"
          @keyup.enter="submit"
        />
      </van-cell-group>

      <p class="hint muted">注册后会自动登录，你可以先去「我的 - 学号认证」完成学生认证。</p>

      <div class="submit-wrap">
        <van-button block round type="primary" :loading="loading" loading-text="注册中" @click="submit">
          注册并登录
        </van-button>
        <p class="to-login">
          已有账号？
          <span class="link" @click="router.replace('/login')">去登录</span>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const form = reactive({
  phone: '',
  nickname: '',
  password: '',
  confirm: ''
})
const showPwd = ref(false)
const loading = ref(false)

async function submit() {
  if (loading.value) return
  const phone = form.phone.trim()
  const nickname = form.nickname.trim()

  if (!/^1\d{10}$/.test(phone)) return showToast('请输入 11 位手机号')
  if (!nickname) return showToast('请填写昵称')
  if (nickname.length > 16) return showToast('昵称最多 16 个字符')
  if (form.password.length < 6) return showToast('密码至少 6 位')
  if (form.password !== form.confirm) return showToast('两次输入的密码不一致')

  loading.value = true
  try {
    await store.register({ phone, password: form.password, nickname })
    // register 只写入 token，这里补一次用户信息，避免“我的”页空白
    try {
      await store.fetchMe()
    } catch {
      // 用户信息拉取失败不阻塞注册结果
    }
    showToast('注册成功，欢迎加入')
    router.replace('/home')
  } catch {
    // 请求层已经提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  background: var(--bg);
}
.intro {
  padding: 20px 4px 12px;
}
.intro h1 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 800;
}
.intro p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.form-group {
  overflow: hidden;
}
.eye {
  color: var(--muted);
  padding: 6px;
}
.hint {
  margin: 10px 20px 0;
  font-size: 11px;
  line-height: 1.6;
}
.submit-wrap {
  padding: 22px 0 28px;
}
.to-login {
  margin: 12px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}
.link {
  color: var(--brand);
  font-weight: 600;
  cursor: pointer;
}
</style>

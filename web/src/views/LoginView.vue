<template>
  <div class="login-page">
    <div class="hero">
      <div class="brand-mark">书</div>
      <h1>教材循环</h1>
      <p>同校交易 · 自提点当面验书 · 担保支付</p>
    </div>

    <div class="sheet">
      <h2>欢迎回来</h2>
      <p class="muted sub">登录后即可发布教材、接龙拼单和查看订单。</p>

      <van-tabs v-model:active="mode" color="var(--brand)" title-active-color="var(--brand)" class="login-tabs">
        <van-tab title="短信验证码" name="sms" />
        <van-tab title="账号密码" name="password" />
      </van-tabs>

      <!-- 短信验证码登录 -->
      <template v-if="mode === 'sms'">
        <van-cell-group inset class="form-group">
          <van-field
            v-model="phone"
            label="手机号"
            placeholder="请输入 11 位手机号"
            type="tel"
            maxlength="11"
            clearable
            left-icon="phone-o"
          />
          <van-field
            v-model="code"
            label="验证码"
            placeholder="请输入验证码"
            type="digit"
            maxlength="6"
            left-icon="shield-o"
            @keyup.enter="submit"
          >
            <template #button>
              <van-button
                size="small"
                round
                plain
                type="primary"
                :loading="sending"
                :disabled="countdown > 0"
                @click="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </van-button>
            </template>
          </van-field>
        </van-cell-group>

        <p class="sms-tip muted">
          <van-icon name="info-o" />
          开发模式下验证码会自动回填，正式环境通过短信下发。
        </p>
      </template>

      <!-- 账号密码登录 -->
      <template v-else>
        <van-cell-group inset class="form-group">
          <van-field
            v-model="account"
            label="账号"
            placeholder="请输入手机号"
            type="tel"
            maxlength="11"
            clearable
            left-icon="user-o"
          />
          <van-field
            v-model="password"
            label="密码"
            placeholder="请输入密码"
            :type="showPwd ? 'text' : 'password'"
            left-icon="lock"
            clearable
            @keyup.enter="submit"
          >
            <template #right-icon>
              <van-icon :name="showPwd ? 'eye-o' : 'closed-eye'" class="eye" @click="showPwd = !showPwd" />
            </template>
          </van-field>
        </van-cell-group>
      </template>

      <div class="submit-wrap">
        <van-button block round type="primary" :loading="loading" loading-text="登录中" @click="submit">
          登录
        </van-button>
        <p class="to-register">
          还没有账号？
          <span class="link" @click="goRegister">去注册</span>
        </p>
      </div>

      <section class="demo">
        <p class="muted">演示账号（点击自动填入）</p>
        <div class="demo-row">
          <button type="button" class="demo-item" @click="fillDemo('buyer')">
            <strong>买家</strong>
            <span>13800000002</span>
          </button>
          <button type="button" class="demo-item" @click="fillDemo('seller')">
            <strong>卖家</strong>
            <span>13800000003</span>
          </button>
        </div>
        <p class="demo-tip">
          开发模式点“获取验证码”会直接回填验证码；账号密码登录的演示密码为 123456。
        </p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const mode = ref('sms')
const phone = ref('')
const code = ref('')
const account = ref('')
const password = ref('')
const showPwd = ref(false)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer = null

const DEMO = {
  buyer: '13800000002',
  seller: '13800000003'
}

function fillDemo(role) {
  phone.value = DEMO[role]
  account.value = DEMO[role]
  password.value = '123456'
}

function goRegister() {
  router.push({ path: '/register', query: route.query })
}

function redirectTarget() {
  const redirect = route.query.redirect
  // 只接受站内绝对路径，避免被 query 带到外站
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/home'
}

function startCountdown(seconds = 60) {
  stopCountdown()
  countdown.value = Math.max(1, Number(seconds) || 60)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) stopCountdown()
  }, 1000)
}

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  countdown.value = 0
}

async function sendCode() {
  if (sending.value || countdown.value > 0) return
  const p = phone.value.trim()
  if (!/^1\d{10}$/.test(p)) return showToast('请输入正确的 11 位手机号')
  sending.value = true
  try {
    const res = await store.sendSmsCode(p)
    if (res?.devCode) {
      code.value = String(res.devCode)
      showToast('开发模式验证码已回填')
    } else {
      showToast('验证码已发送，请查收短信')
    }
    startCountdown(res?.ttlSeconds || 60)
  } catch {
    // 请求层已经提示
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (loading.value) return

  if (mode.value === 'sms') {
    const p = phone.value.trim()
    if (!/^1\d{10}$/.test(p)) return showToast('请输入正确的 11 位手机号')
    if (!/^\d{4,6}$/.test(code.value.trim())) return showToast('请输入验证码')
    loading.value = true
    try {
      await store.smsLogin(p, code.value.trim())
      showToast('登录成功')
      stopCountdown()
      router.replace(redirectTarget())
    } catch {
      // 请求层已经提示，保留输入方便重试
    } finally {
      loading.value = false
    }
    return
  }

  const acc = account.value.trim()
  if (!acc) return showToast('请输入手机号')
  if (!password.value) return showToast('请输入密码')
  loading.value = true
  try {
    await store.login(acc, password.value)
    showToast('登录成功')
    router.replace(redirectTarget())
  } catch {
    // 请求层已经提示，保留输入方便重试
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(stopCountdown)
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: var(--bg);
}
.hero {
  background: linear-gradient(150deg, #245f45 0%, #2f7d5a 55%, #4fa37e 100%);
  color: #fff;
  padding: 56px 24px 68px;
  text-align: center;
}
.brand-mark {
  width: 54px;
  height: 54px;
  margin: 0 auto 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.35);
  display: grid;
  place-items: center;
  font-size: 26px;
  font-weight: 800;
}
.hero h1 {
  margin: 0 0 6px;
  font-size: 26px;
  letter-spacing: 2px;
  font-weight: 800;
}
.hero p {
  margin: 0;
  font-size: 12px;
  opacity: 0.85;
}
.sheet {
  margin: -34px 16px 24px;
  background: #fff;
  border-radius: 18px;
  padding: 22px 8px 24px;
  box-shadow: 0 12px 30px rgba(31, 74, 56, 0.12);
}
.sheet h2 {
  margin: 0 8px 4px;
  font-size: 20px;
  font-weight: 800;
}
.sub {
  margin: 0 8px 10px;
  font-size: 12px;
}
.login-tabs {
  margin: 0 8px 8px;
}
.login-tabs :deep(.van-tabs__nav) {
  background: transparent;
  padding-left: 0;
  padding-right: 0;
}
.login-tabs :deep(.van-tab) {
  flex: none;
  padding: 0 14px 0 0;
  font-size: 14px;
}
.form-group {
  margin: 0;
}
.eye {
  color: var(--muted);
  padding: 6px;
}
.sms-tip {
  margin: 8px 20px 0;
  font-size: 11px;
  line-height: 1.5;
  display: flex;
  gap: 4px;
  align-items: flex-start;
}
.submit-wrap {
  padding: 18px 16px 0;
}
.to-register {
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
.demo {
  margin: 22px 16px 0;
  padding-top: 16px;
  border-top: 1px dashed var(--line);
}
.demo p {
  margin: 0 0 8px;
  font-size: 11px;
}
.demo-row {
  display: flex;
  gap: 8px;
}
.demo-item {
  flex: 1;
  border: 1px solid var(--line);
  background: var(--bg);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
  cursor: pointer;
}
.demo-item strong {
  font-size: 12px;
  color: var(--brand);
}
.demo-item span {
  font-size: 11px;
  color: var(--muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.demo-tip {
  margin: 8px 0 0;
  font-size: 10px;
  line-height: 1.5;
}
</style>

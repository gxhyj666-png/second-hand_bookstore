<template>
  <div class="page upgrade-page">
    <van-nav-bar title="进阶运营" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section class="card hero">
        <h1>输入进阶码</h1>
        <p class="muted">
          进阶码由平台（管理员 / 运营）下发，当前只用于升级<strong>运营身份</strong>：
          可以生成推广码、查看下线业绩、处理投诉与线下核验。
          <br />
          商户身份不靠进阶码——用「商户登录」注册并提交入驻资料，运营审核通过后即可开店；
          商户进阶运营后，店铺与商户工作台全部保留，只是多了一个运营身份。
        </p>
      </section>

      <section class="card">
        <van-field v-model="form.code" label="进阶码" maxlength="16" placeholder="例如 OPER2026" />
        <van-button block round type="primary" class="submit" :loading="submitting" @click="submit">
          立即进阶为运营
        </van-button>
        <p class="muted tip">一个进阶码只能使用一次，进阶后运营入口立即生效。</p>
      </section>

      <section class="card">
        <h2 class="card-title">演示进阶码</h2>
        <p class="muted">
          种子数据内置 <strong>OPER2026</strong> 与 <strong>OPER8888</strong>（运营），可直接体验；
          学生账号与商户账号都可以用它进阶。
        </p>
      </section>

      <section class="card">
        <h2 class="card-title">想开店卖书？</h2>
        <p class="muted">
          退出登录 → 登录页切到「商户登录」→ 用手机号验证码登录（首次会自动创建商户账号）→ 填写店铺资料等审核。
        </p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { upgradeApi } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()
const submitting = ref(false)
const form = reactive({ code: '' })

async function submit() {
  if (submitting.value) return
  if (!form.code.trim()) return showToast('请填写进阶码')
  submitting.value = true
  try {
    const res = await upgradeApi.redeem({ code: form.code.trim().toUpperCase() })
    if (res?.token) store.setToken(res.token)
    if (Array.isArray(res?.roles)) store.roles = res.roles
    store.setUser(res?.user || (await store.fetchMe()))
    await store.fetchMe()
    showToast('已进阶为运营，商户 / 学生功能全部保留')
    router.replace('/operator')
  } catch {
    // 请求层已经提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.upgrade-page {
  padding-bottom: 40px;
}
.hero {
  margin-top: 12px;
}
.hero h1 {
  font-size: 18px;
  margin: 0 0 6px;
  color: var(--ink);
}
.card-title {
  font-size: 14px;
  margin: 0 0 8px;
  color: var(--ink);
}
.submit {
  margin-top: 14px;
}
.tip {
  margin: 10px 0 0;
  font-size: 12px;
}
</style>

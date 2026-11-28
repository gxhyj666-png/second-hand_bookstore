<template>
  <div class="page upgrade-page">
    <van-nav-bar title="进阶商户 / 运营" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section class="card hero">
        <h1>输入进阶码</h1>
        <p class="muted">
          进阶码由平台下发：商户码可批量上下架教材，运营码可推广、查看下线业绩并处理投诉与线下认证。
        </p>
      </section>

      <section class="card">
        <van-field v-model="form.code" label="进阶码" maxlength="16" placeholder="例如 SHOP2026 / OPER2026" />
        <van-field v-model="form.shopName" label="店铺名称" maxlength="40" placeholder="商户进阶时填写（可选）" />
        <van-field v-model="form.contact" label="联系方式" maxlength="40" placeholder="商户进阶时填写（可选）" />
        <van-button block round type="primary" class="submit" :loading="submitting" @click="submit">
          立即进阶
        </van-button>
        <p class="muted tip">一个进阶码只能使用一次，进阶后角色立即生效。</p>
      </section>

      <section class="card">
        <h2 class="card-title">演示进阶码</h2>
        <p class="muted">种子数据内置 <strong>SHOP2026</strong>（商户）与 <strong>OPER2026</strong>（运营），可直接体验。</p>
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
const form = reactive({ code: '', shopName: '', contact: '' })

async function submit() {
  if (submitting.value) return
  if (!form.code.trim()) return showToast('请填写进阶码')
  submitting.value = true
  try {
    const res = await upgradeApi.redeem({
      code: form.code.trim().toUpperCase(),
      shopName: form.shopName.trim(),
      contact: form.contact.trim()
    })
    if (res?.token) store.setToken(res.token)
    store.setUser(res?.user || (await store.fetchMe()))
    showToast(res?.role === 'OPERATOR' ? '已进阶为运营' : '已进阶为商户')
    router.replace(res?.role === 'OPERATOR' ? '/operator' : '/merchant')
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

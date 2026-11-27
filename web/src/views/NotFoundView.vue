<template>
  <div class="page not-found">
    <div class="box">
      <div class="code">404</div>
      <h1>这一页好像被借走了</h1>
      <p class="muted">链接可能已失效，或者页面搬家了。回首页继续淘书吧。</p>
      <div class="actions">
        <van-button round type="primary" @click="router.replace('/home')">回到首页</van-button>
        <van-button round plain @click="router.back()">返回上一页</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: var(--bg);
  padding: 24px;
}
.box {
  max-width: 360px;
  text-align: center;
}
.code {
  font-size: 84px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 2px;
  color: var(--brand);
  opacity: 0.16;
}
h1 {
  margin: 8px 0;
  font-size: 20px;
}
p {
  font-size: 13px;
  line-height: 1.7;
}
.actions {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>

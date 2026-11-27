<template>
  <div class="page verify-page">
    <van-nav-bar title="学号认证" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section class="state-card" :class="stateTone">
        <van-icon :name="stateIcon" size="26" />
        <div>
          <h1>{{ stateTitle }}</h1>
          <p>{{ stateDesc }}</p>
        </div>
      </section>

      <van-cell-group v-if="state !== 'VERIFIED'" inset class="form-group">
        <van-field
          v-model="form.studentNo"
          label="学号"
          maxlength="24"
          placeholder="请输入教务系统学号"
          clearable
        />
        <van-field
          v-model="form.realName"
          label="真实姓名"
          maxlength="20"
          placeholder="与学籍一致的姓名"
          clearable
          @keyup.enter="submit"
        />
      </van-cell-group>

      <p v-if="state !== 'VERIFIED'" class="muted tip">
        认证信息仅用于校内身份核验，管理员审核通过后会展示「已认证」标识，不会公开你的学号。
      </p>

      <div v-if="state !== 'VERIFIED'" class="submit-wrap">
        <van-button block round type="primary" :loading="loading" loading-text="提交中" @click="submit">
          {{ state === 'PENDING' ? '重新提交认证' : '提交认证' }}
        </van-button>
      </div>

      <section v-else class="card done-card">
        <van-icon name="passed" size="20" />
        <p>你已通过学生认证，发布教材和接龙时会展示认证标识。</p>
        <van-button size="small" round plain type="primary" @click="router.push('/mine')">返回我的</van-button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const form = reactive({ studentNo: '', realName: '' })
const loading = ref(false)

const state = computed(() => store.user?.verifyState || 'UNVERIFIED')

const stateTitle = computed(
  () =>
    ({
      VERIFIED: '已通过认证',
      PENDING: '审核中',
      REJECTED: '认证未通过',
      UNVERIFIED: '还没有完成认证'
    })[state.value] || '还没有完成认证'
)

const stateDesc = computed(
  () =>
    ({
      VERIFIED: '你的学生身份已确认，可以放心交易。',
      PENDING: '已提交，等待管理员审核，通常 1 个工作日内完成。',
      REJECTED: '信息未通过审核，请核对学号与姓名后重新提交。',
      UNVERIFIED: '完成学号认证后，交易时更容易获得同学信任。'
    })[state.value] || ''
)

const stateTone = computed(() => {
  if (state.value === 'VERIFIED') return 'ok'
  if (state.value === 'REJECTED') return 'danger'
  if (state.value === 'PENDING') return 'pending'
  return ''
})

const stateIcon = computed(() => {
  if (state.value === 'VERIFIED') return 'passed'
  if (state.value === 'REJECTED') return 'warning-o'
  if (state.value === 'PENDING') return 'clock-o'
  return 'idcard'
})

async function submit() {
  if (loading.value) return
  const studentNo = form.studentNo.trim()
  const realName = form.realName.trim()
  if (!studentNo) return showToast('请输入学号')
  if (!realName) return showToast('请输入真实姓名')
  loading.value = true
  try {
    await store.verifyStudent({ studentNo, realName })
    showToast('已提交，等待管理员审核')
  } catch {
    // 请求层已经提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.verify-page {
  background: var(--bg);
}
.state-card {
  margin-top: 12px;
  border-radius: var(--radius);
  padding: 18px 16px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: #eef4f1;
  color: var(--brand);
}
.state-card h1 {
  margin: 0 0 4px;
  color: var(--ink);
  font-size: 18px;
  font-weight: 800;
}
.state-card p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-2);
}
.state-card.ok {
  background: #e8f6ee;
}
.state-card.danger {
  background: #fdecea;
  color: var(--accent);
}
.state-card.pending {
  background: #fdf4e4;
  color: #c07d16;
}
.form-group {
  margin-top: 14px;
  overflow: hidden;
}
.tip {
  margin: 10px 20px 0;
  font-size: 11px;
  line-height: 1.6;
}
.submit-wrap {
  padding: 20px 0 24px;
}
.done-card {
  margin-top: 14px;
  text-align: center;
  color: var(--brand);
}
.done-card p {
  margin: 8px 0 14px;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.6;
}
</style>

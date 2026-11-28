<template>
  <div class="page profile-page">
    <van-nav-bar title="个人资料" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <div class="avatar-wrap">
        <div class="avatar">
          <img v-if="form.avatar && !avatarError" :src="form.avatar" alt="头像" @error="avatarError = true" />
          <span v-else>{{ (form.nickname || '同').slice(0, 1) }}</span>
        </div>
        <p class="muted">头像使用图片链接，留空则展示昵称首字</p>
      </div>

      <van-cell-group inset class="form-group">
        <van-field v-model="form.nickname" label="昵称" maxlength="16" placeholder="同学们看到的名字" clearable @update:model-value="avatarError = false" />
        <van-field v-model="form.avatar" label="头像 URL" placeholder="https:// 开头的图片链接" clearable @update:model-value="avatarError = false" />
        <van-field v-model="form.college" label="学院" maxlength="30" placeholder="例如：计算机学院" clearable />
        <van-field v-model="form.major" label="专业" maxlength="30" placeholder="例如：软件工程" clearable />
        <van-field v-model="form.grade" label="年级" maxlength="10" placeholder="例如：2023 级" clearable />
        <van-field v-model="form.dorm" label="宿舍" maxlength="40" placeholder="例如：梅园 3 栋 412" clearable />
      </van-cell-group>

      <p class="muted tip">学院、专业与宿舍会帮助买家判断交易距离，建议如实填写。</p>

      <h2 class="section-title">登录密码</h2>
      <van-cell-group inset class="form-group">
        <van-field v-model="pwd.oldPassword" type="password" label="原密码" placeholder="首次设置可留空" clearable />
        <van-field v-model="pwd.newPassword" type="password" label="新密码" placeholder="至少 6 位" clearable />
      </van-cell-group>
      <p class="muted tip">短信登录的用户可在此首次设置密码，之后可用「手机号 + 密码」登录。</p>
      <div class="submit-wrap pwd-submit">
        <van-button block round plain type="primary" :loading="savingPwd" @click="savePassword">保存密码</van-button>
      </div>

      <div class="submit-wrap">
        <van-button block round type="primary" :loading="saving" loading-text="保存中" @click="save">保存资料</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { authApi } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const saving = ref(false)
const savingPwd = ref(false)
const avatarError = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '' })
const form = reactive({
  nickname: '',
  avatar: '',
  college: '',
  major: '',
  grade: '',
  dorm: ''
})

function fill(user) {
  if (!user) return
  form.nickname = user.nickname || ''
  form.avatar = user.avatar || ''
  form.college = user.college || ''
  form.major = user.major || ''
  form.grade = user.grade || ''
  form.dorm = user.dorm || ''
}

async function save() {
  if (saving.value) return
  if (!form.nickname.trim()) return showToast('请填写昵称')
  const avatar = form.avatar.trim()
  if (avatar && !/^https?:\/\//i.test(avatar)) return showToast('头像链接需以 http:// 或 https:// 开头')

  saving.value = true
  try {
    await store.updateProfile({
      nickname: form.nickname.trim(),
      avatar,
      college: form.college.trim(),
      major: form.major.trim(),
      grade: form.grade.trim(),
      dorm: form.dorm.trim()
    })
    showToast('资料已保存')
    router.back()
  } catch {
    // 请求层已经提示
  } finally {
    saving.value = false
  }
}

async function savePassword() {
  if (savingPwd.value) return
  if (!pwd.newPassword || pwd.newPassword.length < 6) return showToast('新密码至少 6 位')
  savingPwd.value = true
  try {
    await authApi.setPassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    pwd.oldPassword = ''
    pwd.newPassword = ''
    showToast('密码已保存')
  } catch {
    // 请求层已经提示
  } finally {
    savingPwd.value = false
  }
}

onMounted(async () => {
  fill(store.user)
  if (!store.user) {
    try {
      fill(await store.fetchMe())
    } catch {
      // 读取失败时保留空表单
    }
  }
})
</script>

<style scoped>
.profile-page {
  background: var(--bg);
}
.avatar-wrap {
  text-align: center;
  padding: 22px 0 6px;
}
.avatar {
  width: 74px;
  height: 74px;
  margin: 0 auto 8px;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--brand-weak);
  color: var(--brand);
  font-size: 30px;
  font-weight: 800;
  border: 2px solid #fff;
  box-shadow: var(--shadow);
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-wrap p {
  margin: 0;
  font-size: 11px;
}
.form-group {
  margin-top: 12px;
  overflow: hidden;
}
.tip {
  margin: 10px 20px 0;
  font-size: 11px;
  line-height: 1.6;
}
.submit-wrap {
  padding: 22px 0 28px;
}
.section-title {
  font-size: 13px;
  color: var(--ink-2);
  margin: 20px 20px 8px;
}
.pwd-submit {
  padding: 12px 0 0;
}
</style>

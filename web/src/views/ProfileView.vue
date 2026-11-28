<template>
  <div class="page profile-page">
    <van-nav-bar title="个人资料" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <div class="avatar-wrap">
        <button type="button" class="avatar" :disabled="uploading" @click="pickAvatar">
          <img v-if="form.avatar && !avatarError" :src="form.avatar" alt="头像" @error="avatarError = true" />
          <span v-else>{{ (form.nickname || '同').slice(0, 1) }}</span>
          <em class="cam"><van-icon :name="uploading ? 'upgrade' : 'photograph'" size="12" /></em>
        </button>
        <p class="muted">{{ uploading ? '上传中…' : '点击头像上传新图片' }}</p>
        <input ref="fileInput" type="file" accept="image/*" class="file-input" @change="onAvatarChange" />
      </div>

      <div class="badges">
        <button type="button" class="badge" :class="{ on: status.idVerified }" @click="router.push('/verify-center')">
          <van-icon :name="status.idVerified ? 'passed' : 'idcard'" /> 实名
        </button>
        <button type="button" class="badge" :class="{ on: status.studentVerified }" @click="router.push('/verify-center')">
          <van-icon :name="status.studentVerified ? 'passed' : 'notes-o'" /> 学生
        </button>
        <button type="button" class="badge" :class="{ on: status.faceVerified }" @click="router.push('/verify-center')">
          <van-icon :name="status.faceVerified ? 'passed' : 'smile-o'" /> 人脸
        </button>
        <span class="badge-tip">{{ store.canTrade ? '已完成交易认证' : '认证后才能买卖' }}</span>
      </div>

      <van-cell-group inset class="form-group">
        <van-field v-model="form.nickname" label="昵称" maxlength="16" placeholder="同学们看到的名字" clearable />
        <van-field
          v-model="collegeText"
          label="学院"
          readonly
          is-link
          placeholder="请选择学院"
          @click="openPicker('college')"
        />
        <van-field
          v-model="majorText"
          label="专业"
          readonly
          is-link
          placeholder="请先选择学院"
          @click="openPicker('major')"
        />
        <van-field
          v-model="gradeText"
          label="年级"
          readonly
          is-link
          placeholder="请选择年级"
          @click="openPicker('grade')"
        />
        <van-field v-model="form.dorm" label="宿舍" maxlength="40" placeholder="例如：梅园 3 栋 412" clearable />
      </van-cell-group>

      <p class="muted tip">学院、专业与宿舍会帮助买家判断交易距离，建议如实填写。</p>

      <h2 class="section-title">账号与安全</h2>
      <van-cell-group inset class="form-group">
        <van-cell title="登录手机号" :value="maskPhone(store.user?.phone)" is-link @click="openPhoneSheet" />
        <van-cell title="实名与学生认证" :value="store.canTrade ? '已认证' : '未完成'" is-link @click="router.push('/verify-center')" />
      </van-cell-group>

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

      <div class="submit-wrap logout-wrap">
        <van-button block round plain type="danger" @click="logout">退出登录</van-button>
      </div>
    </div>

    <!-- 换绑手机号 -->
    <van-popup v-model:show="phoneSheet" position="bottom" round :style="{ paddingBottom: '20px' }">
      <div class="sheet">
        <h3>换绑手机号</h3>
        <van-cell-group inset class="sheet-group">
          <van-field v-model="phoneForm.phone" type="tel" label="新手机号" maxlength="11" placeholder="请输入 11 位手机号" clearable />
          <van-field v-model="phoneForm.code" type="digit" label="验证码" maxlength="6" placeholder="6 位数字">
            <template #button>
              <van-button size="small" type="primary" plain :disabled="counting > 0 || sending" :loading="sending" @click="sendCode">
                {{ counting > 0 ? `${counting}s` : '获取验证码' }}
              </van-button>
            </template>
          </van-field>
        </van-cell-group>
        <p class="muted tip">验证码会发送到新手机号，换绑后请用新号码登录。</p>
        <div class="sheet-actions">
          <van-button block round type="primary" :loading="changing" @click="changePhone">确认换绑</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 字典选择器 -->
    <van-popup v-model:show="pickerShow" position="bottom" round>
      <van-picker
        :columns="pickerColumns"
        title="请选择"
        @confirm="onPickerConfirm"
        @cancel="pickerShow = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { authApi, fileApi, meApi, metaApi } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const saving = ref(false)
const savingPwd = ref(false)
const uploading = ref(false)
const avatarError = ref(false)
const fileInput = ref(null)
const pwd = reactive({ oldPassword: '', newPassword: '' })
const status = reactive({ idVerified: false, studentVerified: false, faceVerified: false })

const form = reactive({
  nickname: '',
  avatar: '',
  college: '',
  major: '',
  grade: '',
  dorm: ''
})

const options = reactive({ colleges: [], grades: [] })

const pickerShow = ref(false)
const pickerType = ref('college')
const pickerColumns = computed(() => {
  if (pickerType.value === 'college') return options.colleges.map((c) => c.name)
  if (pickerType.value === 'major') {
    const college = options.colleges.find((c) => c.name === form.college)
    return (college?.majors || []).slice()
  }
  return options.grades.slice()
})

const collegeText = computed(() => form.college || '')
const majorText = computed(() => form.major || '')
const gradeText = computed(() => (form.grade ? `${form.grade}级` : ''))

// 换绑手机号
const phoneSheet = ref(false)
const phoneForm = reactive({ phone: '', code: '' })
const sending = ref(false)
const changing = ref(false)
const counting = ref(0)
let timer = null

function maskPhone(phone) {
  if (!phone || phone.length !== 11) return '未绑定'
  return `${phone.slice(0, 3)}****${phone.slice(7)}`
}

function fill(user) {
  if (!user) return
  form.nickname = user.nickname || ''
  form.avatar = user.avatar || ''
  form.college = user.college || ''
  form.major = user.major || ''
  form.grade = user.grade || ''
  form.dorm = user.dorm || ''
  avatarError.value = false
}

function pickAvatar() {
  if (uploading.value) return
  fileInput.value?.click()
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!/^image\//.test(file.type)) return showToast('请选择图片文件')
  if (file.size > 8 * 1024 * 1024) return showToast('图片不能超过 8MB')
  uploading.value = true
  try {
    const res = await fileApi.upload(file)
    const url = typeof res === 'string' ? res : res?.url
    if (!url) {
      showToast('上传失败，请重试')
      return
    }
    form.avatar = url
    avatarError.value = false
    showToast('头像已上传，别忘了保存')
  } catch {
    // 请求层已提示
  } finally {
    uploading.value = false
  }
}

function openPicker(type) {
  if (type === 'major' && !form.college) return showToast('请先选择学院')
  pickerType.value = type
  pickerShow.value = true
}

function onPickerConfirm({ selectedValues }) {
  const value = selectedValues?.[0] || ''
  if (pickerType.value === 'college') {
    if (form.college !== value) form.major = ''
    form.college = value
  } else if (pickerType.value === 'major') {
    form.major = value
  } else {
    form.grade = value.replace('级', '')
  }
  pickerShow.value = false
}

async function save() {
  if (saving.value) return
  if (!form.nickname.trim()) return showToast('请填写昵称')
  saving.value = true
  try {
    await store.updateProfile({
      nickname: form.nickname.trim(),
      avatar: form.avatar,
      college: form.college,
      major: form.major,
      grade: form.grade,
      dorm: form.dorm.trim()
    })
    showToast('资料已保存')
  } catch {
    // 请求层已提示
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
    // 请求层已提示
  } finally {
    savingPwd.value = false
  }
}

function openPhoneSheet() {
  phoneForm.phone = ''
  phoneForm.code = ''
  phoneSheet.value = true
}

async function sendCode() {
  if (counting.value > 0 || sending.value) return
  const phone = phoneForm.phone.trim()
  if (!/^1\d{10}$/.test(phone)) return showToast('请输入正确的 11 位手机号')
  sending.value = true
  try {
    const res = await store.sendSmsCode(phone, 'BIND_PHONE')
    if (res?.devCode) {
      phoneForm.code = String(res.devCode)
      showToast(`开发环境验证码：${res.devCode}`)
    } else {
      showToast('验证码已发送')
    }
    counting.value = 60
    clearInterval(timer)
    timer = setInterval(() => {
      counting.value -= 1
      if (counting.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    // 请求层已提示
  } finally {
    sending.value = false
  }
}

async function changePhone() {
  if (changing.value) return
  const phone = phoneForm.phone.trim()
  if (!/^1\d{10}$/.test(phone)) return showToast('请输入正确的 11 位手机号')
  if (!/^\d{6}$/.test(phoneForm.code.trim())) return showToast('请输入 6 位验证码')
  changing.value = true
  try {
    await meApi.changePhone({ phone, code: phoneForm.code.trim() })
    phoneSheet.value = false
    showToast('手机号已更换')
    await store.fetchMe()
  } catch {
    // 请求层已提示
  } finally {
    changing.value = false
  }
}

async function logout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定退出当前账号吗？' })
  } catch {
    return
  }
  store.logout()
  showToast('已退出')
  router.replace('/login')
}

async function loadMeta() {
  try {
    const res = await metaApi.profileOptions()
    options.colleges = res.colleges || []
    options.grades = res.grades || []
  } catch {
    // 字典失败不影响编辑昵称等
  }
}

async function loadStatus() {
  try {
    const res = await (await import('@/api')).verifyApi.status()
    status.idVerified = Boolean(res?.idVerified)
    status.studentVerified = Boolean(res?.studentVerified)
    status.faceVerified = Boolean(res?.faceVerified)
  } catch {
    // 未登录或接口异常时保持未认证
  }
}

onMounted(async () => {
  fill(store.user)
  loadMeta()
  if (!store.user) {
    try {
      fill(await store.fetchMe())
    } catch {
      // 读取失败时保留空表单
    }
  }
  loadStatus()
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
  position: relative;
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
  padding: 0;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cam {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: grid;
  place-items: center;
}
.file-input {
  display: none;
}
.avatar-wrap p {
  margin: 0;
  font-size: 11px;
}
.badges {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 16px 0;
  flex-wrap: wrap;
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 0;
  font-size: 11px;
  color: var(--ink-3, #94a3b8);
  background: #f2f4f8;
  border-radius: 999px;
  padding: 4px 10px;
}
.badge.on {
  color: #16a34a;
  background: #e6f7ee;
}
.badge-tip {
  font-size: 11px;
  color: var(--ink-3, #94a3b8);
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
  padding: 22px 0 0;
}
.pwd-submit {
  padding: 12px 0 0;
}
.logout-wrap {
  padding: 14px 0 32px;
}
.section-title {
  font-size: 13px;
  color: var(--ink-2);
  margin: 20px 20px 8px;
}
.sheet {
  padding: 16px 0 4px;
}
.sheet h3 {
  margin: 0 0 12px;
  font-size: 15px;
  text-align: center;
}
.sheet-group {
  overflow: hidden;
}
.sheet-actions {
  padding: 18px 16px 0;
}
</style>

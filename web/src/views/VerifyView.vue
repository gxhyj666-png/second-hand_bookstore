<template>
  <div class="page verify-page">
    <van-nav-bar title="实名与认证" left-arrow fixed placeholder safe-area-inset-top @click-left="router.back()" />

    <div class="container">
      <section class="hero" :class="{ ok: canTrade }">
        <div class="hero-icon">
          <van-icon :name="canTrade ? 'shield-o' : 'idcard'" size="26" />
        </div>
        <div class="hero-main">
          <h1>{{ canTrade ? '已具备交易资格' : '完成认证后才能交易' }}</h1>
          <p>{{ canTrade ? '实名、学生认证均已完成，可正常下单与出售教材' : '实名认证 + 学生认证是平台交易的前置门槛' }}</p>
        </div>
      </section>

      <van-steps :active="activeStep" active-color="#2f7d5a" inactive-color="#c8cdd2" class="steps">
        <van-step>实名认证</van-step>
        <van-step>学生认证</van-step>
        <van-step>人脸核身</van-step>
      </van-steps>

      <!-- 1. 实名 -->
      <section class="card step-card">
        <div class="between step-head">
          <h2>实名认证</h2>
          <span class="tag" :class="st.idVerified ? 'ok' : 'warn'">{{ st.idVerified ? '已完成' : '待完成' }}</span>
        </div>
        <template v-if="st.idVerified">
          <div class="done-row">
            <span>姓名</span>
            <strong>{{ st.realName || '已实名' }}</strong>
          </div>
          <div v-if="st.idNo" class="done-row">
            <span>证件号</span>
            <strong>{{ maskId(st.idNo) }}</strong>
          </div>
          <p class="muted tip">身份信息已通过校验，如需修改请联系客服。</p>
        </template>
        <template v-else>
          <van-cell-group inset class="form">
            <van-field v-model="idForm.realName" label="真实姓名" maxlength="20" placeholder="与证件一致" />
            <van-field v-model="idForm.idNo" label="身份证号" maxlength="18" placeholder="18 位身份证号" />
          </van-cell-group>
          <div class="upload-grid">
            <div>
              <DemoUploader v-model="idForm.frontUrl" label="身份证人像面" file-key="id-front" />
            </div>
            <div>
              <DemoUploader v-model="idForm.backUrl" label="身份证国徽面" file-key="id-back" />
            </div>
          </div>
          <van-button
            block
            round
            type="primary"
            class="submit-btn"
            :loading="busy.id"
            loading-text="提交中"
            @click="submitId"
          >
            提交实名认证
          </van-button>
        </template>
      </section>

      <!-- 2. 学生 -->
      <section class="card step-card">
        <div class="between step-head">
          <h2>学生认证</h2>
          <span class="tag" :class="st.studentVerified ? 'ok' : 'warn'">
            {{ st.studentVerified ? '已完成' : st.verifyState === 'PENDING' ? '审核中' : '待完成' }}
          </span>
        </div>
        <template v-if="st.studentVerified">
          <div v-if="st.studentNo" class="done-row">
            <span>学号</span>
            <strong>{{ st.studentNo }}</strong>
          </div>
          <p class="muted tip">学生身份已认证，可享受校园交易保障。</p>
        </template>
        <template v-else>
          <p v-if="st.verifyState === 'PENDING'" class="pending-tip">
            <van-icon name="clock-o" /> 资料已提交，等待管理员审核，审核通过后自动生效。
          </p>
          <van-cell-group inset class="form">
            <van-field v-model="studentForm.schoolName" label="学校" maxlength="40" placeholder="例如：XX 大学" />
            <van-field v-model="studentForm.studentNo" label="学号" maxlength="30" placeholder="请输入学号" />
          </van-cell-group>
          <div class="upload-grid">
            <div>
              <DemoUploader v-model="studentForm.coverUrl" label="学生证封面" file-key="student-cover" />
            </div>
            <div>
              <DemoUploader v-model="studentForm.infoUrl" label="学生证信息页" file-key="student-info" />
            </div>
          </div>
          <van-button
            block
            round
            type="primary"
            class="submit-btn"
            :loading="busy.student"
            loading-text="提交中"
            @click="submitStudent"
          >
            提交学生认证
          </van-button>
        </template>
      </section>

      <!-- 3. 人脸 -->
      <section class="card step-card">
        <div class="between step-head">
          <h2>人脸核身</h2>
          <span class="tag" :class="st.faceVerified ? 'ok' : 'warn'">{{ st.faceVerified ? '已完成' : '待完成' }}</span>
        </div>
        <template v-if="st.faceVerified">
          <p class="muted tip">人脸信息已核验通过。</p>
        </template>
        <template v-else>
          <p class="muted tip">请上传一张清晰正面照完成活体核身，需先完成实名认证。</p>
          <div class="face-wrap">
            <DemoUploader v-model="faceForm.imageUrl" label="上传正面照" file-key="face" />
          </div>
          <van-button
            block
            round
            type="primary"
            class="submit-btn"
            :disabled="!st.idVerified"
            :loading="busy.face"
            loading-text="核验中"
            @click="submitFace"
          >
            {{ st.idVerified ? '提交人脸核身' : '请先完成实名认证' }}
          </van-button>
        </template>
      </section>

      <p class="muted foot">开发模式：认证接口会直接返回通过；正式环境学生认证需人工审核。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { verifyApi } from '@/api'
import { useUserStore } from '@/store/user'
import DemoUploader from '@/components/DemoUploader.vue'

const router = useRouter()
const store = useUserStore()

const st = reactive({
  realName: '',
  idNo: '',
  idVerified: false,
  studentVerified: false,
  faceVerified: false,
  verifyState: '',
  studentNo: ''
})

const idForm = reactive({ realName: '', idNo: '', frontUrl: '', backUrl: '' })
const studentForm = reactive({ schoolName: '', studentNo: '', coverUrl: '', infoUrl: '' })
const faceForm = reactive({ imageUrl: '' })

const busy = reactive({ id: false, student: false, face: false })
const loading = ref(false)

const canTrade = computed(() => st.idVerified && st.studentVerified)
const activeStep = computed(() => {
  if (!st.idVerified) return 0
  if (!st.studentVerified) return 1
  return 2
})

const flag = (v) => v === true || v === 1 || v === '1' || v === 'true'

function maskId(idNo) {
  const s = String(idNo || '')
  if (s.length < 8) return s
  return `${s.slice(0, 4)}${'*'.repeat(Math.max(0, s.length - 8))}${s.slice(-4)}`
}

function applyStatus(data) {
  if (!data) return
  st.realName = data.realName || ''
  st.idNo = data.idNo || ''
  st.studentNo = data.studentNo || ''
  st.idVerified = flag(data.idVerified)
  st.studentVerified = flag(data.studentVerified)
  st.faceVerified = flag(data.faceVerified)
  st.verifyState = data.verifyState || ''
  // 回写 store，Mine 页的徽标即时更新
  if (store.user) {
    store.setUser({
      ...store.user,
      idVerified: st.idVerified,
      studentVerified: st.studentVerified,
      faceVerified: st.faceVerified,
      verifyState: st.verifyState || store.user.verifyState
    })
  }
}

async function refresh() {
  loading.value = true
  try {
    applyStatus(await verifyApi.status())
  } catch {
    // 请求层已经提示
  } finally {
    loading.value = false
  }
}

async function submitId() {
  if (busy.id) return
  if (!idForm.realName.trim()) return showToast('请输入真实姓名')
  if (!/^\d{17}[\dXx]$/.test(idForm.idNo.trim())) return showToast('请输入 18 位身份证号')
  if (!idForm.frontUrl || !idForm.backUrl) return showToast('请上传身份证正反面照片')
  busy.id = true
  try {
    applyStatus(
      await verifyApi.idCard({
        realName: idForm.realName.trim(),
        idNo: idForm.idNo.trim().toUpperCase(),
        frontUrl: idForm.frontUrl,
        backUrl: idForm.backUrl
      })
    )
    showToast('实名认证已通过')
    await refresh()
  } catch {
    // 请求层已经提示
  } finally {
    busy.id = false
  }
}

async function submitStudent() {
  if (busy.student) return
  if (!studentForm.schoolName.trim()) return showToast('请输入学校名称')
  if (!studentForm.studentNo.trim()) return showToast('请输入学号')
  if (!studentForm.coverUrl || !studentForm.infoUrl) return showToast('请上传学生证封面和信息页')
  busy.student = true
  try {
    const res = await verifyApi.student({
      schoolName: studentForm.schoolName.trim(),
      studentNo: studentForm.studentNo.trim(),
      coverUrl: studentForm.coverUrl,
      infoUrl: studentForm.infoUrl
    })
    applyStatus({ ...st, ...res })
    showToast(res?.studentVerified ? '学生认证已通过' : '已提交，等待审核')
    await refresh()
  } catch {
    // 请求层已经提示
  } finally {
    busy.student = false
  }
}

async function submitFace() {
  if (busy.face) return
  if (!st.idVerified) return showToast('请先完成实名认证')
  if (!faceForm.imageUrl) return showToast('请上传正面照')
  busy.face = true
  try {
    applyStatus({ ...st, ...(await verifyApi.face({ imageUrl: faceForm.imageUrl })) })
    showToast('人脸核身已通过')
    await refresh()
  } catch {
    // 请求层已经提示
  } finally {
    busy.face = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.verify-page {
  background: var(--bg);
}
.hero {
  margin-top: 12px;
  padding: 16px;
  border-radius: var(--radius);
  background: linear-gradient(135deg, #e08a2e, #e6a23c);
  color: #fff;
  display: flex;
  gap: 12px;
  align-items: center;
}
.hero.ok {
  background: linear-gradient(135deg, #2f7d5a, #4c9d78);
}
.hero-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: grid;
  place-items: center;
  flex: none;
}
.hero-main h1 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 800;
}
.hero-main p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  opacity: 0.92;
}
.steps {
  margin-top: 12px;
  background: #fff;
  border-radius: var(--radius);
  padding: 10px 4px;
}
.step-card {
  margin-top: 12px;
}
.step-head {
  margin-bottom: 8px;
}
.step-head h2 {
  margin: 0;
  font-size: 16px;
}
.form {
  margin: 8px 0;
  overflow: hidden;
}
.upload-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 10px 0 2px;
}
.face-wrap {
  max-width: 50%;
  margin: 10px 0 2px;
}
.submit-btn {
  margin-top: 12px;
}
.done-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--line);
}
.done-row span {
  color: var(--muted);
}
.done-row:last-of-type {
  border-bottom: 0;
}
.tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
}
.pending-tip {
  margin: 4px 0 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff8e8;
  color: #a9741b;
  font-size: 12px;
  line-height: 1.5;
}
.tag.ok {
  color: var(--brand);
  background: var(--brand-weak);
}
.tag.warn {
  color: #a9741b;
  background: #fff8e8;
}
.foot {
  margin: 16px 4px;
  text-align: center;
  font-size: 11px;
  line-height: 1.6;
}
</style>

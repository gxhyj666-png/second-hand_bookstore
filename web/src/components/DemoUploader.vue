<template>
  <div class="uploader">
    <button type="button" class="box" :class="{ filled: Boolean(modelValue) }" @click="pick">
      <video v-if="isVideo && preview && !broken" :src="preview" muted playsinline @error="broken = true" />
      <img v-else-if="preview && !broken" :src="preview" :alt="label" @error="broken = true" />
      <template v-else>
        <van-icon :name="modelValue ? 'passed' : isVideo ? 'video-o' : 'photograph'" size="20" />
        <span class="label">{{ uploading ? '上传中…' : modelValue ? '已上传' : label }}</span>
        <span v-if="!modelValue && !uploading" class="sub">点击上传</span>
      </template>
    </button>
    <div class="ops">
      <button type="button" class="op" :disabled="uploading" @click="pick">
        {{ uploading ? '上传中' : modelValue ? '重选' : '上传' }}
      </button>
      <button v-if="modelValue" type="button" class="op clear" @click="clear">清除</button>
    </div>
    <input ref="fileRef" type="file" :accept="isVideo ? 'video/*' : 'image/*'" class="file" @change="onFile" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { fileApi } from '@/api'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '上传图片' },
  /** 上传类型：image / video */
  kind: { type: String, default: 'image' },
  /** 兼容旧用法，仅用于提示 */
  fileKey: { type: String, default: 'img' }
})
const emit = defineEmits(['update:modelValue'])

const fileRef = ref(null)
const localPreview = ref('')
const broken = ref(false)
const uploading = ref(false)

const isVideo = computed(() => props.kind === 'video')
const preview = computed(() => {
  if (localPreview.value) return localPreview.value
  return /^(https?:\/\/|\/v1\/files\/)/i.test(props.modelValue) ? props.modelValue : ''
})

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      if (localPreview.value) URL.revokeObjectURL(localPreview.value)
      localPreview.value = ''
      broken.value = false
    }
  }
)

function pick() {
  if (uploading.value) return
  fileRef.value?.click()
}

async function onFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (localPreview.value) URL.revokeObjectURL(localPreview.value)
  localPreview.value = URL.createObjectURL(file)
  broken.value = false
  uploading.value = true
  try {
    const res = await fileApi.upload(file, isVideo.value ? 'video' : 'image')
    emit('update:modelValue', res?.url || res?.path || '')
  } catch {
    localPreview.value = ''
  } finally {
    uploading.value = false
  }
}

function clear() {
  emit('update:modelValue', '')
  if (localPreview.value) URL.revokeObjectURL(localPreview.value)
  localPreview.value = ''
  broken.value = false
}
</script>

<style scoped>
.uploader {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.box {
  width: 100%;
  aspect-ratio: 4 / 3;
  border: 1px dashed var(--line);
  border-radius: 10px;
  background: var(--bg);
  color: var(--muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  overflow: hidden;
  cursor: pointer;
  padding: 0;
}
.box.filled {
  border-style: solid;
  border-color: var(--brand);
  background: var(--brand-weak);
  color: var(--brand);
}
.box img,
.box video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.label {
  font-size: 12px;
}
.sub {
  font-size: 10px;
  color: var(--muted);
}
.ops {
  display: flex;
  gap: 6px;
}
.op {
  flex: 1;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-2);
  border-radius: 999px;
  padding: 3px 0;
  font-size: 11px;
  cursor: pointer;
}
.op:disabled {
  opacity: 0.6;
}
.op.clear {
  color: var(--muted);
}
.file {
  display: none;
}
</style>

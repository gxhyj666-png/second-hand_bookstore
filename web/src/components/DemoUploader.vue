<template>
  <div class="uploader">
    <button type="button" class="box" :class="{ filled: Boolean(modelValue) }" @click="pick">
      <img v-if="preview && !broken" :src="preview" :alt="label" @error="broken = true" />
      <template v-else>
        <van-icon :name="modelValue ? 'passed' : 'photograph'" size="20" />
        <span class="label">{{ modelValue ? '已选择' : label }}</span>
        <span v-if="!modelValue" class="sub">点击上传</span>
      </template>
    </button>
    <div class="ops">
      <button type="button" class="op" @click="pick">{{ modelValue ? '重选' : '上传' }}</button>
      <button v-if="modelValue" type="button" class="op clear" @click="clear">清除</button>
    </div>
    <input ref="fileRef" type="file" accept="image/*" class="file" @change="onFile" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '上传图片' },
  /** 用于生成 demo 占位文件名，例如 cover / id-front */
  fileKey: { type: String, default: 'img' }
})
const emit = defineEmits(['update:modelValue'])

const fileRef = ref(null)
const localPreview = ref('')
const broken = ref(false)

const preview = computed(() => {
  if (localPreview.value) return localPreview.value
  return /^https?:\/\//i.test(props.modelValue) ? props.modelValue : ''
})

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      localPreview.value = ''
      broken.value = false
    }
  }
)

function pick() {
  fileRef.value?.click()
}

function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (localPreview.value) URL.revokeObjectURL(localPreview.value)
  localPreview.value = URL.createObjectURL(file)
  broken.value = false
  const ext = (file.name.split('.').pop() || 'jpg').toLowerCase()
  // 开发期没有对象存储：本地预览 + 提交约定好的占位 URL 字符串
  emit('update:modelValue', `demo/${props.fileKey}-${Date.now()}.${ext}`)
  e.target.value = ''
}

function clear() {
  emit('update:modelValue', '')
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
.box img {
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
.op.clear {
  color: var(--muted);
}
.file {
  display: none;
}
</style>

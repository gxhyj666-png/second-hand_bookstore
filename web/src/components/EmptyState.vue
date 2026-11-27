<template>
  <div class="empty">
    <div class="icon">{{ icon }}</div>
    <p class="text">{{ text }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  text: { type: String, default: '这里还没有内容' },
  icon: { type: String, default: '📚' }
})
</script>

<style scoped>
.empty {
  padding: 56px 20px;
  text-align: center;
  color: var(--muted);
}
.icon {
  font-size: 34px;
  margin-bottom: 10px;
  opacity: 0.85;
}
.text {
  margin: 0 0 14px;
  font-size: 14px;
}
</style>

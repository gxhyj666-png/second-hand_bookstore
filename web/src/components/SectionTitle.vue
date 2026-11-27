<template>
  <div class="section-title">
    <span>{{ title }}</span>
    <router-link v-if="to" class="more" :to="to">{{ more || '查看全部' }} ›</router-link>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  to: { type: [String, Object], default: '' },
  more: { type: String, default: '' }
})
</script>

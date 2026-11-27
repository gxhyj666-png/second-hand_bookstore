<template>
  <article class="listing-card" @click="go">
    <div class="cover">
      <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" />
      <span v-else class="cover-fallback">{{ initial }}</span>
      <span v-if="item.condition" class="cond">{{ conditionText(item.condition) }}</span>
    </div>
    <div class="body">
      <h3 class="title clamp-2">{{ item.title || '未命名教材' }}</h3>
      <div class="meta">
        <span class="price">{{ money(item.price) }}</span>
        <del v-if="item.originalPrice" class="origin">{{ money(item.originalPrice) }}</del>
      </div>
      <div class="foot">
        <span class="clamp-1">{{ item.dorm || '校内自提' }}</span>
        <span v-if="item.status && item.status !== 'ON_SALE'" class="tag muted">
          {{ listingStatusText(item.status) }}
        </span>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { money, conditionText, listingStatusText } from '@/utils/format'

const props = defineProps({
  item: { type: Object, required: true }
})

const router = useRouter()
const initial = computed(() => (props.item.title || '书').slice(0, 1))

function go() {
  if (props.item.id) router.push(`/listing/${props.item.id}`)
}
</script>

<style scoped>
.listing-card {
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.listing-card:active {
  transform: scale(0.99);
}
@media (hover: hover) {
  .listing-card:hover {
    box-shadow: 0 4px 22px rgba(20, 30, 40, 0.1);
  }
}
.cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, #eaf4ef, #f6f8f7);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-fallback {
  font-size: 30px;
  font-weight: 700;
  color: #7fb69b;
}
.cond {
  position: absolute;
  left: 8px;
  top: 8px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--brand);
}
.body {
  padding: 10px 12px 12px;
}
.title {
  margin: 0 0 6px;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 600;
  min-height: 40px;
}
.meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.origin {
  color: var(--muted);
  font-size: 12px;
}
.foot {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}
</style>

<template>
  <div class="view-switch" role="group" :aria-label="label">
    <button type="button" :class="{ active: webListMode === 'cover' }"
      :aria-pressed="webListMode === 'cover'" @click="setWebListMode('cover')">封面</button>
    <button type="button" :class="{ active: webListMode === 'compact' }"
      :aria-pressed="webListMode === 'compact'" @click="setWebListMode('compact')">简洁</button>
  </div>
</template>

<script setup>
import { webListMode, setWebListMode } from '@/utils/webListMode';

defineProps({
  label: { type: String, default: '列表展示方式' },
});
</script>

<style scoped>
.view-switch {
  display: inline-flex;
  flex-shrink: 0;
  padding: 3px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #f1f5f9;
}
.view-switch button {
  border: 0;
  border-radius: 999px;
  padding: 5px 12px;
  background: transparent;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
}
.view-switch button.active {
  background: #fff;
  color: #0369a1;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
}
.view-switch button:focus-visible {
  outline: 2px solid #0284c7;
  outline-offset: 2px;
}
</style>

<template>
  <nav class="web-list-pager" aria-label="列表分页导航">
    <span class="web-list-pager__count">共 {{ total }} 条</span>
    <div v-if="size > 0 && pages > 1" class="web-list-pager__pages" aria-label="选择页码">
      <button type="button" class="web-list-pager__arrow" :disabled="page <= 1" aria-label="上一页"
        @click="emitChange(page - 1, size)">‹</button>
      <template v-for="item in pageNumbers" :key="item.key">
        <span v-if="item.ellipsis" class="web-list-pager__ellipsis" aria-hidden="true">…</span>
        <button v-else type="button" class="web-list-pager__number" :class="{ 'is-current': item.number === page }"
          :aria-label="`第 ${item.number} 页`" :aria-current="item.number === page ? 'page' : undefined"
          @click="emitChange(item.number, size)">{{ item.number }}</button>
      </template>
      <button type="button" class="web-list-pager__arrow" :disabled="page >= pages" aria-label="下一页"
        @click="emitChange(page + 1, size)">›</button>
    </div>
    <form class="web-list-pager__size" @submit.prevent="applySize">
      <label :for="sizeId">每页</label>
      <input :id="sizeId" v-model="sizeInput" type="text" inputmode="numeric" pattern="[0-9]*"
        aria-label="每页条数，留空表示不限制" />
      <button type="submit" class="web-list-pager__apply">确定</button>
    </form>
  </nav>
</template>

<script setup>
import { computed, ref, useId, watch } from 'vue';

const props = defineProps({
  page: { type: Number, default: 1 },
  size: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
});
const emit = defineEmits(['change']);
const sizeId = useId();
const sizeInput = ref(props.size === 0 ? '' : String(props.size));
const pages = computed(() => props.size > 0 ? Math.max(1, Math.ceil(props.total / props.size)) : 1);
const pageNumbers = computed(() => {
  const last = pages.value;
  const visible = new Set([1, last]);
  for (let index = Math.max(1, props.page - 1); index <= Math.min(last, props.page + 1); index++) visible.add(index);
  const sorted = [...visible].sort((a, b) => a - b);
  const result = [];
  sorted.forEach((number, index) => {
    if (index && number - sorted[index - 1] > 1) {
      if (number - sorted[index - 1] === 2) result.push({ key: `page-${number - 1}`, number: number - 1 });
      else result.push({ key: `ellipsis-${number}`, ellipsis: true });
    }
    result.push({ key: `page-${number}`, number });
  });
  return result;
});
watch(() => props.size, (size) => {
  sizeInput.value = size === 0 ? '' : String(size);
});

function emitChange(page, size) {
  if (page === props.page && size === props.size) return;
  emit('change', { page, size });
}
function applySize() {
  const input = String(sizeInput.value ?? '').trim();
  const value = input === '' ? 0 : Number(input);
  if (!Number.isSafeInteger(value) || value < 0) {
    sizeInput.value = props.size === 0 ? '' : String(props.size);
    return;
  }
  if (value === 0) sizeInput.value = '';
  emitChange(1, value);
}
</script>

<style scoped>
.web-list-pager { display:flex; align-items:center; justify-content:flex-end; flex-wrap:wrap; gap:14px; width:100%; margin:64px 0 40px; color:#64748b; font-size:12px; }
.web-list-pager__count { white-space:nowrap; font-variant-numeric:tabular-nums; }
.web-list-pager__pages { display:flex; align-items:center; justify-content:center; gap:3px; }
.web-list-pager button { font:inherit; cursor:pointer; }
.web-list-pager__pages button { min-width:28px; height:28px; padding:0 5px; border:1px solid #d9deea; border-radius:5px; background:#fff; color:#475569; font-variant-numeric:tabular-nums; }
.web-list-pager__pages button:hover:not(:disabled) { border-color:#978be5; color:#5749b7; }
.web-list-pager__pages button.is-current { border-color:#6457cc; background:#6457cc; color:#fff; }
.web-list-pager__pages button:disabled { opacity:.35; cursor:default; }
.web-list-pager__ellipsis { padding:0 3px; }
.web-list-pager__size { display:flex; align-items:center; gap:9px; white-space:nowrap; }
.web-list-pager__size input { box-sizing:border-box; width:58px; height:28px; padding:0 5px; border:1px solid #d9deea; border-radius:5px; background:#fff; color:#475569; font:inherit; }
.web-list-pager__apply { height:28px; padding:0 8px; border:1px solid #d9deea; border-radius:5px; background:#fff; color:#475569; }
.web-list-pager__apply:hover { border-color:#978be5; color:#5749b7; }
.web-list-pager button:focus-visible,.web-list-pager input:focus-visible { outline:2px solid #7060dc; outline-offset:2px; }
</style>

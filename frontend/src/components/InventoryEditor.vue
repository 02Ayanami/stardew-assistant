<script setup lang="ts">
import type { Entity } from "../api/types";
export interface Row {
  itemId: string;
  quantity: number;
  quality?: string;
}
const props = defineProps<{
  modelValue: Row[];
  items: Entity[];
  quality?: boolean;
}>();
const emit = defineEmits<{ "update:modelValue": [Row[]] }>();
function add() {
  emit("update:modelValue", [
    ...props.modelValue,
    { itemId: "", quantity: 1, quality: "normal" },
  ]);
}
function remove(index: number) {
  emit(
    "update:modelValue",
    props.modelValue.filter((_, i) => i !== index),
  );
}
</script>
<template>
  <div class="inventory">
    <div v-for="(row, index) in modelValue" :key="index" class="inventory-row">
      <select v-model="row.itemId" aria-label="物品" required>
        <option disabled value="">选择物品</option>
        <option v-for="item in items" :key="item.id" :value="item.id">
          {{ item.name }}
        </option>
      </select>
      <input
        v-model.number="row.quantity"
        type="number"
        min="1"
        step="1"
        aria-label="物品数量"
        required
      />
      <select v-if="quality" v-model="row.quality" aria-label="品质">
        <option value="normal">普通</option>
        <option value="silver">银星</option>
        <option value="gold">金星</option>
        <option value="iridium">铱星</option>
      </select>
      <button
        type="button"
        class="small"
        @click="remove(index)"
        aria-label="移除物品"
      >
        ×
      </button>
    </div>
    <button type="button" class="small" @click="add">＋ 添加物品</button>
  </div>
</template>

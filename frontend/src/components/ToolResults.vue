<script setup lang="ts">
import { computed } from "vue";
import type { ToolExecution, ResultBundle } from "../api/types";
import ComparisonPanel from "./ComparisonPanel.vue";
import MaterialPanel from "./MaterialPanel.vue";
import DomainResults from "./DomainResults.vue";
const props = defineProps<{ executions: ToolExecution[] }>();
const panels = computed(() =>
  props.executions.map((e) => ({
    e,
    results: {
      [(
        {
          findFish: "fish_matches",
          queryGiftPreferences: "gift_preferences",
          getBundleMissing: "bundle_missing",
        } as Record<string, string>
      )[e.name] || e.name]: e.result,
    } as ResultBundle,
  })),
);
const labels: Record<string, string> = {
  calculateCropProfit: "种植收益",
  expandMaterialRequirements: "制作材料",
  findFish: "钓鱼筛选",
  queryGiftPreferences: "送礼查询",
  getBundleMissing: "献祭缺项",
  queryGameData: "游戏资料",
  listCrops: "作物列表",
  searchGuide: "攻略检索",
};
</script>
<template>
  <div
    class="tool-results"
    v-for="({ e, results }, index) in panels"
    :key="index"
  >
    <p v-if="e.result.errorCode" class="notice-box">{{ e.result.errorCode }}</p>
    <template v-if="e.result.data">
      <ComparisonPanel
        v-if="e.name === 'calculateCropProfit'"
        :comparison="e.result as any"
      />
      <MaterialPanel
        v-else-if="e.name === 'expandMaterialRequirements'"
        :materials="e.result as any"
      />
      <DomainResults
        v-else-if="
          ['findFish', 'queryGiftPreferences', 'getBundleMissing'].includes(
            e.name,
          )
        "
        :results="results"
      />
      <details v-else>
        <summary>{{ labels[e.name] || e.name }}结果</summary>
        <pre>{{ JSON.stringify(e.result.data, null, 2) }}</pre>
      </details>
    </template>
    <details class="tool-input">
      <summary>{{ labels[e.name] || e.name }} · 实际使用的条件</summary>
      <pre>{{ JSON.stringify(e.args, null, 2) }}</pre>
    </details>
  </div>
</template>

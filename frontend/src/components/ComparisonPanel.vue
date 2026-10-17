<script setup lang="ts">
import type { CropProjection, ToolResult, GameDate } from "../api/types";
defineProps<{ comparison: ToolResult<CropProjection[]> }>();
const seasons = { spring: "春", summer: "夏", fall: "秋", winter: "冬" };
const date = (d: GameDate) => `第${d.year}年${seasons[d.season]}${d.day}日`;
</script>
<template>
  <section class="result-card">
    <div class="card-heading">
      <span class="eyebrow">种植方案比较</span
      ><span class="muted">分别计算</span>
    </div>
    <div v-for="crop in comparison.data" :key="crop.cropId" class="crop-result">
      <div class="crop-title">
        <h3>{{ crop.name }}</h3>
        <span class="badge" :class="crop.feasible ? 'passed' : 'conflict'">{{
          crop.feasible ? "可行" : "不可行"
        }}</span>
      </div>
      <div class="money-grid">
        <div>
          <small>总成本</small
          ><strong>{{ crop.totalCost.toLocaleString() }}<em>g</em></strong>
        </div>
        <div>
          <small>总收入</small
          ><strong>{{ crop.totalRevenue.toLocaleString() }}<em>g</em></strong>
        </div>
        <div class="profit">
          <small>净收益</small
          ><strong>{{ crop.netProfit.toLocaleString() }}<em>g</em></strong>
        </div>
      </div>
      <p v-if="crop.harvests.length" class="muted">
        收获：{{ crop.harvests.map((h) => date(h.date)).join("、") }}
      </p>
      <p v-if="crop.reason" class="notice-box conflict">{{ crop.reason }}</p>
    </div>
    <details class="assumptions">
      <summary>计算条件与边界</summary>
      <p v-for="item in comparison.assumptions" :key="item">{{ item }}</p>
    </details>
  </section>
</template>

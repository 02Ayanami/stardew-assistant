<script setup lang="ts">
import type { ResultBundle } from "../api/types";
defineProps<{ results: ResultBundle }>();
const levels: Record<string, string> = {
  love: "最爱",
  like: "喜欢",
  neutral: "一般",
  dislike: "不喜欢",
  hate: "讨厌",
};
const qualities: Record<string, string> = {
  normal: "普通",
  silver: "银星",
  gold: "金星",
  iridium: "铱星",
};
</script>
<template>
  <section v-if="results.fish_matches?.data" class="result-card">
    <h3>钓鱼条件匹配</h3>
    <p class="footnote">仅列当前资料匹配项，不代表全游戏列表。</p>
    <p v-if="!results.fish_matches.data.fish.length">当前资料没有匹配项。</p>
    <article v-for="fish in results.fish_matches.data.fish" :key="fish.id">
      <strong>{{ fish.name }}</strong>
      <p v-for="(h, index) in fish.habitats" :key="index">
        {{ h.locations.join(" / ") }} · {{ h.seasons.join(" / ") }} ·
        {{ h.weather.join(" / ") }} · {{ h.startTime }}—{{ h.endTime }}
      </p>
    </article>
  </section>
  <section v-if="results.gift_preferences?.data" class="result-card">
    <h3>送礼偏好</h3>
    <p class="footnote">个人例外优先；只展示已核验偏好，不计算好感度。</p>
    <p v-if="!results.gift_preferences.data.preferences.length">
      当前资料没有匹配项。
    </p>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>村民</th>
            <th>礼物</th>
            <th>偏好</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="p in results.gift_preferences.data.preferences"
            :key="p.villagerId + ':' + p.itemId"
          >
            <td>{{ p.villagerName }}</td>
            <td>{{ p.itemName }}</td>
            <td>{{ levels[p.level] || p.level }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <section v-if="results.bundle_missing?.data" class="result-card">
    <h3>{{ results.bundle_missing.data.bundleName }} · 献祭缺项</h3>
    <p v-if="results.bundle_missing.data.goldRequired">
      需 {{ results.bundle_missing.data.goldRequired }}g；当前还缺
      {{ results.bundle_missing.data.goldMissing }}g。
    </p>
    <template v-else>
      <p>
        已提交完成 {{ results.bundle_missing.data.completedSlots }} 项，还需提交
        {{ results.bundle_missing.data.remainingSlots }}
        项；从未完成选项中任选，不需提交所有备选。
      </p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>备选</th>
              <th>品质</th>
              <th>已提交</th>
              <th>背包可用</th>
              <th>差额</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(p, index) in results.bundle_missing.data.options"
              :key="p.slotId || index"
            >
              <td>{{ p.name }} × {{ p.quantity }}</td>
              <td>{{ qualities[p.quality] || p.quality }}</td>
              <td>{{ p.submitted }}</td>
              <td>{{ p.usableOwned }}</td>
              <td>{{ p.missing }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <p class="footnote">仅核算此包，不为其他收集包分配库存。</p>
  </section>
</template>

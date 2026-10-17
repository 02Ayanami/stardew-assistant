<script setup lang="ts">
import type { MaterialExpansion, ToolResult } from "../api/types";
defineProps<{ materials: ToolResult<MaterialExpansion> }>();
</script>
<template>
  <section class="result-card">
    <div class="card-heading">
      <span class="eyebrow">材料清单</span
      ><span class="muted">目标数量 {{ materials.data.quantity }}</span>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>材料</th>
            <th>从零需求</th>
            <th>实际缺口</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in materials.data.materials" :key="line.id">
            <td>{{ line.name }}</td>
            <td>{{ line.totalRequired }}</td>
            <td>
              <strong>{{ line.missingQuantity }}</strong>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="footnote">实际缺口已抵扣你声明的原料或中间物品库存。</p>
    <details v-if="materials.data.materials.length" class="assumptions">
      <summary>材料获取方式</summary>
      <p v-for="line in materials.data.materials" :key="line.id">
        <strong>{{ line.name }}</strong> ·
        {{ line.acquisition.join("；") || "尚未收录" }}
      </p>
    </details>
    <p v-if="materials.data.prerequisites.length" class="notice-box">
      需核实：{{ materials.data.prerequisites.join("；") }}
    </p>
    <p v-if="materials.data.gaps.length" class="notice-box conflict">
      资料缺口：{{ materials.data.gaps.join("；") }}
    </p>
  </section>
</template>

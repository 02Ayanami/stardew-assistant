<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { api } from "../api/client";
import type { Entity } from "../api/types";
import {
  tools,
  drafts,
  emptyDraft,
  buildArgs,
  validateDraft,
  type ToolKind,
  type ToolDraft,
} from "../tools";
import InventoryEditor from "./InventoryEditor.vue";
const props = defineProps<{ kind: ToolKind; busy: boolean }>();
const emit = defineEmits<{
  submit: [name: string, args: Record<string, unknown>];
  error: [message: string];
}>();
const d = reactive<ToolDraft>(emptyDraft());
const options = ref<Record<string, any>>({});
const loading = ref(false);
const localError = ref("");
const current = computed(() => tools.find((t) => t.id === props.kind)!);
const bundle = computed<Entity | undefined>(() =>
  options.value.bundles?.find((b: Entity) => b.id === d.bundle),
);
const vault = computed(() => Number(bundle.value?.attributes.gold) > 0);
const names: Record<string, string> = {
  spring: "春季",
  summer: "夏季",
  fall: "秋季",
  winter: "冬季",
  sun: "晴天",
  rain: "雨天",
  any: "不限",
  love: "最爱",
  like: "喜欢",
  neutral: "一般",
  dislike: "不喜欢",
  hate: "讨厌",
};
function cloneDraft(value: ToolDraft): ToolDraft {
  return {
    ...value,
    start: { ...value.start },
    end: { ...value.end },
    cropIds: [...value.cropIds],
    ownedMaterials: value.ownedMaterials.map((row) => ({ ...row })),
    submitted: value.submitted.map((row) => ({ ...row })),
    owned: value.owned.map((row) => ({ ...row })),
  };
}
onBeforeUnmount(() => {
  drafts[props.kind] = cloneDraft(d);
});
let generation = 0;
watch(
  () => props.kind,
  async () => {
    const ticket = ++generation;
    loading.value = true;
    localError.value = "";
    options.value = {};
    Object.assign(
      d,
      drafts[props.kind] ? cloneDraft(drafts[props.kind]!) : emptyDraft(),
    );
    try {
      const result = await api.options(current.value.name);
      if (ticket === generation) options.value = result.options;
    } catch (e) {
      if (ticket === generation) localError.value = (e as Error).message;
    } finally {
      if (ticket === generation) loading.value = false;
    }
  },
  { immediate: true },
);
watch(
  () => d.bundle,
  () => {
    d.submitted = [];
    d.submittedEmpty = false;
  },
);
function submit() {
  localError.value = validateDraft(props.kind, d, vault.value);
  if (localError.value) return;
  if (props.kind === "crops" && !d.cropIds.length) {
    localError.value = "请至少选择一种作物";
    return;
  }
  if (props.kind === "gifts" && !d.villager && !d.item) {
    localError.value = "请至少选择村民或礼物";
    return;
  }
  if (
    props.kind === "bundles" &&
    !vault.value &&
    ((!d.submittedEmpty && !d.submitted.length) ||
      (!d.ownedEmpty && !d.owned.length))
  ) {
    localError.value = "请填写清单，或明确勾选为空";
    return;
  }
  const args = buildArgs(props.kind, d, vault.value);
  emit("submit", current.value.name, args);
}
function example() {
  if (props.kind === "crops") {
    const crop =
      options.value.crops?.find((e: Entity) => e.id === "parsnip") ||
      options.value.crops?.[0];
    d.cropIds = crop ? [crop.id] : [];
    d.start = { year: 1, season: "spring", day: 1 };
    d.end = { year: 1, season: "spring", day: 9 };
    d.quantity = 2;
    d.budget = 40;
  } else if (props.kind === "materials") {
    d.targetId =
      options.value.targets?.find((e: Entity) => e.id === "sprinkler")?.id ||
      options.value.targets?.[0]?.id ||
      "";
    d.quantity = 2;
  } else if (props.kind === "fishing") {
    d.season = "summer";
    d.weather = "sun";
    d.location = "ocean";
    d.time = "1200";
  } else if (props.kind === "gifts") {
    d.villager =
      options.value.villagers?.find((e: Entity) => e.id === "haley")?.id ||
      options.value.villagers?.[0]?.id ||
      "";
    d.item =
      options.value.items?.find((e: Entity) => e.id === "prismatic-shard")
        ?.id || "";
  } else {
    d.bundle =
      options.value.bundles?.find(
        (e: Entity) => Number(e.attributes.gold) === 2500,
      )?.id ||
      options.value.bundles?.[0]?.id ||
      "";
    d.gold = 2400;
  }
}
async function copy() {
  try {
    await navigator.clipboard.writeText(
      JSON.stringify(buildArgs(props.kind, d, vault.value), null, 2),
    );
    localError.value = "条件已复制";
  } catch {
    localError.value = "复制失败，请检查浏览器剪贴板权限";
  }
}
</script>
<template>
  <div>
    <details class="tutorial">
      <summary>怎么使用</summary>
      <p>{{ current.tutorial }}</p>
      <p class="example-text">示例：{{ current.example }}</p>
      <button type="button" :disabled="busy || loading" @click="example">
        填入示例（不发送）
      </button>
    </details>
    <p v-if="loading">正在读取游戏资料…</p>
    <p v-if="localError" role="status" class="notice-box">{{ localError }}</p>
    <form class="tool-form" @submit.prevent="submit">
      <details class="snapshot-note">
        <summary>资料与基础规则</summary>
        <p>{{ current.tutorial }}</p>
      </details>
      <fieldset
        class="tool-fields"
        :disabled="busy || loading || !Object.keys(options).length"
      >
        <template v-if="kind === 'crops'">
          <fieldset>
            <legend>选择作物（可比较多个独立方案）</legend>
            <div class="crop-options">
              <label v-for="c in options.crops" :key="c.id" class="check"
                ><input v-model="d.cropIds" type="checkbox" :value="c.id" />{{
                  c.name
                }}</label
              >
            </div>
          </fieldset>
          <fieldset
            v-for="field in ['start', 'end'] as const"
            :key="field"
            class="date-row"
          >
            <legend>{{ field === "start" ? "开始日期" : "结束日期" }}</legend>
            <div class="form-row">
              <label
                >相对年份<input
                  v-model.number="d[field].year"
                  type="number"
                  min="1"
                  step="1"
                  required /></label
              ><label
                >季节<select v-model="d[field].season">
                  <option v-for="s in options.seasons" :key="s" :value="s">
                    {{ names[s] }}
                  </option>
                </select></label
              ><label
                >日<input
                  v-model.number="d[field].day"
                  type="number"
                  min="1"
                  max="28"
                  step="1"
                  required
              /></label>
            </div>
          </fieldset>
          <div class="form-grid">
            <label
              >种植数量<input
                v-model.number="d.quantity"
                type="number"
                min="1"
                step="1"
                required /></label
            ><label
              >预算（金币）<input
                v-model.number="d.budget"
                type="number"
                min="0"
                step="1"
                required
            /></label>
          </div>
        </template>
        <template v-if="kind === 'materials'"
          ><label
            >制作目标<select v-model="d.targetId" required>
              <option disabled value="">选择制作物</option>
              <option v-for="t in options.targets" :key="t.id" :value="t.id">
                {{ t.name }}
              </option>
            </select></label
          ><label
            >制作数量<input
              v-model.number="d.quantity"
              type="number"
              min="1"
              step="1"
              required
          /></label>
          <h3>已有材料（不填按空库存）</h3>
          <InventoryEditor
            v-model="d.ownedMaterials"
            :items="[...(options.items || []), ...(options.targets || [])]"
        /></template>
        <template v-if="kind === 'fishing'"
          ><div class="form-grid">
            <label
              >季节<select v-model="d.season">
                <option value="">不限</option>
                <option v-for="s in options.seasons" :key="s" :value="s">
                  {{ names[s] }}
                </option>
              </select></label
            ><label
              >天气<select v-model="d.weather">
                <option value="">不限</option>
                <option v-for="s in options.weather" :key="s" :value="s">
                  {{ names[s] }}
                </option>
              </select></label
            ><label
              >地点<select v-model="d.location">
                <option value="">不限</option>
                <option v-for="s in options.locations" :key="s" :value="s">
                  {{ s }}
                </option>
              </select></label
            ><label
              >时间 HHMM<input
                v-model="d.time"
                type="number"
                min="600"
                max="2600"
                step="1"
                placeholder="例如 1200" /></label
            ><label
              >鱼类<select v-model="d.fishName">
                <option value="">不限</option>
                <option v-for="f in options.fish" :key="f.id" :value="f.id">
                  {{ f.name }}
                </option>
              </select></label
            >
          </div></template
        >
        <template v-if="kind === 'gifts'"
          ><div class="form-grid">
            <label
              >村民<select v-model="d.villager">
                <option value="">不限</option>
                <option
                  v-for="v in options.villagers"
                  :key="v.id"
                  :value="v.id"
                >
                  {{ v.name }}
                </option>
              </select></label
            ><label
              >礼物<select v-model="d.item">
                <option value="">不限</option>
                <option v-for="i in options.items" :key="i.id" :value="i.id">
                  {{ i.name }}
                </option>
              </select></label
            ><label
              >偏好<select v-model="d.level">
                <option value="">全部</option>
                <option v-for="s in options.levels" :key="s" :value="s">
                  {{ names[s] }}
                </option>
              </select></label
            >
          </div></template
        >
        <template v-if="kind === 'bundles'"
          ><label
            >收集包<select v-model="d.bundle" required>
              <option disabled value="">选择收集包</option>
              <option v-for="b in options.bundles" :key="b.id" :value="b.id">
                {{ b.name }}
              </option>
            </select></label
          ><label v-if="vault"
            >可用金币<input
              v-model.number="d.gold"
              type="number"
              min="0"
              step="1"
              required /></label
          ><template v-else-if="d.bundle"
            ><h3>已提交给这个包的物品</h3>
            <label class="check"
              ><input
                v-model="d.submittedEmpty"
                type="checkbox"
              />明确没有已提交物品</label
            ><InventoryEditor
              v-if="!d.submittedEmpty"
              v-model="d.submitted"
              :items="options.items || []"
              quality />
            <h3>尚未提交的背包物品</h3>
            <label class="check"
              ><input
                v-model="d.ownedEmpty"
                type="checkbox"
              />明确背包为空</label
            ><InventoryEditor
              v-if="!d.ownedEmpty"
              v-model="d.owned"
              :items="options.items || []"
              quality /></template
        ></template>
        <div class="actions">
          <button class="primary-action" type="submit">
            {{ busy ? "正在运行…" : "运行工具" }}</button
          ><button type="button" @click="copy">复制条件</button>
        </div>
      </fieldset>
    </form>
    <p class="footnote">表单直接执行 Java 工具，不调用聊天模型。</p>
  </div>
</template>

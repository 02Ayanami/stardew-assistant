export const tools = [
  {
    id: "crops",
    name: "calculateCropProfit",
    title: "种植收益",
    icon: "crop",
    color: "green",
    description: "看收获日、投入与收益，核对种子预算。",
    example: "春季 1–9 日，防风草 2 株，预算 40 金。",
    tutorial:
      "选择作物和起止日期，再填数量与预算。普通品质、每日浇水、无肥料和职业加成；按固定数量补种，直接售卖。各作物是独立方案，收益不能相加。",
  },
  {
    id: "materials",
    name: "expandMaterialRequirements",
    title: "制作材料",
    icon: "chest",
    color: "wood",
    description: "拆解配方，按已有库存抵扣缺少的材料。",
    example: "制作 2 个洒水器，已有 1 个铜锭。",
    tutorial:
      "从目录选择已支持的制作目标。填制作数量，再逐项填已有材料；空库存按 0 抵扣。结果列出获取途径和未知前置条件，不承诺最短或全局最优时间。",
  },
  {
    id: "fishing",
    name: "findFish",
    title: "钓鱼筛选",
    icon: "fish",
    color: "blue",
    description: "按季节、天气、地点与时间找适合的鱼。",
    example: "夏季晴天，中午 12:00，在海边钓鱼。",
    tutorial:
      "筛选条件都可留空。时间按 HHMM 填写，2400–2600 代表次日午夜到 02:00。每组栖息条件独立展示；没有匹配只代表当前目录和条件下没有结果。特殊鱼饵和 Mod 未覆盖。",
  },
  {
    id: "gifts",
    name: "queryGiftPreferences",
    title: "送礼查询",
    icon: "gift",
    color: "pink",
    description: "查村民与礼物的喜好，留意个人例外。",
    example: "海莉对彩虹碎片的偏好。",
    tutorial:
      "至少选一个村民或礼物，也可以一起选择。个人例外优先；未知偏好不会默认中立。不计算好感增长，不提供未经验证的最优送礼排名。",
  },
  {
    id: "bundles",
    name: "getBundleMissing",
    title: "献祭缺项",
    icon: "bundle",
    color: "gold",
    description: "分清已提交与背包物品，查看剩余槽位。",
    example: "2,500 金收集包，可用金币 2,400。",
    tutorial:
      "选择普通收集包，分别确认已提交和未提交背包；没有物品请勾选为空。农产品品质必须明确，同一物品不会重复抵扣。任选包只需剩余槽位。金钱包仅填可用金币。切换包会清除已提交清单。",
  },
] as const;
export type ToolKind = (typeof tools)[number]["id"];
export interface InventoryRow {
  itemId: string;
  quantity: number;
  quality: string;
}
export interface ToolDraft {
  cropIds: string[];
  start: { year: number; season: string; day: number };
  end: { year: number; season: string; day: number };
  quantity: number;
  budget: number;
  targetId: string;
  ownedMaterials: { itemId: string; quantity: number }[];
  season: string;
  weather: string;
  location: string;
  time: string;
  fishName: string;
  villager: string;
  item: string;
  level: string;
  bundle: string;
  gold: number;
  submitted: InventoryRow[];
  owned: InventoryRow[];
  submittedEmpty: boolean;
  ownedEmpty: boolean;
}
export function emptyDraft(): ToolDraft {
  return {
    cropIds: [],
    start: { year: 1, season: "spring", day: NaN },
    end: { year: 1, season: "spring", day: NaN },
    quantity: NaN,
    budget: NaN,
    targetId: "",
    ownedMaterials: [],
    season: "",
    weather: "",
    location: "",
    time: "",
    fishName: "",
    villager: "",
    item: "",
    level: "",
    bundle: "",
    gold: NaN,
    submitted: [],
    owned: [],
    submittedEmpty: false,
    ownedEmpty: false,
  };
}
export const drafts: Partial<Record<ToolKind, ToolDraft>> = {};
export function buildArgs(
  kind: ToolKind,
  d: ToolDraft,
  vault = false,
): Record<string, unknown> {
  if (kind === "crops")
    return {
      cropIds: [...d.cropIds],
      start: { ...d.start },
      end: { ...d.end },
      quantity: d.quantity,
      budget: d.budget,
    };
  if (kind === "materials") {
    const owned: Record<string, number> = {};
    for (const row of d.ownedMaterials)
      owned[row.itemId] = (owned[row.itemId] || 0) + row.quantity;
    return { targetId: d.targetId, quantity: d.quantity, owned };
  }
  if (kind === "fishing")
    return Object.fromEntries(
      Object.entries({
        season: d.season,
        weather: d.weather,
        location: d.location,
        time: d.time ? Number(d.time) : undefined,
        fishName: d.fishName,
      }).filter(([, v]) => v !== "" && v !== undefined),
    );
  if (kind === "gifts")
    return Object.fromEntries(
      Object.entries({
        villager: d.villager,
        item: d.item,
        level: d.level,
      }).filter(([, v]) => v !== ""),
    );
  if (vault) return { bundle: d.bundle, gold: d.gold };
  return {
    bundle: d.bundle,
    submitted: d.submittedEmpty ? [] : d.submitted.map((r) => ({ ...r })),
    owned: d.ownedEmpty ? [] : d.owned.map((r) => ({ ...r })),
  };
}
export function validateDraft(
  kind: ToolKind,
  d: ToolDraft,
  vault = false,
): string {
  const positive = (n: number) => Number.isSafeInteger(n) && n > 0;
  const nonnegative = (n: number) => Number.isSafeInteger(n) && n >= 0;
  if (kind === "crops") {
    if (!d.cropIds.length) return "请至少选择一种作物。";
    if (!positive(d.quantity) || !nonnegative(d.budget))
      return "数量须为正整数，预算须为非负整数。";
    for (const date of [d.start, d.end])
      if (!positive(date.year) || !positive(date.day) || date.day > 28)
        return "年份须为正整数，日期为 1–28。";
    const dateValue = (date: ToolDraft["start"]) =>
      date.year * 112 +
      ["spring", "summer", "fall", "winter"].indexOf(date.season) * 28 +
      date.day;
    if (dateValue(d.end) < dateValue(d.start))
      return "结束日期不能早于开始日期。";
  }
  if (
    kind === "materials" &&
    (!d.targetId ||
      !positive(d.quantity) ||
      d.ownedMaterials.some((r) => !r.itemId || !nonnegative(r.quantity)))
  )
    return "请选择制作目标，并检查数量和库存。";
  if (kind === "fishing" && d.time) {
    const n = Number(d.time);
    if (!Number.isInteger(n) || n < 600 || n > 2600 || n % 100 >= 60)
      return "时间须为 0600–2600 的 HHMM，分钟小于 60。";
  }
  if (kind === "gifts" && !d.villager && !d.item) return "至少选择村民或礼物。";
  if (kind === "bundles") {
    if (!d.bundle) return "请选择收集包。";
    if (vault && !nonnegative(d.gold)) return "金币须为非负整数。";
    if (!vault) {
      if (
        (!d.submittedEmpty && !d.submitted.length) ||
        (!d.ownedEmpty && !d.owned.length)
      )
        return "请分别填写已提交、背包，或明确勾选为空。";
      if (
        [
          ...(d.submittedEmpty ? [] : d.submitted),
          ...(d.ownedEmpty ? [] : d.owned),
        ].some((r) => !r.itemId || !nonnegative(r.quantity) || !r.quality)
      )
        return "物品、数量和品质都需要明确。";
    }
  }
  return "";
}

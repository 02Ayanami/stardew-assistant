import { describe, it, expect } from "vitest";
import { emptyDraft, buildArgs, validateDraft } from "./tools";
describe("表单业务条件", () => {
  it("不填数量预算不能提交，作物比较不能默认全部", () => {
    const d = emptyDraft();
    expect(validateDraft("crops", d)).toContain("选择");
    d.cropIds = ["parsnip"];
    expect(validateDraft("crops", d)).toContain("数量");
  });
  it("献祭分别确认空清单，金钱包只需金币", () => {
    const d = emptyDraft();
    d.bundle = "bundle";
    expect(validateDraft("bundles", d)).toContain("明确");
    d.submittedEmpty = true;
    d.ownedEmpty = true;
    expect(buildArgs("bundles", d)).toEqual({
      bundle: "bundle",
      submitted: [],
      owned: [],
    });
    d.gold = 2400;
    expect(buildArgs("bundles", d, true)).toEqual({
      bundle: "bundle",
      gold: 2400,
    });
  });
  it("拒绝无效分钟并保留次日时间", () => {
    const d = emptyDraft();
    d.time = "1265";
    expect(validateDraft("fishing", d)).toContain("分钟");
    d.time = "2500";
    expect(validateDraft("fishing", d)).toBe("");
  });
  it("库存汇总、独立字段不串到其他工具", () => {
    const d = emptyDraft();
    d.targetId = "sprinkler";
    d.quantity = 2;
    d.ownedMaterials = [
      { itemId: "copper-bar", quantity: 1 },
      { itemId: "copper-bar", quantity: 2 },
    ];
    expect(buildArgs("materials", d)).toEqual({
      targetId: "sprinkler",
      quantity: 2,
      owned: { "copper-bar": 3 },
    });
    expect(buildArgs("fishing", d)).toEqual({});
  });
});

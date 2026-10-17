import { afterEach, describe, it, expect, vi } from "vitest";
import { api } from "./client";
afterEach(() => vi.unstubAllGlobals());
describe("同步接口", () => {
  it("提交普通请求并保留后端错误", async () => {
    const fetch = vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ message: "模型未配置" }),
    });
    vi.stubGlobal("fetch", fetch);
    await expect(api.chat("abc", "你好")).rejects.toThrow("模型未配置");
    expect(fetch.mock.calls[0][0]).toBe("/api/sessions/abc/messages");
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      message: "你好",
    });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("表单直接调用工具接口", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue({ ok: true, json: async () => ({ answer: "完成" }) });
    vi.stubGlobal("fetch", fetch);
    await api.tool("abc", "findFish", { season: "spring" });
    expect(fetch.mock.calls[0][0]).toBe("/api/sessions/abc/tools/findFish");
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      args: { season: "spring" },
    });
  });
});

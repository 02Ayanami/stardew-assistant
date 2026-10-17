import { describe, it, expect } from "vitest";
import { renderAnswer, safeSourceUrl } from "./answer";
describe("回答与来源", () => {
  it("允许普通Markdown并清除脚本、图片和危险链接", () => {
    const html = renderAnswer(
      "**收成**<script>alert(1)</script><img src=x onerror=alert(1)>[链接](javascript:alert(1))",
    );
    expect(html).toContain("<strong>收成</strong>");
    expect(html).not.toContain("<script");
    expect(html).not.toContain("<img");
    expect(html).not.toContain("javascript:");
  });
  it("来源只使用有效https地址", () => {
    expect(safeSourceUrl("https://stardewvalleywiki.com/Parsnip")).toContain(
      "https://",
    );
    expect(safeSourceUrl("javascript:alert(1)")).toBeNull();
    expect(safeSourceUrl("https://user:secret@example.com")).toBeNull();
  });
});

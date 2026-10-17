import { test, expect } from "@playwright/test";
import { mkdirSync } from "node:fs";
test("首页、同步聊天、真实工具调用和刷新历史", async ({ page }) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "今天的农场，有什么新计划？" }),
  ).toBeVisible();
  mkdirSync("../.local/screenshots", { recursive: true });
  await page.screenshot({
    path: "../.local/screenshots/home-desktop.png",
    fullPage: true,
  });
  await page.getByLabel("输入问题").fill("查作物");
  await page.getByRole("button", { name: /^发送/ }).click();
  await expect(page.getByText("已根据实际工具结果整理回答")).toBeVisible();
  await page.getByRole("button", { name: "来源", exact: true }).click();
  await expect(page.locator(".detail-panel .source-row").first()).toBeVisible();
  await page.getByRole("button", { name: "关闭详情" }).click();
  await page.reload();
  await expect(page.locator(".message.assistant")).toHaveCount(1);
  await page.getByRole("button", { name: "新对话", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "今天的农场，有什么新计划？" }),
  ).toBeVisible();
});
test("五类表单可执行、示例仅填充、工具结果可讨论", async ({ page }) => {
  await page.goto("/");
  for (const [title, assertion] of [
    ["种植收益", "种植方案比较"],
    ["制作材料", "材料清单"],
    ["钓鱼筛选", "钓鱼条件匹配"],
    ["送礼查询", "送礼偏好"],
    ["献祭缺项", "当前还缺 100g"],
  ] as const) {
    await page.getByTitle(title, { exact: true }).click();
    await expect(page.locator(".app-header > span")).toHaveText(title);
    await page.locator("summary").filter({ hasText: "怎么使用" }).click();
    await expect(
      page.getByRole("button", { name: "填入示例（不发送）" }),
    ).toBeEnabled();
    await page.getByRole("button", { name: "填入示例（不发送）" }).click();
    await page.getByRole("button", { name: "运行工具" }).click();
    await expect(
      page.getByText(assertion, { exact: false }).last(),
    ).toBeVisible();
  }
  await page.screenshot({
    path: "../.local/screenshots/tools-desktop.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "继续讨论" }).last().click();
  await expect(page.getByLabel("输入问题")).toBeVisible();
  await page.reload();
  await expect(page.getByLabel("输入问题")).toBeEnabled();
  await expect(page).toHaveURL(/\/$/);
});
test("移动端无横向溢出", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "今天的农场，有什么新计划？" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "展开导航" }).click();
  await page.getByRole("button", { name: "历史对话", exact: true }).click();
  await expect(page.getByRole("button", { name: "关闭历史" })).toBeVisible();
  await page.getByRole("button", { name: "关闭历史" }).click();
  await page.screenshot({
    path: "../.local/screenshots/home-mobile.png",
    fullPage: true,
  });
});

test("失败保留输入，重试后才保存完整消息", async ({ page }) => {
  await page.goto("/");
  await page.route("**/api/sessions/*/messages", (route) =>
    route.fulfill({
      status: 502,
      contentType: "application/json",
      body: JSON.stringify({
        code: "MODEL_FAILED",
        message: "测试模型暂不可用",
      }),
    }),
  );
  const question = page.getByLabel("输入问题");
  await question.fill("你好，农场主");
  await page.getByRole("button", { name: /^发送/ }).click();
  await expect(page.getByRole("alert")).toContainText("测试模型暂不可用");
  await expect(question).toHaveValue("你好，农场主");
  await expect(page.locator(".message")).toHaveCount(0);
  await page.unroute("**/api/sessions/*/messages");
  await page.getByRole("button", { name: /^发送/ }).click();
  await expect(page.locator(".message.assistant")).toHaveCount(1);
  await expect(question).toHaveValue("");
});

test("工具地址可直接打开、刷新和前后退", async ({ page }) => {
  await page.goto("/tools/crops");
  await expect(page.getByRole("button", { name: "运行工具" })).toBeEnabled();
  await expect(page.locator(".app-header > span")).toHaveText("种植收益");
  await page.getByTitle("制作材料", { exact: true }).click();
  await expect(page).toHaveURL(/\/tools\/materials$/);
  await page.reload();
  await expect(page.getByRole("button", { name: "运行工具" })).toBeEnabled();
  await expect(page.locator(".app-header > span")).toHaveText("制作材料");
  await page.goBack();
  await expect(page.locator(".app-header > span")).toHaveText("种植收益");
  await page.goForward();
  await expect(page.locator(".app-header > span")).toHaveText("制作材料");
});

test("提交已成功而历史读取失败时仍显示答案且不重复提交", async ({ page }) => {
  await page.goto("/");
  const question = page.getByLabel("输入问题");
  await expect(question).toBeEnabled();
  await page.route("**/api/sessions/*", (route) => {
    if (route.request().method() === "GET")
      return route.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({ message: "历史暂不可用" }),
      });
    return route.continue();
  });
  let submissions = 0;
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().endsWith("/messages"))
      submissions++;
  });
  await question.fill("你好，农场主");
  await page.getByRole("button", { name: /^发送/ }).click();
  await expect(page.locator(".message.assistant")).toHaveCount(1);
  await expect(
    page.getByText("结果已保存，历史列表暂未同步。可稍后刷新页面查看。"),
  ).toBeVisible();
  await expect(question).toHaveValue("");
  expect(submissions).toBe(1);
  await page.unroute("**/api/sessions/*");
  await page.reload();
  await expect(page.locator(".message.assistant")).toHaveCount(1);
});

test("代理返回非 JSON 错误时保留输入并给出明确提示", async ({ page }) => {
  await page.goto("/");
  await page.route("**/api/sessions/*/messages", (route) =>
    route.fulfill({
      status: 502,
      contentType: "text/html",
      body: "<h1>Bad Gateway</h1>",
    }),
  );
  const question = page.getByLabel("输入问题");
  await question.fill("你好，农场主");
  await page.getByRole("button", { name: /^发送/ }).click();
  await expect(page.getByRole("alert")).toContainText(
    "服务暂不可用（HTTP 502）",
  );
  await expect(question).toHaveValue("你好，农场主");
  await expect(page.locator(".message")).toHaveCount(0);
});

test("读取会话期间禁止提交，避免消息落入上一个会话", async ({ page }) => {
  const response = await page.request.post("/api/sessions", { data: {} });
  const session = await response.json();
  await page.request.post(`/api/sessions/${session.id}/messages`, {
    data: { message: "会话切换检查" },
  });
  await page.goto("/");
  await expect(page.getByLabel("输入问题")).toBeEnabled();
  let release!: () => void;
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(`**/api/sessions/${session.id}`, async (route) => {
    await pending;
    await route.continue();
  });
  await page.getByTitle("会话切换检查", { exact: true }).click();
  await expect(page.getByLabel("输入问题")).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "新对话", exact: true }),
  ).toBeDisabled();
  release();
  await expect(page.locator(".app-header > span")).toHaveText("会话切换检查");
  await expect(page.getByLabel("输入问题")).toBeEnabled();
});

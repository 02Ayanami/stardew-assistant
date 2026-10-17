import type { Session, Message, Turn, Options } from "./types";
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 150000);
  try {
    const response = await fetch("/api" + path, {
      ...init,
      signal: controller.signal,
    });
    let body: any;
    try {
      body = await response.json();
    } catch {
      throw new Error(
        response.ok
          ? "服务返回了无法读取的结果，请查看历史确认。"
          : `服务暂不可用（HTTP ${response.status}），请稍后重试。`,
      );
    }
    if (!response.ok) throw new Error(body?.message || "服务请求失败");
    return body;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError")
      throw new Error("等待超时。请查看历史确认结果，再决定是否重新提交。");
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
const post = <T>(path: string, body: unknown) =>
  request<T>(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
export const api = {
  create: () => post<Session>("/sessions", {}),
  history: (page = 1) =>
    request<{ items: Session[] }>("/sessions?page=" + page),
  session: (id: string) =>
    request<{ session: Session; messages: Message[] }>(
      "/sessions/" + encodeURIComponent(id),
    ),
  chat: (id: string, message: string) =>
    post<Turn>("/sessions/" + encodeURIComponent(id) + "/messages", {
      message,
    }),
  tool: (id: string, name: string, args: Record<string, unknown>) =>
    post<Turn>(
      "/sessions/" +
        encodeURIComponent(id) +
        "/tools/" +
        encodeURIComponent(name),
      { args },
    ),
  options: (name: string) =>
    request<Options>("/tool-options?tool=" + encodeURIComponent(name)),
  status: () =>
    request<{ data: Record<string, unknown>; modelConfigured: boolean }>(
      "/status",
    ),
};

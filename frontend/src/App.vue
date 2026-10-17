<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, nextTick } from "vue";
import { api } from "./api/client";
import type { Session, Message, Turn } from "./api/types";
import { tools, type ToolKind } from "./tools";
import PixelIcon from "./components/PixelIcon.vue";
import FarmWelcomeScene from "./components/FarmWelcomeScene.vue";
import MessageComposer from "./components/MessageComposer.vue";
import ToolForm from "./components/ToolForm.vue";
import ToolResults from "./components/ToolResults.vue";
import AnswerRenderer from "./components/AnswerRenderer.vue";
import { safeSourceUrl } from "./components/answer";
const showHistory = ref(false),
  mobileNav = ref(false),
  sidebarVisible = ref(true),
  desktop = ref(window.innerWidth >= 1100),
  wide = ref(window.innerWidth >= 1280);
const detail = ref<Message | null>(null),
  detailTab = ref<"sources" | "results" | "execution">("results");
const tooltip = ref<{ label: string; top: number } | null>(null);
const pinnedHistory = computed(
  () => desktop.value && sidebarVisible.value && !detail.value,
);
const modalHistory = computed(() => showHistory.value && !pinnedHistory.value);
function resize() {
  desktop.value = window.innerWidth >= 1100;
  wide.value = window.innerWidth >= 1280;
  document.documentElement.style.setProperty(
    "--app-height",
    `${window.innerHeight}px`,
  );
}
function closePanels() {
  showHistory.value = false;
  mobileNav.value = false;
  detail.value = null;
  tooltip.value = null;
}
function toggleHistory() {
  if (desktop.value) {
    detail.value = null;
    sidebarVisible.value = !sidebarVisible.value;
  } else {
    mobileNav.value = false;
    showHistory.value = !showHistory.value;
  }
}
function hideHistory() {
  if (desktop.value) sidebarVisible.value = false;
  showHistory.value = false;
}
type Page = "chat" | "help" | ToolKind;
function pageFromPath(): Page {
  if (window.location.pathname === "/help") return "help";
  return (
    tools.find((t) => window.location.pathname === "/tools/" + t.id)?.id ||
    "chat"
  );
}
function pagePath(page: Page) {
  return page === "chat" ? "/" : page === "help" ? "/help" : "/tools/" + page;
}
function syncPath(replace = false) {
  const path = pagePath(mode.value);
  if (window.location.pathname !== path)
    window.history[replace ? "replaceState" : "pushState"]({}, "", path);
}
function backOrForward() {
  if (busy.value) {
    syncPath(true);
    return;
  }
  mode.value = pageFromPath();
  closePanels();
}
function go(next: Page) {
  if (busy.value) return;
  mode.value = next;
  syncPath();
  closePanels();
}
function openDetail(
  message: Message,
  tab: "sources" | "results" | "execution",
) {
  detail.value = message;
  detailTab.value = tab;
  showHistory.value = false;
}
function showLabel(event: Event) {
  if (mobileNav.value) return;
  const el = (event.target as Element).closest<HTMLElement>(
    "a[aria-label],button[aria-label]",
  );
  if (el && el !== event.currentTarget)
    tooltip.value = {
      label: el.getAttribute("aria-label")!,
      top: el.getBoundingClientRect().top + 12,
    };
}
function keyboard(event: KeyboardEvent) {
  if (event.key === "Escape") closePanels();
  if (event.key !== "Tab") return;
  const dialog = document.querySelector<HTMLElement>('[role="dialog"]');
  if (
    !dialog ||
    dialog.getAttribute("aria-modal") === "false" ||
    !dialog.contains(event.target as Node)
  )
    return;
  const nodes = [
    ...dialog.querySelectorAll<HTMLElement>(
      'button,a[href],input,select,textarea,[tabindex="0"]',
    ),
  ].filter((el) => el.getClientRects().length && !el.hasAttribute("disabled"));
  const first = nodes[0],
    last = nodes.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
onMounted(() => {
  resize();
  window.addEventListener("resize", resize);
  window.addEventListener("popstate", backOrForward);
});
onUnmounted(() => {
  window.removeEventListener("resize", resize);
  window.removeEventListener("popstate", backOrForward);
});
const mode = ref<Page>(pageFromPath()),
  sessionId = ref(""),
  history = ref<Session[]>([]),
  messages = ref<Message[]>([]),
  draft = ref(""),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  historyPage = ref(1),
  hasMore = ref(false),
  status = ref<{ data: Record<string, unknown>; modelConfigured: boolean }>();
const selected = computed(() => tools.find((t) => t.id === mode.value));
const ready = computed(() => status.value?.data.status === "READY");
const title = computed(() =>
  mode.value === "chat"
    ? history.value.find((h) => h.id === sessionId.value)?.title || "新对话"
    : mode.value === "help"
      ? "帮助"
      : selected.value?.title || "星露谷助手",
);
const toolMessage = computed(() =>
  [...messages.value]
    .reverse()
    .find(
      (m) =>
        m.role === "assistant" &&
        m.toolResults?.some((t) => t.name === selected.value?.name),
    ),
);
const toolExecutions = computed(
  () =>
    toolMessage.value?.toolResults.filter(
      (t) => t.name === selected.value?.name,
    ) || [],
);

async function loadHistory(reset = true) {
  if (reset) historyPage.value = 1;
  const result = await api.history(historyPage.value);
  history.value = reset ? result.items : [...history.value, ...result.items];
  hasMore.value = result.items.length === 20;
}
async function more() {
  historyPage.value++;
  try {
    await loadHistory(false);
  } catch (e) {
    error.value = (e as Error).message;
    historyPage.value--;
  }
}
function fresh() {
  closePanels();
  if (busy.value) return;
  sessionId.value = "";
  messages.value = [];
  error.value = "";
  mode.value = "chat";
  syncPath();
  notice.value = "";
  draft.value = "";
  localStorage.removeItem("stardew-simple-session");
}
async function open(id: string) {
  if (busy.value) return;
  closePanels();
  busy.value = true;
  try {
    const result = await api.session(id);
    sessionId.value = id;
    messages.value = result.messages;
    const lastUser = [...result.messages]
      .reverse()
      .find((m) => m.role === "user");
    const lastAssistant = [...result.messages]
      .reverse()
      .find((m) => m.role === "assistant");
    mode.value = lastUser?.content.startsWith("表单：")
      ? tools.find((t) =>
          lastAssistant?.toolResults.some((e) => e.name === t.name),
        )?.id || "chat"
      : "chat";
    syncPath(true);
    localStorage.setItem("stardew-simple-session", id);
    notice.value = "";
    draft.value = "";
    error.value = "";
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function ensureSession() {
  if (!sessionId.value) {
    const s = await api.create();
    sessionId.value = s.id;
    localStorage.setItem("stardew-simple-session", s.id);
  }
  return sessionId.value;
}
async function showTurn(turn: Turn, userText: string) {
  // POST 已确认保存成功，先展示其结果；后续 GET 失败不能抹掉已完成的一轮。
  messages.value.push(
    {
      id: -Date.now(),
      role: "user",
      content: userText,
      toolResults: [],
      sources: [],
    },
    {
      id: turn.messageId,
      role: "assistant",
      content: turn.answer,
      toolResults: turn.toolResults,
      sources: turn.sources,
    },
  );
  try {
    const result = await api.session(turn.sessionId);
    messages.value = result.messages;
    await loadHistory();
  } catch {
    notice.value = "结果已保存，历史列表暂未同步。可稍后刷新页面查看。";
  }
  await nextTick();
  document
    .querySelector("#messages-end")
    ?.scrollIntoView({ behavior: "smooth" });
}
async function send() {
  if (busy.value || !draft.value.trim()) return;
  busy.value = true;
  notice.value = "";
  error.value = "";
  const text = draft.value;
  try {
    const id = await ensureSession();
    const turn = await api.chat(id, text);
    draft.value = "";
    await showTurn(turn, text);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function tool(name: string, args: Record<string, unknown>) {
  if (busy.value) return;
  busy.value = true;
  notice.value = "";
  error.value = "";
  try {
    const id = await ensureSession();
    const turn = await api.tool(id, name, args);
    await showTurn(turn, "表单：" + JSON.stringify({ tool: name, args }));
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function copy(message: Message) {
  try {
    await navigator.clipboard.writeText(
      message.content + "\n" + JSON.stringify(message.toolResults, null, 2),
    );
    notice.value = "结果已复制";
  } catch {
    error.value = "复制失败，请选择正文复制";
  }
}
function discuss() {
  go("chat");
  nextTick(() =>
    document.querySelector<HTMLTextAreaElement>("#question")?.focus(),
  );
}
onMounted(async () => {
  const requestedPage = pageFromPath();
  busy.value = true;
  const results = await Promise.allSettled([
    api.status().then((value) => {
      status.value = value;
    }),
    loadHistory(),
  ]);
  for (const result of results)
    if (result.status === "rejected")
      error.value = (result.reason as Error).message;
  busy.value = false;
  const saved = localStorage.getItem("stardew-simple-session");
  if (saved) await open(saved);
  mode.value = requestedPage;
  syncPath(true);
});
</script>

<template>
  <div
    class="app-shell"
    :class="{ 'has-sidebar': pinnedHistory, 'has-detail': !!detail }"
    @keydown="keyboard"
  >
    <button
      class="mobile-nav-toggle icon-button"
      aria-label="展开导航"
      :aria-expanded="mobileNav"
      @click="
        closePanels();
        mobileNav = true;
      "
    >
      ☰
    </button>
    <aside
      class="icon-rail"
      :class="{ visible: mobileNav }"
      :role="mobileNav ? 'dialog' : undefined"
      :aria-modal="mobileNav ? true : undefined"
      aria-label="导航"
      @pointerover="showLabel"
      @focusin="showLabel"
      @pointerleave="tooltip = null"
      @focusout="tooltip = null"
    >
      <button
        class="mobile-nav-close"
        aria-label="关闭导航"
        @click="closePanels"
      >
        关闭 ×
      </button>
      <button
        v-if="mobileNav"
        class="sidebar-new-chat"
        @click="fresh"
        :disabled="busy"
      >
        新对话
      </button>
      <nav aria-label="主导航">
        <a
          href="/"
          aria-label="综合聊天"
          title="综合聊天"
          :class="{ 'router-link-exact-active': mode === 'chat' }"
          @click.prevent="go('chat')"
          ><PixelIcon kind="stardrop" :size="28" /><span>综合聊天</span></a
        >
        <a
          v-for="t in tools"
          :key="t.id"
          :href="'/tools/' + t.id"
          :aria-label="t.title"
          :title="t.title"
          :class="{ 'router-link-exact-active': mode === t.id }"
          @click.prevent="go(t.id)"
          ><PixelIcon :kind="t.icon as any" :size="28" /><span>{{
            t.title
          }}</span></a
        >
      </nav>
      <div class="rail-divider"></div>
      <button
        class="rail-action"
        aria-label="历史对话"
        title="历史对话"
        :aria-expanded="modalHistory || pinnedHistory"
        @click="toggleHistory"
      >
        <svg
          width="25"
          height="25"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="8.5" />
          <path d="M12 7v5l-3 2" /></svg
        ><span>历史对话</span>
      </button>
      <a
        href="/help"
        class="rail-action rail-help"
        aria-label="使用帮助"
        title="使用帮助"
        @click.prevent="go('help')"
        >?<span>使用帮助</span></a
      >
    </aside>
    <div
      v-if="tooltip && !mobileNav"
      class="rail-tooltip"
      :style="{ top: tooltip.top + 'px' }"
      role="tooltip"
    >
      {{ tooltip.label }}
    </div>
    <main
      class="app-main"
      :inert="modalHistory || mobileNav || (!!detail && !wide)"
    >
      <header class="app-header">
        <span :title="title">{{ title }}</span
        ><button
          v-if="!pinnedHistory && (mode !== 'chat' || messages.length)"
          class="new-chat-button"
          aria-label="新对话"
          @click="fresh"
          :disabled="busy"
        >
          新对话
        </button>
      </header>
      <section
        v-if="mode === 'chat'"
        class="conversation-page"
        :class="{ 'is-empty': !messages.length }"
      >
        <div class="conversation-scroll">
          <div v-if="!messages.length" class="empty-brand">
            <FarmWelcomeScene />
            <h1>今天的农场，有什么新计划？</h1>
            <p class="welcome-letter">聊聊种植、村民，或你的下一场冒险。</p>
          </div>
          <section class="messages" aria-live="polite">
            <article
              v-for="m in messages"
              :key="m.id"
              class="message"
              :class="m.role"
            >
              <p v-if="m.role === 'user'" class="user-text">
                {{
                  m.content.startsWith("表单：") ? "提交了工具查询" : m.content
                }}
              </p>
              <template v-else
                ><div class="message-label">
                  <span class="avatar">✦</span><span>星露谷助手</span>
                </div>
                <AnswerRenderer :text="m.content" />
                <div class="message-actions">
                  <button @click="copy(m)">复制</button
                  ><button
                    v-if="m.sources?.length"
                    @click="openDetail(m, 'sources')"
                  >
                    来源</button
                  ><button
                    v-if="m.toolResults?.length"
                    @click="openDetail(m, 'results')"
                  >
                    查看结果</button
                  ><button
                    v-if="m.toolResults?.length"
                    @click="openDetail(m, 'execution')"
                  >
                    执行记录
                  </button>
                </div>
              </template>
            </article>
            <p v-if="busy" class="thinking" role="status">正在查阅和整理…</p>
            <div id="messages-end"></div>
          </section>
          <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
          <p v-if="notice" class="footnote" role="status">{{ notice }}</p>
        </div>
        <div class="conversation-composer">
          <MessageComposer v-model="draft" :busy="busy" @send="send" />
        </div>
      </section>
      <section v-else-if="mode === 'help'" class="page-scroll">
        <div class="help-page">
          <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
          <h1>使用帮助</h1>
          <section class="result-card">
            <h2>综合聊天</h2>
            <p>
              直接提问，助手会按需要查询资料或调用工具。日期、数量、预算等条件不明确时，请补充后继续。
            </p>
          </section>
          <section class="result-card">
            <h2>五个工具入口</h2>
            <p v-for="t in tools" :key="t.id">
              <strong>{{ t.title }}</strong
              >：{{ t.description }}
            </p>
            <p>
              填入示例只填写条件，点击运行工具后才执行。工具结果可以继续讨论，也可以复制条件。
            </p>
          </section>
          <section class="result-card">
            <h2>资料与来源</h2>
            <p>
              工具覆盖当前已收录资料，回答中的来源可单独查看。没有匹配不代表游戏中不存在；玩家经验与游戏规则分别标注。
            </p>
            <p>
              {{ ready ? "游戏资料已就绪" : "请先导入游戏资料" }} ·
              {{ status?.modelConfigured ? "模型已配置" : "模型尚未配置" }}
            </p>
          </section>
        </div>
      </section>
      <section v-else class="tool-page">
        <header class="page-header">
          <PixelIcon :kind="selected!.icon as any" :size="28" />
          <p class="tool-description">{{ selected?.description }}</p>
        </header>
        <div class="tool-columns">
          <div class="tool-form-scroll">
            <KeepAlive
              ><ToolForm
                :key="mode"
                :kind="mode"
                :busy="busy"
                @submit="tool"
                @error="error = $event"
            /></KeepAlive>
          </div>
          <div class="tool-result-scroll" aria-label="工具结果">
            <h2>结果</h2>
            <p v-if="notice" class="footnote" role="status">{{ notice }}</p>
            <p v-if="busy" role="status" class="run-status">正在查阅和整理…</p>
            <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
            <template v-if="toolMessage"
              ><ToolResults :executions="toolExecutions" />
              <div class="result-actions">
                <button
                  v-if="toolMessage.sources?.length"
                  @click="openDetail(toolMessage, 'sources')"
                >
                  来源</button
                ><button @click="openDetail(toolMessage, 'execution')">
                  执行记录</button
                ><button @click="discuss" :disabled="busy">继续讨论</button
                ><button @click="copy(toolMessage)">复制结果</button>
              </div>
              <p>继续讨论仅打开聊天；发送消息后才调用模型。</p></template
            >
            <div v-else class="empty-notebook"><p>运行后显示结果</p></div>
          </div>
        </div>
      </section>
    </main>
    <button
      v-if="modalHistory || mobileNav || detail"
      class="panel-backdrop"
      :class="{ 'detail-backdrop': !!detail }"
      aria-label="关闭面板"
      @click="closePanels"
    ></button>
    <section
      v-if="modalHistory || pinnedHistory"
      class="history-panel"
      :class="{ pinned: pinnedHistory }"
      :role="pinnedHistory ? 'complementary' : 'dialog'"
      :aria-modal="pinnedHistory ? undefined : true"
      aria-label="历史对话"
    >
      <header>
        <div class="sidebar-brand">
          <PixelIcon kind="stardrop" :size="24" />星露谷助手
        </div>
        <button class="icon-button" aria-label="关闭历史" @click="hideHistory">
          ‹
        </button>
      </header>
      <button
        v-if="pinnedHistory"
        class="sidebar-new-chat"
        aria-label="新对话"
        @click="fresh"
        :disabled="busy"
      >
        <span aria-hidden="true">✎</span> 新对话
      </button>
      <p class="history-label">最近对话</p>
      <nav class="server-history" aria-label="服务端历史">
        <a
          v-for="h in history"
          :key="h.id"
          href="/"
          :title="h.title"
          :class="{ 'is-current': h.id === sessionId }"
          @click.prevent="open(h.id)"
          >{{ h.title }}</a
        >
        <p v-if="!history.length" class="muted">暂无已提交的对话</p>
        <button v-if="hasMore" @click="more" :disabled="busy">加载更多</button>
      </nav>
    </section>
    <section
      v-if="detail"
      class="detail-panel"
      :role="wide ? 'complementary' : 'dialog'"
      :aria-modal="wide ? undefined : true"
      aria-label="消息详情"
    >
      <header>
        <h2>消息详情</h2>
        <button
          class="icon-button"
          aria-label="关闭详情"
          @click="detail = null"
        >
          ×
        </button>
      </header>
      <div class="detail-tabs" role="tablist">
        <button
          v-for="(label, tab) in {
            results: '结果',
            sources: '来源',
            execution: '执行记录',
          }"
          :key="tab"
          role="tab"
          :aria-selected="detailTab === tab"
          @click="detailTab = tab"
        >
          {{ label }}
        </button>
      </div>
      <div class="detail-scroll">
        <template v-if="detailTab === 'results'"
          ><ToolResults :executions="detail.toolResults || []" />
          <p v-if="!detail.toolResults?.length" class="muted">
            暂无工具结果
          </p></template
        >
        <div v-else-if="detailTab === 'sources'" class="sources">
          <p v-if="!detail.sources?.length" class="muted">
            本次回答没有本地来源。
          </p>
          <article
            v-for="s in detail.sources"
            :key="s.sourceId + ':' + s.url"
            class="source-row"
          >
            <a
              v-if="safeSourceUrl(s.url)"
              :href="safeSourceUrl(s.url)!"
              target="_blank"
              rel="noopener noreferrer"
              >{{ s.title }}</a
            ><span v-else>{{ s.title }}</span
            ><small
              >{{ s.sourceType === "PLAYER_GUIDE" ? "玩家经验" : "规则资料" }} ·
              {{ s.gameVersion }}</small
            >
          </article>
        </div>
        <template v-else
          ><p v-if="!detail.toolResults?.length" class="muted">
            本次没有执行工具。
          </p>
          <article
            v-for="(t, index) in detail.toolResults"
            :key="index"
            class="trace-item"
          >
            <strong>{{ t.name }} · {{ t.result.status }}</strong>
            <pre class="trace-json">{{
              JSON.stringify({ args: t.args, result: t.result }, null, 2)
            }}</pre>
          </article></template
        >
      </div>
    </section>
  </div>
</template>

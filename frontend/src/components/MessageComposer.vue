<script setup lang="ts">
defineProps<{ busy: boolean }>();
const emit = defineEmits<{ send: [] }>();
const text = defineModel<string>({ default: "" });
function key(event: KeyboardEvent, busy: boolean) {
  if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    if (!busy && text.value.trim()) emit("send");
  }
}
</script>
<template>
  <div class="composer">
    <textarea
      id="question"
      v-model="text"
      aria-label="输入问题"
      placeholder="问一个问题，或告诉我想完成的目标…"
      maxlength="8000"
      rows="2"
      :disabled="busy"
      @keydown="key($event, busy)"
    ></textarea>
    <div class="composer-bottom">
      <small>Enter 发送 · Shift + Enter 换行</small
      ><button
        class="send-button"
        :disabled="busy || !text.trim()"
        @click="emit('send')"
      >
        {{ busy ? "正在发送…" : "发送" }}
        <span v-if="!busy" aria-hidden="true">↑</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { renderAnswer } from "./answer";
const props = defineProps<{ text: string }>();
const html = computed(() => renderAnswer(props.text));
</script>
<template><div class="markdown" v-html="html"></div></template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    kind:
      | "stardrop"
      | "book"
      | "crop"
      | "chest"
      | "junimo"
      | "chicken"
      | "fish"
      | "gift"
      | "bundle";
    size?: number;
  }>(),
  { size: 32 },
);
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    shape-rendering="crispEdges"
    aria-hidden="true"
    class="pixel-icon"
  >
    <g v-if="kind === 'fish'">
      <path d="M5 12h4V8h13v4h5v8h-5v4H9v-4H5l-4 5V7Z" fill="#467b9b" />
      <path d="M9 12h13v8H9Z" fill="#8ed7d4" />
      <path d="M20 12h3v3h-3Z" fill="#243745" />
    </g>
    <g v-else-if="kind === 'gift'">
      <path d="M4 10h24v19H4Z" fill="#a7638b" />
      <path d="M6 12h20v15H6Z" fill="#e9a6be" />
      <path
        d="M14 9h4v20h-4ZM3 10h26v4H3ZM8 3h6v7H8ZM18 3h6v7h-6Z"
        fill="#f8d98b"
      />
    </g>
    <g v-else-if="kind === 'bundle'">
      <path d="M8 3h16v5h4v21H4V8h4Z" fill="#997044" />
      <path d="M6 10h20v17H6Z" fill="#e7c570" />
      <path d="M14 12h4v3h4v4h-4v4h-4v-4h-4v-4h4Z" fill="#a56c2d" />
    </g>
    <g v-else-if="kind === 'stardrop'">
      <path
        d="M14 2h4v6h4v4h8v6h-6v4h-4v8h-8v-8H8v-4H2v-6h8V8h4Z"
        fill="#4e2d67"
      />
      <path
        d="M14 5h4v6h5v3h4v3h-6v4h-4v6h-3v-6h-4v-4H5v-3h6v-3h3Z"
        fill="#bb7acb"
      />
      <path d="M14 8h3v6h5v3h-5v7h-3v-7H9v-3h5Z" fill="#e4b5e9" />
    </g>
    <g v-else-if="kind === 'book'">
      <path d="M5 5h21v24H5V5H3v23h2" fill="#563a27" />
      <path d="M6 4h18v22H6Z" fill="#a65f36" />
      <path d="M9 5h13v18H9Z" fill="#c98948" />
      <path d="M11 8h9v2h-9zm0 5h9v2h-9zm0 5h6v2h-6z" fill="#f3dba0" />
      <path d="M6 26h18v2H6Z" fill="#e4c18b" />
      <path d="M6 5h2v19H6Z" fill="#efb965" />
    </g>
    <g v-else-if="kind === 'crop'">
      <path d="M15 13h4v12h-4v4h-4v-8h-3v-6h4v-3h3Z" fill="#6d4b2a" />
      <path d="M13 13h5v10h-3v4h-3v-6h-3v-5h4Z" fill="#f1d286" />
      <path d="M15 14h2v8h-2Z" fill="#fff0b2" />
      <path
        d="M15 12V6h3v6h4V7h4v6h-8v2h-3v-3h-5V7H6V4h8v4h3v4Z"
        fill="#43653c"
      />
      <path d="M8 5h5v4h3v3h-3V9H8zm12 4h4v3h-4Z" fill="#84a650" />
    </g>
    <g v-else-if="kind === 'chest'">
      <path d="M5 9h22v3h3v16H2V12h3Z" fill="#633d25" />
      <path d="M6 7h20v3H6Z" fill="#704324" />
      <path d="M6 10h20v3h2v5H4v-5h2Z" fill="#ce914a" />
      <path d="M4 19h24v7H4Z" fill="#ad6935" />
      <path
        d="M6 12h4v4H6zm16 0h4v4h-4zm-16 8h4v5H6zm16 0h4v5h-4Z"
        fill="#e8b767"
      />
      <path d="M13 15h6v8h-6Z" fill="#623a26" />
      <path d="M14 16h4v5h-4Z" fill="#e2c45d" />
      <path d="M15 18h2v2h-2Z" fill="#835d2a" />
    </g>
    <g v-else-if="kind === 'junimo'">
      <path d="M15 4h3v8h-3Z" fill="#44652b" />
      <path d="M17 3h7v3h-7zm-2 4H9V4h6Z" fill="#91bd4e" />
      <path d="M8 11h17v3h3v13h-3v3H7v-3H4V15h4Z" fill="#466b2f" />
      <path d="M9 12h14v3h3v10h-3v3H8v-3H6V16h3Z" fill="#8bbd47" />
      <path d="M10 14h11v2H10Z" fill="#c3dc72" />
      <path d="M10 19h3v4h-3zm10 0h3v4h-3Z" fill="#354127" />
      <path d="M15 24h4v2h-4Z" fill="#d68b63" />
      <path d="M4 20H1v3h3zm24 0h3v3h-3" fill="#86ad44" />
    </g>
    <g v-else-if="kind === 'chicken'">
      <path d="M12 4h3v5h-3zm4 1h3v4h-3Z" fill="#bb553d" />
      <path d="M11 9h11v3h3v10h-3v4H7v-3H4v-7h4v-4h3Z" fill="#715640" />
      <path d="M12 10h9v3h2v8h-3v3H8v-3H6v-4h4v-4h2Z" fill="#fff5d7" />
      <path d="M9 17h5v4H9Z" fill="#e5d5b1" />
      <path d="M19 13h2v2h-2Z" fill="#443b2c" />
      <path d="M23 15h5v3h-5Z" fill="#daa64b" />
      <path d="M11 26h2v4h-5v-2h3zm7 0h2v4h-5v-2h3Z" fill="#c8913e" />
    </g>
  </svg>
</template>

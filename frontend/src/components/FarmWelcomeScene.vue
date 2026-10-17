<script setup lang="ts">
import PixelIcon from "./PixelIcon.vue";
</script>
<template>
  <div class="farm-welcome-scene" aria-hidden="true">
    <div class="scene-caption">
      <span class="scene-dot"></span> 来自星露谷的一封信
      <span class="scene-dot"></span>
    </div>
    <div class="scene-window">
      <span class="scene-corner top-left"></span
      ><span class="scene-corner top-right"></span>
      <img src="/art/valley-farm.png" alt="" />
      <div class="scene-window-shade"></div>
      <span class="scene-corner bottom-left"></span
      ><span class="scene-corner bottom-right"></span>
    </div>
    <div class="scene-garden">
      <svg
        class="garden-fence"
        viewBox="0 0 600 42"
        preserveAspectRatio="none"
        shape-rendering="crispEdges"
      >
        <path d="M0 17h600v5H0zm0 14h600v4H0" fill="#bb8d51" />
        <path
          d="M12 8h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8zm142 0h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8zm43 0h8v34h-8"
          fill="#d4b073"
        />
        <path
          d="M12 8h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8zm142 0h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8zm43 0h8v3h-8"
          fill="#f5d69e"
        />
      </svg>
      <span class="garden-crops"
        ><PixelIcon kind="crop" :size="32" /><PixelIcon kind="crop" :size="26"
      /></span>
      <span class="garden-host"><PixelIcon kind="junimo" :size="52" /></span>
      <span class="garden-chicken"
        ><PixelIcon kind="chicken" :size="38"
      /></span>
      <svg
        class="garden-flowers"
        width="45"
        height="36"
        viewBox="0 0 45 36"
        shape-rendering="crispEdges"
      >
        <path
          d="M10 17h3v19h-3zm20-4h3v23h-3zM3 24h8v4H3zm10 3h7v4h-7zm11-9h8v4h-8zm9 6h8v4h-8"
          fill="#739444"
        />
        <path d="M8 6h7v14H8zM4 10h15v7H4z" fill="#c67c8d" />
        <path d="M8 10h7v7H8z" fill="#f7d585" />
        <path d="M28 2h7v14h-7zm-4 4h15v7H24z" fill="#cca764" />
        <path d="M28 6h7v7h-7z" fill="#fff0a4" />
      </svg>
    </div>
    <span class="scene-leaf leaf-left"
      ><svg
        width="40"
        height="60"
        viewBox="0 0 40 60"
        shape-rendering="crispEdges"
      >
        <path
          d="M29 50h3V17h-3ZM18 24h11v4H18ZM12 19h10v5H12ZM8 13h10v7H8ZM24 36h7v4h-7ZM14 29h12v8H14ZM29 14h8v9h-8Z"
          fill="#809555"
        />
        <path
          d="M10 14h8v3h-8zm6 16h8v3h-8zm15-15h5v3h-5z"
          fill="#afbf78"
        /></svg
    ></span>
    <span class="scene-leaf leaf-right"
      ><svg
        width="40"
        height="60"
        viewBox="0 0 40 60"
        shape-rendering="crispEdges"
      >
        <path
          d="M29 50h3V17h-3ZM18 24h11v4H18ZM12 19h10v5H12ZM8 13h10v7H8ZM24 36h7v4h-7ZM14 29h12v8H14ZM29 14h8v9h-8Z"
          fill="#809555"
        />
        <path
          d="M10 14h8v3h-8zm6 16h8v3h-8zm15-15h5v3h-5z"
          fill="#afbf78"
        /></svg
    ></span>
  </div>
</template>

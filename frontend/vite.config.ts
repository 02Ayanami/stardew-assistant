import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";
export default defineConfig({
  plugins: [vue()],
  server: {
    host: "127.0.0.1",
    port: Number(process.env.FRONTEND_PORT || 5174),
    strictPort: true,
    proxy: {
      "/api": {
        target: process.env.API_TARGET || "http://127.0.0.1:9911",
        changeOrigin: true,
        timeout: 150000,
        proxyTimeout: 150000,
      },
    },
  },
  test: { environment: "jsdom", include: ["src/**/*.test.ts"] },
});

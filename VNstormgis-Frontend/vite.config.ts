import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    host: true, // Cho phép lắng nghe trên tất cả địa chỉ mạng nội bộ
    strictPort: false,
    proxy: {
      // Reverse Proxy chuyển tiếp lời gọi API sang Backend Spring Boot
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
        configure: (proxy) => {
          proxy.on("error", (err) => {
            console.error(
              "[Vite Proxy Error] Lỗi kết nối tới Backend Spring Boot (:8080):",
              err.message
            );
          });
        },
      },
      // Reverse Proxy chuyển tiếp lời gọi Actuator Health sang Backend
      "/actuator": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
    chunkSizeWarningLimit: 1000,
  },
});

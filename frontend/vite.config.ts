import path from "path"
import { defineConfig } from "vitest/config"
import react from "@vitejs/plugin-react-swc"
import tailwindcss from "@tailwindcss/vite"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    // Deshabilitar el polyfill de modulepreload para evitar el inline script
    // que generaría Vite en index.html y rompería script-src 'self' de la CSP.
    modulePreload: { polyfill: false },
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor:  ["react", "react-dom", "react-router"],
          query:   ["@tanstack/react-query"],
          ui:      ["radix-ui"],
          charts:  ["recharts"],
          icons:   ["lucide-react"],
        },
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    // Sin esto Vite escucha SOLO en IPv6 (`[::1]:5173`) en macOS: el navegador
    // funciona porque resuelve `localhost` a ::1, pero cualquier herramienta
    // que use 127.0.0.1 —automatización de navegador, Playwright segun el
    // entorno, un contenedor— no llega y parece que el servidor esta caido.
    //
    // Se fija a la loopback IPv4, que es a donde apunta casi todo por defecto.
    // Para abrirlo a la red local (probar la web desde el movil) seria
    // `host: true`, pero entonces el proxy de /api expone tambien el backend
    // a quien este en la misma wifi.
    host: "127.0.0.1",
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    globals: true,
    include: ["src/**/*.test.{ts,tsx}"],
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
})

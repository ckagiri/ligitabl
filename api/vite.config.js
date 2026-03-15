import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig(({ mode }) => {
  const isDev = mode === "development";

  return {
    build: {
      outDir: resolve(__dirname, "src/main/resources/static/dist"),
      emptyOutDir: true,

      rollupOptions: {
        input: {
          main: resolve(__dirname, "src/main/resources/static/css/main.css"),
          app: resolve(__dirname, "src/main/resources/static/js/ligitabl.js"),
        },
        output: {
          assetFileNames: "css/[name][extname]",
          entryFileNames: "js/[name].js",
        },
      },

      minify: isDev ? false : "terser",
      terserOptions: !isDev
        ? {
            compress: {
              drop_console: true,
              drop_debugger: true,
            },
            mangle: true,
          }
        : undefined,
    },

    css: {
      postcss: {
        plugins: [require("tailwindcss"), require("autoprefixer")],
      },
    },

    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  };
});

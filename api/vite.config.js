/** @type {import('vite').Config} */
import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig(({ mode }) => {
  const isDev = mode === "development";

  return {
    build: {
      // dev  → static/css/main.css  (served at /css/main.css)
      // prod → static/dist/css/main.css  (served at /dist/css/main.css)
      outDir: isDev
        ? resolve(__dirname, "src/main/resources/static")
        : resolve(__dirname, "src/main/resources/static/dist"),

      // Never wipe the whole static dir in dev; prod can clean dist/
      emptyOutDir: !isDev,

      rollupOptions: {
        input: {
          main: resolve(__dirname, "src/main/resources/assets/css/main.css"),
          app: resolve(__dirname, "src/main/resources/assets/js/ligitabl.js"),
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

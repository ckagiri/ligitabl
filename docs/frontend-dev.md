# Frontend Development Guide

## Overview

The frontend uses **Tailwind CSS** for styling and **Alpine.js** for interactivity, bundled by **Vite**.

## Directory layout

```
api/
├── assets/               # Source files — edit these
│   ├── css/main.css      # Tailwind CSS entry point
│   └── js/ligitabl.js    # Alpine.js component definitions
│
└── src/main/resources/
    ├── static/           # Vite build output — do not edit directly
    │   ├── css/          # dev build output  (/css/main.css)
    │   ├── js/           # dev build output  (/js/app.js)
    │   └── dist/         # prod build output (/dist/css/main.css, /dist/js/app.js)
    └── templates/        # Thymeleaf templates
```

**Rule of thumb:** only ever edit files under `assets/`. Anything in `static/` is generated.

## Build commands

Run from `api/`:

| Command | Output dir | Use for |
|---|---|---|
| `npm run dev` | `static/` | Watch mode — auto-rebuilds on save |
| `npm run build:dev` | `static/` | One-off dev build (unminified) |
| `npm run build` | `static/dist/` | Production build (minified) |

## Which files are served

The base layout (`layout/base.html`) switches paths based on build mode:

```html
<!-- Dev  → /css/main.css and /js/app.js -->
<!-- Prod → /dist/css/main.css and /dist/js/app.js -->
```

Both point to the same source — the path just differs by build mode.

## Tailwind class purging

Vite runs Tailwind's JIT scanner over the template files. New utility classes used in
`templates/**/*.html` are picked up automatically on the next build. If a class isn't
appearing, run a fresh build — it means the CSS hasn't been regenerated yet.

## Alpine.js components

All Alpine component data factories live in `assets/js/ligitabl.js` under the `Ligitabl`
namespace, e.g. `Ligitabl.predictionPage($el)`. They are referenced in templates via
`x-data="Ligitabl.predictionPage($el)"`.

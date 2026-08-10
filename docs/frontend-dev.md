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

### Updating a component after a fetch: use state, not `data-*`

Components are seeded from `data-*` attributes at mount. That makes it tempting to *update* them
the same way after a background save — write `el.dataset.foo` and let the next render pick it up.

**It does not work for anything bound.** Alpine re-evaluates a binding only when a *reactive*
dependency changes, and a `dataset` write is a plain DOM mutation it never observes:

```js
// Silently stale: x-text="label()" will not re-render.
card.dataset.order = JSON.stringify(data.order);

// Correct: assign to component state.
Alpine.$data(card)?.applySaved?.(data);
```

The failure mode is nasty because it is **partial**. Anything computed on demand — a click handler
reading `dataset` when it fires — looks fine, while bound text stays stale. On the Final Table
share card this showed as a downloaded image with the new order and share text with the old one
(see [Final Table share card](./final-table-share.md#reactivity-why-attributes-are-not-enough)).

Rule of thumb: `data-*` is the **seed**, read once in `init()`. Anything that changes after mount
belongs in component state.

To push into a sibling/child component from outside, `Alpine.$data(el)` is the supported handle
(Alpine v3 has no `el.__x` — that was v2). Prefer a named method on the target (`applySaved(data)`)
over reaching in and assigning fields, so the dependency direction stays obvious.

### Reading a parent's state from a nested fragment

A fragment mounted inside another component's DOM subtree can use the parent's state **directly in
markup** via Alpine's scope chain — no plumbing, no handle:

```html
<!-- inside x-data="Ligitabl.finalTablePage($el)" -->
<div x-show="getDirtyCount() > 0">Unsaved changes</div>
```

This is worth preferring when the child component should stay independent of the parent: the markup
sees the parent scope while the component itself keeps no reference back.

Two cautions:

- **Guard fragments that are also mounted standalone.** If the same fragment renders on a page
  without that parent, the expression must not be emitted at all — gate it server-side with
  `th:if` on a model attribute that distinguishes the two, rather than letting it render and fail.
- **Use parser-level comments (`<!--/* … */-->`) for notes near such markup.** A plain HTML comment
  survives Thymeleaf rendering and ships to the browser; on a public page that leaks internal
  reasoning to visitors.

## Timestamps: always format client-side

The server does not know the viewer's timezone, so **never** render a user-facing instant with
`#temporals.format(...)` over `ZoneId.systemDefault()` — that is the *server's* zone, and every
viewer outside it sees a time wrong by their offset. Emit the raw ISO instant and let the client
format it.

```html
<!-- th:text keeps the ISO string as the no-JS fallback -->
<span th:attr="data-timestamp=${instant}" th:text="${instant}">12 Aug 2026, 14:32</span>
```

`Ligitabl.formatTimestamps(root?)` rewrites every `[data-timestamp]` in scope, and re-runs on
`DOMContentLoaded` and `htmx:afterSettle` so swapped-in content is covered. For non-DOM output
(canvas text, a computed label), call `Ligitabl.formatTimestamp(iso, preset)` — it returns `''`
for absent or unparseable input so callers can omit the line.

Shapes are named presets in `Ligitabl.TIMESTAMP_FORMATS`, opted into with `data-timestamp-format`:

| Preset | Shape |
|---|---|
| `default` (omit the attribute) | `Aug 9, 2026, 4:40 PM` |
| `settled` | `9 Aug 2026, 16:40` |

Add a preset rather than accepting arbitrary options from a template — a template that can request
any format is one that can invent a third by accident.

## Manual browser verification (Playwright)

`curl`/HTML inspection can't confirm real interactive behavior — button-disabled states, JS
callback races, third-party widget rendering, or "what happens when a script is blocked." For
that, drive a real headless browser with Playwright via `npx` (no need to add it as a project
dependency — it's not in `api/package.json`, just pulled ad hoc for a verification session):

```bash
npx --yes playwright install chromium              # full browser (renders pages)
npx --yes playwright install chromium-headless-shell # separate download, needed too — see below
```

Then run a small script with `node your-script.mjs` (ESM: `import { chromium } from 'playwright'`
after `npm install playwright --no-save` in whatever scratch directory you're working from).

**Gotchas hit in practice (task 66 / Turnstile widget verification):**

- **`chromium.launch()` needs `chromium-headless-shell`, not just `chromium`.** Installing only
  `chromium` produces `Executable doesn't exist at .../chromium_headless_shell-.../chrome-headless-shell`
  on the first `launch()` call. Install both — they're separate downloads (~170MB and ~95MB).
- **Both downloads can be slow and get killed by a `timeout` wrapper mid-download**, restarting
  from 0% on the next attempt rather than resuming. Give each install its own generous timeout
  (150s+) and don't chain them behind other slow commands in the same timeout budget.
- **Alpine.js v3 has no `el.__x`** (that was Alpine v2). To read a component's live state from
  outside the page (e.g. to assert on `x-data` fields in a test), use the official API:
  ```js
  await page.evaluate(() => window.Alpine.$data(document.querySelector('form')).someField);
  ```
- Route interception (`page.route('**/some-domain.com/**', route => route.abort())`) is the way
  to simulate a blocked/failed third-party script and check fail-closed behavior. This is how a
  real race condition was caught in the Turnstile registration widget (`templates/auth/register.html`):
  a `<script>` tag's `onerror` handler could fire before Alpine had finished initializing and
  attached its window-event listener, silently dropping a user-facing error message even though
  the underlying fail-closed behavior (submit staying disabled) was unaffected. Fixed by setting a
  plain top-level flag synchronously inside the `onerror` handler, independent of Alpine's
  readiness, and having the component's `init()` lifecycle hook read it on mount.

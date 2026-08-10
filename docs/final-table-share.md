# Final Table: share card and saved state

This document explains how the Final Table share card decides what to draw, why it shows saved
state rather than what is on screen, and the local-time rules the feature follows. It covers the
owner's page (`/final-table`), the public view (`/final-table/u/{publicId}/{seasonShorthand}`), and
the FAQ page behind them (`/faq/final-table`).

## Table of Contents

- [Why the card shows saved state](#why-the-card-shows-saved-state)
- [How ordering is resolved](#how-ordering-is-resolved)
- [The save round trip](#the-save-round-trip)
- [Reactivity: why attributes are not enough](#reactivity-why-attributes-are-not-enough)
- [Unsaved-changes notice](#unsaved-changes-notice)
- [Timestamps in the viewer's timezone](#timestamps-in-the-viewers-timezone)
- [The FAQ page](#the-faq-page)
- [Implementation files](#implementation-files)

## Why the card shows saved state

The share card is deliberately blind to unsaved moves. Everything it produces leaves the app — a
downloaded PNG, copied text, a link to the public page — so it must describe a table the server
has actually stored.

If it followed the live table instead, a player could drag two clubs, not save, copy the share
text, and post a prediction that exists nowhere on the server — while the public link in that same
panel showed something different. The card also prints `settledAt`, the leaderboard tiebreak, which
only ever describes a save; pairing that timestamp with unsaved rows would make the card misstate
its own provenance.

The visible consequence is intentional: **with unsaved moves, the card and the table above it
disagree.** The [unsaved-changes notice](#unsaved-changes-notice) exists to explain that.

## How ordering is resolved

`_resolvedOrder()` in `finalTableShareCard` picks between two sources:

| Source | When | What it is |
|---|---|---|
| `savedOrder` | After any save, or seeded from `data-order` | The server's own replay of the swaps |
| `data-rows` (as seeded) | First paint, and the public view | Already the saved order — the server just rendered it |

`savedOrder` holds **team codes only**, so display fields (`name`, `shortName`) are looked up in
`data-rows`, which is a stable code→display map that does not change mid-session. Scored figures
(`actual`, `hit`) are always merged back from `data-rows` — the client never recomputes them.

Two guards:

- A code with no seeded row is dropped rather than drawn blank.
- If dropping leaves the wrong number of teams, the order disagrees with the seed, so the seeded
  order is used instead.

`rows()` (the canvas) and `shareText()` both call `_resolvedOrder()`, so the image someone
downloads and the text they copy can never list the clubs differently.

## The save round trip

`POST /final-table` sends `swaps` plus `expectedOrder` — a checksum of what the client believes the
table looks like, not the payload. The server replays the swaps against the stored row and returns:

```json
{ "success": true, "message": "Saved", "swapCount": 3,
  "settledAt": "2026-08-10T11:08:53Z", "hasEntry": true,
  "order": ["LIV", "MCI", "MUN", "…"] }
```

`order` is the authoritative answer to "what did you just save." It is redundant-by-construction —
`expectedOrder` already 409s on a mismatch — but it is what lets the card render saved state
without inferring it from the DOM.

The page never re-renders on save, so `finalTablePage._refreshShareCard(data)` pushes `order` and
`settledAt` into the card. The dependency is one-way: the page pushes, the card never reaches back.

> `_refreshShareCard` runs **after** `hasEntry = true`. The card is mounted from page load either
> way (`x-show` only toggles visibility), so this just orders the update behind the reveal on a
> first save.

## Reactivity: why attributes are not enough

⚠️ **The card's order and settle time live in Alpine component state (`savedOrder`, `settledAt`),
not in `data-*` attributes.**

An earlier version wrote `card.dataset.order` and `card.dataset.settledAt`. That silently failed
for the share text: the panel binds `x-text="shareText()"`, and Alpine re-evaluates a binding only
when a *reactive* dependency changes. A `dataset` write is a plain DOM mutation Alpine never sees,
so the text kept rendering the pre-save order until a full reload.

The failure was asymmetric and easy to misread:

| Surface | Reads order | Affected by the dataset bug? |
|---|---|---|
| Share text | `x-text="shareText()"` — bound, re-renders reactively | **Yes** — stayed stale |
| Image | `downloadCard()` → `rows()` at click time | No — read fresh on each click |

So the image looked correct while the text did not. If you add another live-bound surface to this
card, drive it from component state for the same reason.

The attributes are still the *seed*: `init()` reads `data-order` and `data-settled-at` once, so a
server-rendered order is honoured on page load. After that, `applySaved()` owns both fields.

## Unsaved-changes notice

Because the card shows saved state, the panel warns when the two disagree:

> ⚠ Showing your last saved table. Save to share the **2 moves** you just made.

Two details:

- **It reads `getDirtyCount()` through Alpine's scope chain, not through the component.** The
  fragment is nested inside `finalTablePage` on the owner's page, so the markup can see the
  parent's state even though the card cannot. Giving the card a handle back to the parent would
  rebuild the coupling that removing it bought.
- **It is gated `th:if="${shareCardHeading == null}"`** — an attribute only the public controller
  sets. On the public view there is no `finalTablePage` to inherit from, so the expression must not
  render at all.

> Use a Thymeleaf parser-level comment (`<!--/* … */-->`) for notes near this block. A plain HTML
> comment survives rendering, and the public page is served to strangers — an ordinary `<!-- -->`
> here leaked internal reasoning onto it.

## Timestamps in the viewer's timezone

`settledAt` is an instant. The server has no idea where the viewer is, so **it is always formatted
client-side** — a UTC time on a card shared locally reads as wrong by however many hours the viewer
is offset.

`Ligitabl.TIMESTAMP_FORMATS` holds the named option bags:

| Preset | Shape | Used by |
|---|---|---|
| `default` | `Aug 9, 2026, 4:40 PM` | Everything with a bare `data-timestamp` (swap history, matches) |
| `settled` | `9 Aug 2026, 16:40` | The share card footer, and the public page's settled line |

Two ways in:

```html
<!-- Declarative: formatted by Ligitabl.formatTimestamps on load and htmx:afterSettle.
     th:text keeps the ISO string as the no-JS fallback. -->
<strong th:attr="data-timestamp=${settledAt}"
        data-timestamp-format="settled"
        th:text="${settledAt}">12 Aug 2026, 14:32</strong>
```

```js
// Imperative, for canvas text and other non-DOM output.
Ligitabl.formatTimestamp(iso, 'settled');   // '' when absent or unparseable
```

> **Do not format this server-side.** `#temporals.format(...)` over `ZoneId.systemDefault()` uses
> the *server's* zone. That was the original bug on the public page: every viewer outside the
> server's timezone saw a wrong time, while the share card beside it was correct.

Adding a preset is preferable to passing arbitrary options from a template — there are two shapes
in play, and a template that can request any format is one that can invent a third by accident.

## The FAQ page

`/faq/final-table` serves the Final Table section of the FAQ as its own page. The content lives in
one fragment, `fragments/faq-final-table.html`, included by both:

| Caller | Include | First item |
|---|---|---|
| `/faq` | `:: section(false)` | Closed — it sits among other sections |
| `/faq/final-table` | `:: section(true)` | Open — it is the whole page, and a collapsed list reads as empty |

Two things to know when touching this:

- **`SecurityConfig`'s permit-all list is exact-match.** `"/faq"` does **not** cover `/faq/…`;
  `/faq/final-table` is listed separately. Without it the page 302s anonymous visitors to login —
  and signed-out readers are most of its audience.
- **The CTA is gated on `finalTableEntryOpen`**, matching the public page and the navbar. "Order
  all twenty clubs before a ball is kicked" is a promise the app cannot keep after kickoff, so it
  is dropped rather than reworded. The flag comes from `NavbarControllerAdvice`, so the route does
  not resolve a season itself.

The section's copy is written to stand alone — a guest can land on `/final-table` and play without
ever seeing a gameweek — so it explains the game directly rather than by contrast with the main
competition. `ZERO_BONUS`/`CHAMPION_BONUS` interpolate from `FinalTableScorer` via SpEL, which
works identically inside the fragment.

## Implementation files

| Concern | File |
|---|---|
| Share card component | `api/assets/js/ligitabl.js` — `Ligitabl.finalTableShareCard` |
| Save + push to card | `api/assets/js/ligitabl.js` — `finalTablePage.save`, `_refreshShareCard` |
| Timestamp presets | `api/assets/js/ligitabl.js` — `TIMESTAMP_FORMATS`, `formatTimestamp(s)` |
| Share panel markup | `api/src/main/resources/templates/fragments/final-table-share.html` |
| Owner page | `api/src/main/resources/templates/final-table.html` |
| Public view | `api/src/main/resources/templates/final-table-public.html` |
| FAQ fragment / page | `templates/fragments/faq-final-table.html`, `templates/faq-final-table.html` |
| Save endpoint | `api/.../web/finaltable/savefinaltable/SaveFinalTableWebController.java` |
| Routes + permit-all | `api/.../web/PublicController.java`, `api/.../config/SecurityConfig.java` |

Remember: `api/assets/js/ligitabl.js` is the Vite **source**. `static/js/app.js` and
`static/dist/js/app.js` are build output — never hand-edit them.

# Prediction Page UI States & Banners

This document explains the UI state management and banner logic for the predictions page (`/predictions/user/me`, `/predictions/user/guest`). Round navigation and refresh reload the page fragment via HTMX through `/my-table`.

> **Note on copy**: banner text and emojis shown here are illustrative — the wording is tuned continually based on feedback. The *conditions* and *sentiment* are the contract; the exact strings live in the templates.

## Table of Contents

- [Game Mechanics Overview](#game-mechanics-overview)
- [Season Phases & Setup Mode](#season-phases--setup-mode)
- [Pre-Season Registration](#pre-season-registration)
- [Round States](#round-states)
- [Season Completion](#season-completion)
- [User Access Modes](#user-access-modes)
- [Derived Editing Gates](#derived-editing-gates)
- [Banner Display Logic](#banner-display-logic)
- [Decision Tree](#decision-tree)
- [Swap Rules](#swap-rules)
- [Round Navigation Logic](#round-navigation-logic)
- [Interactive vs Historical View](#interactive-vs-historical-view)
- [Examples by Scenario](#examples-by-scenario)
- [Implementation Files](#implementation-files)

## Game Mechanics Overview

### Core Concepts

**Round Lifecycle:**

1. **Open** — users can create and modify predictions
2. **Locked** — matches in progress, predictions locked (with exceptions, see below)
3. **Completed** — all matches finished, awaiting scoring
4. **Finalized** — results scored; brief window before the season pointer advances
5. **Advanced** — season pointer moved to the next round (or, on the last round, the season is complete)

**Swap Allowances** (how many swaps a single submission may contain):

| Context                              | Swaps allowed | Counts toward cooldown?                    |
| ------------------------------------ | ------------- | ------------------------------------------ |
| Initial prediction (joining)         | up to 5       | No — but using any spends the next-swap bonus |
| Pre-season registration              | up to 5       | No — never counted anywhere (the easter egg) |
| Opening-round window (each new round)| up to 2       | Yes — cooldown starts after                |
| Regular swap                         | 1             | Yes — 24h cooldown                         |

### Next Swap Bonus

**Condition:** `swapStatus.firstSwapBonus` = `initialPredictionMade && lastSwapAt == null`

`lastSwapAt` is only set at join/merge time **if any swaps were used then** (`CreatePredictionUseCase`: `lastSwapAt = swaps.isEmpty() ? null : now`). So the "✨ Next Swap Available!" banner appears **only when the user made zero swaps during registration or joining** — they accepted the baseline table as-is, and their first real swap is free (no 24h wait). After it's used, the cooldown applies.

### Opening-Round Swap

After the next-swap bonus is spent, each new round grants a one-time opening window:

**Condition:** `openingRoundAvailable = openingCommittedRound != currentRound && lastSwapAt != null && roundStatus == OPEN` (computed in `GetUserPredictionUseCase.buildCurrentEditableView`)

- Allows **1 or 2 swaps** in a single Update; cooldown starts after.
- `MakeSwapUseCase.validateCooldown` enforces ordering: once the bonus is spent, the opening window must be used before cooldown swaps (`SwapError.UseOpeningWindowFirst`), otherwise the 24h `CooldownActive` check applies.
- Submitting commits `openingCommittedRound = currentRound`, closing the window for this round.

### Swap Cooldown

- 24 hours (`SwapCooldown.COOLDOWN`), keyed off `lastSwapAt`.
- `canSwap` is true when `lastSwapAt == null` (free first swap) or the cooldown has elapsed.
- Status indicator shows "Cooldown active - Xh remaining" parsed from `SwapCooldown.getStatusMessage`.

## Season Phases & Setup Mode

The season has a derived 4-value phase, `SeasonState` (`Season.getSeasonState()`), computed from `completed`, `startDate`/`endDate`, `preSeasonOpensAt`, and `predictionsOpenAt`:

| Phase        | Template flag | Meaning                                                                 |
| ------------ | ------------- | ----------------------------------------------------------------------- |
| `IN_PLAY`    | `isInPlay`    | Season active, predictions open — the normal state                      |
| `PRE_SEASON` | `isPreSeason` | Upcoming season promoted, pre-season open, predictions not yet open     |
| `OFF_SEASON` | `isOffSeason` | Between seasons (completed & past end, or before start with nothing open) |
| `INACTIVE`   | `isInactive`  | Fallback — none of the above apply (e.g. successor season not configured). Treated like setup mode. |

**Setup mode** is orthogonal to the phase: `Season.isInSetupMode()` = `mainContestId == null` (the main contest is detached into `detachedContestId` while an admin reconfigures fixtures). Template flag: `seasonInSetupMode`.

**Season-level banners** (in `predictions.html`, above the access banners):

- 🛠️ **Setup mode** — `seasonInSetupMode && !isGuest`: "an admin is reconfiguring this season's fixtures. Swaps and new entries are paused."
- 🛠️ **Inactive** — `!seasonInSetupMode && isInactive && !isGuest`: "This season isn't active right now."
- 🚀 **Pre-Season CTA** (indigo) — `isPreSeason && (isGuest || canCreateEntry)`: register your table, with countdown ("Competition starts in N days" via `daysToPredictions` / `predictionsAboutToStart`).
- ✅ **You're Registered!** (green) — `isPreSeason && !isGuest && !canCreateEntry`: table is set, predictions open on date, countdown.
- 🌙 **Off-Season** (slate) — `isOffSeason`: pre-season countdown (`daysToPreSeason` / `preSeasonAboutToStart`) or "dates haven't been announced yet."

**Gating**: the access-banners fragment and the round-state header indicators only render when `isInPlay || isOffSeason`. Pre-season and setup/inactive states rely on their own banners.

A debug panel dumping all flags is available via `?debug=1`.

## Pre-Season Registration

The pre-season "easter egg": during `PRE_SEASON`, a user with no prediction can register their table early.

- `CreatePredictionUseCase` resolves one of three join plans: `NewJoin` (in-play), `NewPreSeasonRegistration` (pre-season, no existing prediction), `MergePreSeasonRegistration` (in-play, round-0 row exists).
- A registration is a `SeasonPrediction` with **`atRoundNumber == 0`** (`isPreSeasonRegistration()`) plus a contest `Entry` with `joinedAtRound = 0`. `initialRankings` is set as the permanent pre-registered marker. Confirmation: "You're registered! Your table is set for when predictions open."
- Up to **5 swaps** at registration; `lastSwapAt` stays null iff zero swaps were used.
- Registration/join is rejected when the season is completed or in setup mode.

**Round 0 is a bookkeeping marker, not a scoring exclusion.** `FinalizeRoundUseCase` fetches predictions with `atRoundNumber <= round.position`, so a registration — merged or not — is snapshotted (`currentRankings`) and fully scored from the first finalized round. If the user never returns, their pre-season table simply plays as-is.

**The easter egg is the swap cost.** Registration swaps are stored as a `RoundSwap` with `round = 0`; `FinalizeRoundUseCase.countSwapsInRound` only counts swaps whose `round == roundPosition` (always ≥ 1), so round-0 swaps never enter any `RoundResult.swapCount` — and the leaderboard tiebreaks on total swaps ascending. Net effect: up to 5 swaps to author your *own* starting table (instead of the season's default initial rankings), never counted against you.

**Merge**: once `isInPlay`, the user's next submit finds the round-0 row and merges it in place — real `atRoundNumber` set, `openingCommittedRound = atRoundNumber`, additional swaps applied on top of the registration snapshot, and `lastSwapAt = now` iff registration had swaps or swaps were used at merge (which is what spends the next-swap bonus).

**Rendering an unmerged registration**: template flag `isPreSeasonRegistration`. `GetUserPredictionUseCase.buildCurrentEditableView` forces `SwapCooldown.initial()` (`initialPredictionMade = false`) so the user presents as a brand-new predictor: 5-swap allowance, no cooldown or bonus indicators, and the opening-round calculation (whose inputs only become meaningful at merge) is skipped. The access mode is unchanged; everywhere `isInitialPrediction` is checked, the registration behaves like an initial prediction.

## Round States

`RoundStatus` precedence (`GetUserPredictionUseCase.resolveRoundStatus`): `advanced` → `finalized` → match-derived (`open`/`locked`/`completed`). Lowercased into template var `roundState`.

| State       | Description                          | User Actions                                              |
| ----------- | ------------------------------------ | --------------------------------------------------------- |
| `open`      | Round open for predictions           | Create prediction, swap (subject to cooldown/windows)     |
| `locked`    | Matches in progress                  | Create initial prediction only; future-round swaps allowed |
| `completed` | Matches finished, scoring pending    | Same as locked                                            |
| `finalized` | Results scored, pointer not yet moved | Read-only; brief transitional state                       |
| `advanced`  | Pointer moved on (mid-season) or season complete (last round) | Read-only                        |

**Header state indicator** (shown only when `isCurrentRound && (isInPlay || isOffSeason)`):

- 🟢 **Open** (green) — `roundState == 'open'`
- 🔒 **Locked** (yellow) — `roundState == 'locked' || 'completed'`
- ⏳ **Finalizing** (orange) — `roundState == 'finalized'`
- 📊 **Finalized** (blue) — `roundState == 'advanced' && isLastRound` (season done)

The header also shows a **source indicator pill** when `source != 'USER_PREDICTION'` ("Current Gameweek Standings" / "Previous Gameweek Baseline" / "Last Season Baseline") and a **Refresh** button that reloads the fragment via HTMX.

## Season Completion

`seasonCompleted` is **derived, not stored** (`GetUserPredictionUseCase.buildViewData`):

```java
boolean seasonCompleted = currentRoundStatus == RoundStatus.ADVANCED && currentRound == lastRound;
```

- `lastRound = season.getMaxRounds()`.
- Mid-season, advancing a round moves `currentRoundId` forward, so the finalized round becomes historical naturally. The **last** round has nowhere to advance to — `currentRoundId` stays on it, and its `ADVANCED` status is the "season is done" signal.
- `RoundAdvancementService` deliberately leaves `season.completed` for an admin to set explicitly; the UI never reads it for this.
- Related flags: `isLastRound` (`currentRound == lastRound`), `notLastRound`, `isCurrentRoundLast`, `isLastRoundOpen`, `notLastRoundClosed` (`!isLastRound || isLastRoundOpen` — once the last round stops being open, editing is over for good).

## User Access Modes

`PredictionAccessMode` has three values; guest is a separate `isGuest` flag, not a mode.

| Access Mode        | Description                                       |
| ------------------ | ------------------------------------------------- |
| `EDITABLE`         | Current round and (`canSwap` OR opening window)   |
| `READONLY`         | Cooldown active, historical round, or guest view  |
| `CAN_CREATE_ENTRY` | Authenticated, no prediction yet                  |

Determined in `GetUserPredictionUseCase.determineAccessMode`. Historical views are always `READONLY`. The access mode is the *user permission* layer; the template applies round-state and season-state gates on top (next section).

## Derived Editing Gates

Computed once in `UserPredictionsController.handleSuccess` and passed to templates — fragments never re-derive these:

```java
seasonAllowsUpdate  = isInPlay || (isPreSeason && !hasPreSeasonRegistration);
seasonEditingAllowed = !seasonInSetupMode && seasonAllowsUpdate;

canInteractEffective = canInteractWithTable && !isHistoricalView && seasonEditingAllowed && notLastRoundClosed;
canSwapEditable      = (canSwap || isOpeningRound) && !isHistoricalView && seasonEditingAllowed && isRoundOpenForPrediction;
isInitialPredictionEditable = canCreateEntry && seasonEditingAllowed && isRoundOpenForPrediction;
```

Supporting round flags:

- `isFutureRoundPrediction` = `atRoundNumber != null && atRoundNumber > currentRound` (user predicted during a locked round; their prediction targets the next round, so they may keep swapping)
- `isRoundOpenForPrediction` = `roundState == 'open' || isFutureRoundPrediction`
- `isRoundLockedOrBeyond` = `roundState in {locked, completed, finalized}`
- `isHistoricalView` = `!isCurrentRound || (isCurrentRound && seasonCompleted)`

These feed the Alpine.js data attributes on the prediction table (`data-can-swap=canSwapEditable`, `data-can-interact=canInteractEffective`, `data-is-initial-prediction=isInitialPredictionEditable`, `data-is-opening-round`, `data-is-pre-season-registration`, `data-access-mode`) and the actions-footer visibility (`th:if="${canInteractEffective}"`).

**Design pattern**: backend `canSwap` = user's cooldown permission; frontend gates layer round state and season state on top. All must hold for swaps to work.

## Banner Display Logic

All access banners live in `fragments/access-banners.html` and render only when `isInPlay || isOffSeason`. Season-phase banners (setup, inactive, pre-season, off-season) are covered in [Season Phases & Setup Mode](#season-phases--setup-mode).

### 1. Guest Banner (yellow, "Guest Preview")

**Condition:** `isGuest == true`. Message varies:

- **Current round + open**: "Tap teams to swap and build your prediction — up to 5 swaps. Sign up to save and join the competition."
- **Current round + locked/completed/finalized, not last round** (`isRoundLockedOrBeyond && notLastRound`): "👋 Matches In Progress — Tap teams to swap and build your prediction… Sign up…"
- **Current round is last + locked/completed/finalized** (`isCurrentRoundLast && isRoundLockedOrBeyond`): "This is the final round. Matches are in progress and the season is completing. Sign up to join future competitions."
- **Current round + season completed**: "This season has completed. You can browse the results. Sign up to join future competitions."

### 2. Can Create Entry Banner (green)

**Condition:** `(canCreateEntry || isPreSeasonRegistration) && roundState != 'locked' && seasonEditingAllowed`

- With imported guest prediction (localStorage `ligitabl.guestPrediction`): "Your Guest Prediction Imported! We've loaded your prediction - review it and submit…"
- Otherwise: "Ready to Predict! Set your predicted order and submit. Take your time — no swaps needed, but up to 5 to fine-tune."

### 3. Next Swap Bonus Banner (green)

**Condition:** `swapStatus.firstSwapBonus && seasonEditingAllowed`

- Default: "✨ Next Swap Available! Make your next swap without waiting 24 hours. After this, the 24h cooldown applies."
- Locked + future-round prediction (`roundState == 'locked' && isFutureRoundPrediction`): adds "Matches are in progress. Your prediction will be scored next round."

### 4. Opening Swap Banner (amber)

**Condition:** `swapStatus.openingRoundAvailable && seasonEditingAllowed`

"⚡ Opening Swap Available — Make 1 or 2 swaps and hit Update — cooldown starts after."

### 5. Locked State Banner

**Condition:** `!isGuest && isCurrentRound && roundState == 'locked' && !swapStatus.firstSwapBonus`

Variant selected via `lockedVariant`:

| Variant            | Condition                                             | Style  | Message                                                                 |
| ------------------ | ----------------------------------------------------- | ------ | ----------------------------------------------------------------------- |
| `seasonCompleting` | `isLastRound`                                         | yellow | "🔒 Season Completing — This is the final round. Matches are in progress and predictions are locked." |
| `initialPrediction`| `isInitialPredictionEditable`                         | blue   | "👋 Matches In Progress — Set your predicted order and submit…" (or guest-import variant) |
| `futureRoundSwap`  | `isFutureRoundPrediction && seasonEditingAllowed`     | blue   | "👋 Matches In Progress — Your prediction will be scored next round. You can still make swaps." |
| `locked` (default) | otherwise                                             | yellow | "🔒 Round Locked — Matches are in progress. Predictions are locked until results are finalized." |

### 6. Scoring Banner (yellow)

**Condition:** `!isGuest && isCurrentRound && (roundState == 'completed' || roundState == 'finalized') && !seasonCompleted`

- Default: "⏳ Scoring — Scoring predictions. Points will be available shortly."
- Initial prediction still editable and not last round (`isInitialPredictionEditable && notLastRound`): "⏳ Scoring predictions — Set your predicted order for next round…"

### 7. Historical Round Info Banner (blue)

**Condition:** `(!isCurrentRound || seasonCompleted) && !isGuest`

Also covers viewing the current (last) round once the season is complete — with a receipt/document icon when `seasonCompleted && viewingRound == lastRound`, a clock-stack icon otherwise.

- Scored: "**Gameweek N** — You scored **X** points this round." (+ score emoji)
- Not yet scored: "**Gameweek N** — Results pending."

### 8. Status Indicator (Ready/Cooldown)

**Condition:** `!isGuest && !canCreateEntry && !swapStatus.firstSwapBonus && seasonEditingAllowed`

- 🟢 **Ready to modify** — `isCurrentRound && canSwap && isRoundOpenForPrediction`
- 🟡 **Cooldown active - Xh remaining** — `isCurrentRound && !canSwap && isRoundOpenForPrediction`

Hidden entirely when the round isn't open for the user's prediction (e.g. locked in on the current round).

## Decision Tree

```
Season in setup mode or inactive? (non-guest)
├─ YES → 🛠️ Setup/Inactive banner; swaps and joins paused (seasonEditingAllowed = false)
│
Season phase?
├─ PRE_SEASON → 🚀 Pre-Season CTA (guest / no entry) or ✅ You're Registered (registered)
├─ OFF_SEASON → 🌙 Off-Season banner (+ access banners below)
└─ IN_PLAY / OFF_SEASON → access banners:
    │
    Is user a guest?
    ├─ YES → Guest Banner (message varies by round state / last round / season completed)
    │
    └─ NO (authenticated)
        ├─ Can create entry (or unmerged pre-season registration) + round not locked?
        │  └─ YES → "Ready to Predict!" banner (green)
        │
        ├─ Next swap bonus (no swaps used at join/registration)?
        │  └─ YES → "✨ Next Swap Available!" banner (green)
        │
        ├─ Opening-round window available?
        │  └─ YES → "⚡ Opening Swap Available" banner (amber)
        │
        ├─ Current round + locked (and no bonus)?
        │  └─ YES → Locked banner variant:
        │      ├─ last round → "🔒 Season Completing"
        │      ├─ can still make initial prediction → "👋 Matches In Progress" (blue)
        │      ├─ future-round prediction → "👋 Matches In Progress — can still swap" (blue)
        │      └─ otherwise → "🔒 Round Locked" (yellow)
        │
        ├─ Current round + completed/finalized + season not complete?
        │  └─ YES → "⏳ Scoring" banner
        │
        └─ Historical round, or current round with season complete?
           └─ YES → "📊 Gameweek N — You scored X points" banner
```

## Swap Rules

### Core Principle

```
Swaps allowed = userCanSwap AND roundOpenForPrediction AND seasonEditingAllowed AND !historicalView
```

- **Backend** (`canSwap` / `openingRoundAvailable`): the user's cooldown/window permission
- **Frontend** (`canSwapEditable`): layers round state, season phase, setup mode, and historical-view checks on top

### The Future-Round Exception

When a user predicts during a locked round, their prediction activates next round (`atRoundNumber > currentRound`). They may keep swapping while matches are in progress — their prediction isn't being scored this round. This is why `isRoundOpenForPrediction` includes `isFutureRoundPrediction`.

### Server-Side Enforcement (`MakeSwapUseCase`)

```
lastSwapAt == null                        → free swap (bonus)
openingCommittedRound != currentRound     → must use opening window first (UseOpeningWindowFirst)
lastSwapAt + 24h > now                    → CooldownActive
otherwise                                 → swap allowed
```

### Scenario Trace: Joined With No Swaps, Round Open

| Step                        | canSwap | firstSwapBonus | openingRoundAvailable | Banner / indicator          |
| --------------------------- | ------- | -------------- | --------------------- | --------------------------- |
| Just joined (0 swaps used)  | true    | true           | false                 | ✨ Next Swap Available       |
| After bonus swap (cooldown) | false   | false          | false                 | 🟡 Cooldown active           |
| New round opens             | false*  | false          | true                  | ⚡ Opening Swap Available    |
| After opening swaps         | false   | false          | false                 | 🟡 Cooldown active           |
| Cooldown expires            | true    | false          | false                 | 🟢 Ready to modify           |

\* `canSwapEditable` is still true during the opening window (`canSwap || isOpeningRound`).

### Scenario Trace: Joined During Locked Round (Future-Round Prediction)

User predicted for GW 23 during locked GW 22 (`atRoundNumber = 23`, `currentRound = 22`, `roundState = 'locked'`):

| Step                        | canSwap | firstSwapBonus | Table interactive? | Banner                        |
| --------------------------- | ------- | -------------- | ------------------ | ----------------------------- |
| Joined with no swaps        | true    | true           | Yes                | ✨ Next Swap Available (locked-variant copy) |
| After bonus swap (cooldown) | false   | false          | No                 | 👋 Matches In Progress + 🟡 Cooldown |
| Cooldown expires            | true    | false          | Yes                | 👋 Matches In Progress + 🟢 Ready |

### Scenario Trace: Own Prediction, Current Round Locked In

`atRoundNumber <= currentRound`, `roundState = 'locked'`: table read-only, no actions footer, no status indicator (round not open for their prediction), yellow "🔒 Round Locked" banner — regardless of `canSwap`.

## Round Navigation Logic

Round navigation (Previous/Next + dropdown, `fragments/round-navigation.html`) is only rendered for non-guests. It reloads via HTMX: `GET /my-table?round=N` targeting `#prediction-page`.

### MinRound

```html
minRoundForNav = ${canCreateEntry ? currentRound
    : (joinedAtRound != null && joinedAtRound <= currentRound
        ? joinedAtRound : currentRound)}
```

`joinedAtRound` is the main-contest `Entry.joinedAtRound` — the round the user joined at, clamped to ≥ 1 in `GetUserPredictionUseCase`. It is **not** `SeasonPrediction.atRoundNumber`: since swaps update `atRoundNumber` (it marks the round `currentRankings` belong to), `atRoundNumber` is no longer a stable join marker and must not drive navigation.

1. **No prediction yet** (`canCreateEntry`) → only the current round.
2. **Has a prediction** → can page back to `joinedAtRound`, capped at `currentRound`.
3. Pre-season registrations carry `Entry.joinedAtRound = 0` permanently (the merge flow never updates it), but round-0 rows are scored from round 1 — the clamp maps 0 → 1 so their full history is navigable.
4. The `<= currentRound` cap means future join rounds (prediction made during a locked round for the next round) never appear.

### MaxRound

Always `currentRound` — future rounds never appear in the dropdown, even when a prediction targets one.

### Jump to Current

`showJumpToCurrent = !isCurrentRound` — appears when viewing a historical round.

## Interactive vs Historical View

The table switches on `isHistoricalView = !isCurrentRound || (isCurrentRound && seasonCompleted)`:

- **Interactive table** (`!isHistoricalView`): the current round while the season is ongoing — open, locked, completed, finalized. Interactivity itself is separately gated by `canSwapEditable` / `canInteractEffective`.
- **Historical view** (`isHistoricalView`): past rounds, or the current (last) round once the season completes. Static table with hit indicators, total hits/score/zeroes, season best, and sprint best (`fragments/prediction-historical-view.html`).

Mid-season finalized rounds become historical naturally when the pointer advances; only the last round stays "current" after finalization — that's the `seasonCompleted` case.

Also on the page: a results banner loaded via HTMX (`/my-table/latest-result-banner`) when `isCurrentRound && !isGuest`, and a collapsible swap-history section when the user has swaps for the viewed round.

## Examples by Scenario

### 1. Guest, Current Round Open

`isGuest`, `roundState = 'open'` → Guest Banner ("up to 5 swaps… Sign up"), interactive localStorage-backed table, comparison options, Sign Up CTA footer. No round navigation.

### 2. Pre-Season, Not Registered

`isPreSeason`, `canCreateEntry` (or guest) → 🚀 Pre-Season CTA with countdown, interactive table, 5-swap allowance, submit registers (round 0). No access banners (not in play).

### 3. Pre-Season, Registered

`isPreSeason`, `!canCreateEntry` → ✅ You're Registered banner with predictions-open date and countdown. `seasonAllowsUpdate = false` (registration exists), so editing gates are off.

### 4. New Authenticated User, Round Open

`canCreateEntry`, `roundState = 'open'` → "Ready to Predict!" banner, interactive table, submit joins the competition (up to 5 swaps).

### 5. New User During Locked Round

`canCreateEntry`, `roundState = 'locked'` → blue "👋 Matches In Progress" locked-banner variant (`initialPrediction`); user can still submit; prediction activates next round.

### 6. Just Joined With Zero Swaps

`firstSwapBonus = true` → "✨ Next Swap Available!" banner; one free swap, then 24h cooldown. (A user who used swaps at join skips straight to cooldown.)

### 7. New Round Opens, Bonus Already Spent

`openingRoundAvailable = true` → "⚡ Opening Swap Available" banner; 1–2 swaps in one Update, then cooldown.

### 8. Future-Round Prediction During Locked Round

`atRoundNumber > currentRound`, `roundState = 'locked'` → blue "👋 Matches In Progress — you can still make swaps"; table interactive when not in cooldown.

### 9. Current-Round Prediction, Round Locked

`atRoundNumber <= currentRound`, `roundState = 'locked'` → yellow "🔒 Round Locked"; read-only, no footer, no indicator.

### 10. Matches Completed, Scoring

`roundState = 'completed'` or `'finalized'`, `!seasonCompleted` → "⏳ Scoring" banner; table still rendered interactive-style but gated.

### 11. Last Round Advanced (Season Complete)

`roundState = 'advanced'`, `isLastRound` → `seasonCompleted = true`; header shows 📊 Finalized; historical view of the final round with "Gameweek N — You scored X points" banner (receipt icon); guests see "This season has completed."

### 12. Viewing a Historical Round

`!isCurrentRound` → "📊 Gameweek N — You scored X points" (or "Results pending"), historical view, round navigation with Jump to Current.

### 13. Setup Mode

`seasonInSetupMode` → 🛠️ banner; `seasonEditingAllowed = false` disables the create-entry banner, bonus/opening banners, status indicator, swaps, and the actions footer, whatever the round state.

## Implementation Files

**Templates**

- `api/src/main/resources/templates/predictions.html` — page, header indicators, season-phase banners, navigation, table wiring
- `api/src/main/resources/templates/fragments/access-banners.html` — all access banners + status indicator
- `api/src/main/resources/templates/fragments/swap-instructions.html`, `fragments/prediction-actions.html` — swap-limit copy (5 / 2 / 1 per 24h), submit/update buttons
- `api/src/main/resources/templates/fragments/round-navigation.html`, `fragments/prediction-table.html`, `fragments/prediction-historical-view.html`

**Controllers / Use Cases**

- `api/.../web/predictions/userpredictions/UserPredictionsController.java` — builds every template flag (`handleSuccess`), `SwapStatusDTO`
- `api/.../web/predictions/userpredictions/GetUserPredictionUseCase.java` — access mode, `seasonCompleted` derivation, `showAsHistorical`, opening-window computation, pre-season-registration cooldown override
- `api/.../rest/prediction/createprediction/CreatePredictionUseCase.java` — join plans, pre-season registration & merge, initial-swap allowance
- `api/.../rest/prediction/makeswap/MakeSwapUseCase.java` — server-side cooldown/opening-window enforcement
- `api/.../rest/round/finalizeround/FinalizeRoundUseCase.java` — round submissions/results, `countSwapsInRound`

**Domain (model module)**

- `model/.../domain/Season.java`, `SeasonState.java` — season phases, setup mode
- `model/.../domain/SwapCooldown.java` — 24h cooldown value object
- `model/.../domain/SeasonPrediction.java` — `isPreSeasonRegistration()`, `openingCommittedRound`

## Notes

- Banner copy and emojis in this doc are illustrative; conditions are authoritative.
- Banners are effectively mutually exclusive by design — one primary banner at a time.
- Guests never see round navigation, the status indicator, or the actions footer.
- The code flag for the "Next Swap Available" banner is still named `firstSwapBonus`.
- `seasonCompleted` = last round is current and `ADVANCED`; `season.completed` itself is an explicit admin action the UI doesn't read.
- `seasonEditingAllowed` (`!setupMode && (inPlay || (preSeason && !registered))`) gates every editing affordance and most banners.
- Thymeleaf best practices (explicit boolean comparisons, `th:block` around `th:if` + `th:replace`) are documented in `docs/backend-dev.md`.

# Prediction Page UI States & Banners

This document explains the complex UI state management and banner logic for the predictions page (`/predictions/user/me`, `/predictions/user/guest`, `/predictions/user/{userId}`).

## Table of Contents
- [Game Mechanics Overview](#game-mechanics-overview)
- [Round States](#round-states)
- [User Access Modes](#user-access-modes)
- [Banner Display Logic](#banner-display-logic)
- [Decision Tree](#decision-tree)
- [Examples by Scenario](#examples-by-scenario)

## Game Mechanics Overview

### Core Concepts

**Round Lifecycle:**
1. **Open** - Users can create and modify predictions freely
2. **Locked** - Matches in progress, predictions locked (except first swap bonus)
3. **Completed** - All matches finished, awaiting scoring
4. **Finalized** - Results scored and displayed

**Prediction Rules:**
- Users must create an initial prediction during the Open or Locked state
- After initial submission, users get ONE free swap (first swap bonus)
- Subsequent swaps have a 24-hour cooldown
- Predictions cannot be modified once round is finalized
- Last round of season has special messaging

### First Swap Bonus

When a user makes their initial prediction, they get one immediate swap opportunity:
- **Condition**: `initialPredictionMade = true && swapCount = 0 && canSwap = true`
- **Benefit**: Can swap teams without waiting for 24h cooldown
- **After use**: 24h cooldown applies to all future swaps

### Swap Cooldown

After using the first swap bonus or any subsequent swap:
- Users must wait 24 hours before next swap
- **Condition**: `initialPredictionMade = true && swapCount > 0 && canSwap = false`
- Countdown timer shown: "Cooldown active - 23h remaining"

## Round States

| State | Description | User Actions |
|-------|-------------|--------------|
| `open` | Round is open for predictions | Create prediction, swap freely |
| `locked` | Matches in progress | Create initial prediction only, first swap bonus available |
| `completed` | Matches finished, scoring pending | Same as locked |
| `finalized` | Results scored and published | Read-only, view scores |

**State Indicator (shown in page header):**
- 🟢 Open (green)
- 🔒 Locked (yellow, for locked/completed)
- ⏳ Finalizing (orange, when `roundState == 'finalized' && seasonCompleted != true`) — temporary state before round advances to next
- 📊 Finalized (blue, when `roundState == 'finalized' && seasonCompleted == true`) — last round, season is done

## User Access Modes

| Access Mode | Description | Can Swap | Can Create | Read Only |
|-------------|-------------|----------|------------|-----------|
| `READONLY_GUEST` | Unauthenticated user | ❌ | ❌ | ✅ |
| `CAN_CREATE_ENTRY` | Authenticated, no prediction yet | ❌ | ✅ | ❌ |
| `EDITABLE` | Can swap (no cooldown) | ✅ | ❌ | ❌ |
| `READONLY_COOLDOWN` | In cooldown period | ❌ | ❌ | ✅ (can view only) |
| `READONLY_VIEWING_OTHER` | Viewing another user's prediction | ❌ | ❌ | ✅ |
| `READONLY_USER_NOT_FOUND` | Requested user doesn't exist | ❌ | ❌ | ✅ |

## Banner Display Logic

### 1. Guest Banner (Yellow with Info Icon)

**Condition:** `isGuest == true`

**Messages vary by round state:**

- **Current Round + Open:**
  ```
  Guest Preview
  Tap teams to swap and preview your prediction! Sign up to save and join the competition.
  ```

- **Current Round + Locked/Completed (not last round):**
  ```
  Guest Preview
  👋 Matches In Progress - You can still arrange teams and preview your prediction!
  Sign up to save it and join the competition.
  ```

- **Current Round + Last Round + Locked/Completed:**
  ```
  Guest Preview
  This is the final round. Matches are in progress and the season is completing.
  Sign up to join future competitions.
  ```

- **Current Round + Finalized:**
  ```
  Guest Preview
  This round has been finalized. You can browse the results.
  Sign up to join future competitions.
  ```

- **Historical Round:**
  ```
  Guest Preview
  You're viewing a past gameweek. Browse results or check the current round.
  Sign up to join the competition.
  ```

### 2. Can Create Entry Banner (Green with Checkmark)

**Condition:** `canCreateEntry == true`

Shows when authenticated user hasn't made initial prediction yet.

**Message:**
```
✨ Ready to Predict! (or "Your Guest Prediction Imported!" if localStorage has guest prediction)
Arrange teams in your predicted order, then submit to join the competition.
```

### 3. First Swap Bonus Banner (Green with Checkmark)

**Condition:** `swapStatus != null && swapStatus.firstSwapBonus == true`

**Message:**
```
✨ First Swap Available!
Make your first swap without waiting 24 hours. After this, the 24h cooldown applies.
```

### 4. Locked State Banner (Yellow/Blue with Lock Icon)

**Condition:**
```
(isGuest == false || isGuest == null)
&& isCurrentRound == true
&& roundState == 'locked'
&& !(swapStatus != null && swapStatus.firstSwapBonus == true)
```

**Excludes:**
- Guests (they see Guest Banner instead)
- Users with first swap bonus (they see First Swap Bonus Banner instead)
- `completed` state (now handled by Scoring Banner)

**Three sub-states:**

**a) Last Round + Locked (Yellow):**
```
🔒 Season Completing
This is the final round. Matches are in progress and predictions are locked.
```

**b) Initial Prediction Available (Blue):**
```
👋 Matches In Progress
You can still create your prediction! It will be scored next round.
```
- Shows when `canCreateEntry == true`

**c) Already Predicted - Locked In (Yellow):**
```
🔒 Round Locked
Matches are in progress. Predictions are locked until results are finalized.
```
- Shows when user already predicted and is in cooldown

### 5. Scoring Banner (Yellow with Clock Icon)

**Condition:** `isCurrentRound == true && (roundState == 'completed' || roundState == 'finalized') && seasonCompleted != true`

Replaces the old "Finalized State Banner" for non-season-end cases. Shows while matches are completed and scoring is in progress, or when a mid-season round is finalized (brief window before round advances).

**Message:**
```
⏳ Scoring
Scoring predictions, points will be available shortly.
```

### 5b. Season Ended Banner (Blue with Document Icon)

**Condition:** `isCurrentRound == true && seasonCompleted == true`

Shows when the season has fully completed (last round finalized, no next round).

**Message:**
```
📊 Season Completed
The season has ended. Your final prediction has been graded.
You scored 156 points this round. (if totalScore available)
```

### 6. Viewing Other User Banner (Blue with User Icon)

**Condition:** `isViewingOther == true`

**Message:**
```
Viewing [username]'s prediction
This view is read-only.
```

### 7. User Not Found Banner (Red with X Icon)

**Condition:** `isUserNotFound == true`

**Message:**
```
User Not Found
The requested user doesn't exist or hasn't made any predictions.
```

### 8. Historical Round Info Banner (Blue with Clock Icon)

**Condition:**
```
(isCurrentRound == false || isCurrentRound == null)
&& (isGuest == false || isGuest == null)
&& (isViewingOther == false || isViewingOther == null)
&& (isUserNotFound == false || isUserNotFound == null)
```

**Message:**
```
📊 Historical Prediction
This is your prediction from Gameweek 18.
You scored 42 points this round. (if hasRoundResult)
This view is read-only.
```

### 9. Status Indicator (Ready/Cooldown)

**Condition:**
```
(isGuest == false || isGuest == null)
&& (isViewingOther == false || isViewingOther == null)
&& (isUserNotFound == false || isUserNotFound == null)
&& (canCreateEntry == false || canCreateEntry == null)
&& !(swapStatus != null && swapStatus.firstSwapBonus == true)
```

**Shows one of:**
- 🟢 **Ready to modify** - when `isCurrentRound && canSwap`
- 🟡 **Cooldown active - 23h remaining** - when `isCurrentRound && !canSwap`

## Decision Tree

```
Is user a guest?
├─ YES → Show Guest Banner (contextual message based on round state)
│
└─ NO (Authenticated User)
    │
    ├─ Is viewing another user?
    │  └─ YES → Show "Viewing Other User" Banner
    │
    ├─ Is user not found?
    │  └─ YES → Show "User Not Found" Banner
    │
    ├─ Can create initial entry?
    │  └─ YES → Show "Can Create Entry" Banner
    │      │
    │      └─ If round is locked/completed → Also show "👋 Matches In Progress" Banner
    │
    ├─ Has first swap bonus?
    │  └─ YES → Show "First Swap Bonus" Banner (green)
    │
    ├─ Is current round + locked?
    │  └─ YES → Show "Locked State" Banner
    │      ├─ Last round? → "🔒 Season Completing"
    │      └─ Not last round → "🔒 Round Locked" (if already predicted)
    │
    ├─ Is current round + (completed or finalized) + NOT season completed?
    │  └─ YES → Show "Scoring" Banner: "⏳ Scoring predictions..."
    │
    ├─ Is current round + season completed?
    │  └─ YES → Show "Season Ended" Banner: "📊 Season Completed"
    │
    └─ Is historical round?
       └─ YES → Show "Historical Round Info" Banner
```

## Examples by Scenario

### Scenario 1: Guest User Browsing Current Round (Open)

**State:**
- `isGuest = true`
- `isCurrentRound = true`
- `roundState = 'open'`

**Displays:**
- ✅ Guest Banner: "Tap teams to swap and preview your prediction! Sign up to save..."
- ✅ Guest prediction table (interactive with localStorage)
- ✅ Comparison options
- ❌ No round navigation (guests don't see this)

---

### Scenario 2: Guest User During Matches (Locked, Not Last Round)

**State:**
- `isGuest = true`
- `isCurrentRound = true`
- `roundState = 'locked'`
- `currentRound != lastRound`

**Displays:**
- ✅ Guest Banner: "👋 Matches In Progress - You can still arrange teams..."
- ✅ Guest prediction table
- ✅ Comparison options

---

### Scenario 3: New Authenticated User (No Prediction Yet, Round Open)

**State:**
- `isGuest = false`
- `canCreateEntry = true`
- `isCurrentRound = true`
- `roundState = 'open'`

**Displays:**
- ✅ Can Create Entry Banner (green): "Ready to Predict!"
- ✅ Interactive prediction table
- ✅ Submit button
- ✅ Round navigation

---

### Scenario 4: New User Creates Prediction During Locked State

**State:**
- `isGuest = false`
- `canCreateEntry = true`
- `isCurrentRound = true`
- `roundState = 'locked'`
- `currentRound != lastRound`

**Displays:**
- ✅ Can Create Entry Banner (green): "Ready to Predict!"
- ✅ Locked State Banner (blue): "👋 Matches In Progress - You can still create your prediction!"
- ✅ Interactive prediction table
- ✅ Submit button

---

### Scenario 5: User Just Submitted Initial Prediction During Locked Round (First Swap Available)

**State:**
- `isGuest = false`
- `canCreateEntry = false`
- `swapStatus.firstSwapBonus = true`
- `atRoundNumber = 23` (future round)
- `currentRound = 22`
- `isCurrentRound = true`
- `roundState = 'locked'`

**Displays:**
- ✅ First Swap Bonus Banner (green): "✨ First Swap Available!"
- ✅ Interactive prediction table (swappable — `atRoundNumber > currentRound`)
- ✅ Swap button enabled
- ❌ NO Locked State Banner (excluded by firstSwapBonus condition)

---

### Scenario 6a: Future Round Prediction, In Cooldown (Locked)

**State:**
- `isGuest = false`
- `canCreateEntry = false`
- `canSwap = false`
- `swapStatus.firstSwapBonus = false`
- `atRoundNumber = 23` (future round)
- `currentRound = 22`
- `isCurrentRound = true`
- `roundState = 'locked'`

**Displays:**
- ✅ Locked State Banner (blue): "👋 Matches In Progress - Your prediction will be scored next round. You can still make swaps."
- ✅ Status Indicator: "🟡 Cooldown active - 23h remaining"
- ✅ Prediction table (read-only during cooldown)
- ❌ No actions footer (cooldown active)

---

### Scenario 6b: Future Round Prediction, Cooldown Expired (Locked)

**State:**
- `isGuest = false`
- `canCreateEntry = false`
- `canSwap = true`
- `swapStatus.firstSwapBonus = false`
- `atRoundNumber = 23` (future round)
- `currentRound = 22`
- `isCurrentRound = true`
- `roundState = 'locked'`

**Displays:**
- ✅ Locked State Banner (blue): "👋 Matches In Progress - Your prediction will be scored next round. You can still make swaps."
- ✅ Status Indicator: "🟢 Ready to modify"
- ✅ Interactive prediction table (swappable — `atRoundNumber > currentRound`)
- ✅ Actions footer visible

---

### Scenario 6c: Current Round Prediction, Locked In

**State:**
- `isGuest = false`
- `canCreateEntry = false`
- `canSwap = true` (or false — doesn't matter, locked either way)
- `swapStatus.firstSwapBonus = false`
- `atRoundNumber = 22` (current round)
- `currentRound = 22`
- `isCurrentRound = true`
- `roundState = 'locked'`

**Displays:**
- ✅ Locked State Banner (yellow): "🔒 Round Locked - Matches are in progress. Predictions are locked until results are finalized."
- ❌ No status indicator (round not open, not future round)
- ✅ Prediction table (read-only, no swapping)
- ❌ No actions footer

---

### Scenario 7: User Can Swap (No Cooldown)

**State:**
- `isGuest = false`
- `canSwap = true`
- `swapStatus.firstSwapBonus = false` (used first swap already)
- `isCurrentRound = true`
- `roundState = 'open'`

**Displays:**
- ✅ Status Indicator: "🟢 Ready to modify"
- ✅ Interactive prediction table (swappable)
- ✅ Swap button enabled
- ❌ No banner (round is open, no special state)

---

### Scenario 8: Last Round, Matches In Progress

**State:**
- `isGuest = false`
- `canCreateEntry = false`
- `isCurrentRound = true`
- `roundState = 'locked'`
- `currentRound == lastRound`

**Displays:**
- ✅ Locked State Banner (yellow): "🔒 Season Completing - This is the final round..."
- ✅ Prediction table (read-only)

---

### Scenario 9: Matches Completed, Scoring In Progress

**State:**
- `isGuest = false`
- `isCurrentRound = true`
- `roundState = 'completed'`
- `seasonCompleted = false`

**Displays:**
- ✅ Scoring Banner (yellow): "⏳ Scoring - Scoring predictions, points will be available shortly."
- ✅ Interactive table (season not completed)
- ✅ Round navigation

---

### Scenario 10: Season Completed (Last Round Finalized)

**State:**
- `isGuest = false`
- `isCurrentRound = true`
- `roundState = 'finalized'`
- `seasonCompleted = true`
- `totalScore = 200`

**Displays:**
- ✅ Season Ended Banner (blue): "📊 Season Completed - Your final prediction has been graded. You scored 200 points..."
- ✅ Historical view with final scores (season completed triggers historical view)

---

### Scenario 11: Viewing Historical Round

**State:**
- `isGuest = false`
- `isCurrentRound = false`
- `viewingRound = 18`
- `hasRoundResult = true`
- `totalScore = 42`

**Displays:**
- ✅ Historical Round Info Banner (blue): "📊 Historical Prediction - This is your prediction from Gameweek 18. You scored 42 points..."
- ✅ Historical view with past scores
- ✅ Round navigation with "Jump to Current" button

---

### Scenario 12: Viewing Another User's Prediction

**State:**
- `isGuest = false`
- `isViewingOther = true`
- `targetDisplayName = "John Doe"`

**Displays:**
- ✅ Viewing Other User Banner (blue): "Viewing John Doe's prediction - This view is read-only."
- ✅ Prediction table (read-only)
- ❌ No swap buttons
- ❌ No navigation

## Implementation Files

- **Template**: `api/src/main/resources/templates/fragments/access-banners.html`
- **Controller**: `api/src/main/java/com/ligitabl/api/web/predictions/userpredictions/UserPredictionsController.java`
- **Use Case**: `api/src/main/java/com/ligitabl/api/web/predictions/userpredictions/GetUserPredictionUseCase.java`

## Key Conditions Reference

### Banner Exclusion Logic

**Locked State Banner excludes:**
```html
th:if="${(isGuest == false || isGuest == null)
     && isCurrentRound == true
     && roundState == 'locked'
     && !(swapStatus != null && swapStatus.firstSwapBonus == true)}"
```

**Status Indicator excludes:**
```html
th:if="${(isGuest == false || isGuest == null)
     && (isViewingOther == false || isViewingOther == null)
     && (isUserNotFound == false || isUserNotFound == null)
     && (canCreateEntry == false || canCreateEntry == null)
     && !(swapStatus != null && swapStatus.firstSwapBonus == true)}"
```

**Historical Banner shows only when:**
```html
th:if="${(isCurrentRound == false || isCurrentRound == null)
     && (isGuest == false || isGuest == null)
     && (isViewingOther == false || isViewingOther == null)
     && (isUserNotFound == false || isUserNotFound == null)}"
```

## Round Navigation Logic

### Overview

Round navigation (Previous/Next buttons and dropdown) is only shown to authenticated users who are not in error states. The navigation range is carefully controlled to prevent:
1. Navigating to rounds before the user made their first prediction
2. Navigating to future rounds that haven't occurred yet

### MinRound Calculation

The minimum round a user can navigate to is calculated dynamically:

```html
minRoundForNav=${canCreateEntry == true
  ? currentRound
  : (atRoundNumber != null && atRoundNumber <= currentRound
      ? atRoundNumber
      : currentRound)}
```

**Logic breakdown:**

1. **User can create entry** (`canCreateEntry = true`):
   - Set `minRound = currentRound`
   - Rationale: User has no predictions yet, so they can only view the current round

2. **User has predictions** (`canCreateEntry = false`):
   - If `atRoundNumber ≤ currentRound`: Use `atRoundNumber` as minRound
   - If `atRoundNumber > currentRound`: Use `currentRound` as minRound (cap to current)
   - Rationale: User can navigate back to when they first predicted, but never to future rounds

### MaxRound Calculation

The maximum round is always set to `currentRound`:

```html
maxRound=${currentRound}
```

This ensures future rounds never appear in the dropdown, even if predictions are made for upcoming rounds (e.g., during locked state).

### Edge Cases Handled

**Case 1: User makes prediction for next round during locked state**
- Scenario: Current round is 22 (locked), user makes prediction for round 23
- `atRoundNumber = 23`, `currentRound = 22`
- Without fix: Dropdown would show GW 23 (future round) ❌
- With fix: `minRound = min(23, 22) = 22`, dropdown shows only GW 22 ✅

**Case 2: User without initial prediction**
- Scenario: New user viewing locked round
- `canCreateEntry = true`, `currentRound = 22`
- `minRound = 22`, `maxRound = 22`
- Dropdown shows only: GW 22 (Current) ✅

**Case 3: User with historical predictions**
- Scenario: User first predicted in round 18, current round is 22
- `atRoundNumber = 18`, `currentRound = 22`
- `minRound = 18`, `maxRound = 22`
- Dropdown shows: GW 18, GW 19, GW 20, GW 21, GW 22 (Current) ✅

**Case 4: User with first swap bonus**
- Scenario: User just made initial prediction, has first swap available
- `canCreateEntry = false`, `atRoundNumber = 23`, `currentRound = 22`
- Since `23 > 22`, `minRound = 22`
- User sees only current round (can't navigate to future round 23)

### Navigation Component Parameters

From `predictions.html`:

```html
th:replace="~{fragments/round-navigation :: round-nav(
  '/predictions/user/me',           /* baseUrl */
  ${viewingRound},                  /* viewingRound */
  ${currentRound},                  /* maxRound - always currentRound */
  ${currentRound},                  /* currentRound - for labeling */
  '#prediction-page',               /* htmxTarget */
  'outerHTML swap:100ms settle:100ms show:window:top', /* htmxSwap */
  ${isCurrentRound == false || isCurrentRound == null}, /* showJumpToCurrent */
  false,                           /* dismissBanner - false to avoid marking results banner as viewed */
  'query',                         /* urlStyle */
  null,                            /* pathSuffix */
  ${minRoundForNav}                /* minRound - computed above */
)}"
```

### "Jump to Current" Button

Shows when:
```html
showJumpToCurrent=${isCurrentRound == false || isCurrentRound == null}
```

This button appears when viewing a historical round, allowing users to quickly return to the current round.

## Swap & Interactivity Rules During Locked Rounds

### Core Principle

The frontend enforces round state rules **on top of** backend user permissions:

```
Swaps allowed = userCanSwap AND (roundIsOpen OR predictionIsForFutureRound)
```

- **Backend** (`canSwap`): User's permission based on 24h cooldown state
- **Frontend**: Applies round state rules — both must be true

### The `atRoundNumber > currentRound` Exception

When a user's prediction is for a **future round** (e.g. they predicted during a locked round), they can continue swapping regardless of round state. This is because their prediction isn't being scored this round.

### Implementation

```html
<!-- Table interactivity -->
data-can-swap=${canSwap && (roundState == 'open' || (atRoundNumber != null && atRoundNumber > currentRound))}

<!-- Actions footer visibility -->
th:if="${(canSwap || canCreateEntry) && (roundState == 'open' || (atRoundNumber != null && atRoundNumber > currentRound))}"

<!-- Status indicator -->
th:if="${isCurrentRound && canSwap && (roundState == 'open' || (atRoundNumber != null && atRoundNumber > currentRound))}"
```

### Scenario Trace: Future Round Prediction (Locked Round)

User predicted for GW 23 during locked GW 22 (`atRoundNumber=23`, `currentRound=22`, `roundState='locked'`):

| Step | `canSwap` | `firstSwapBonus` | `atRoundNumber > currentRound` | Table interactive? | Actions visible? | Status indicator | Banner |
|------|-----------|-------------------|-------------------------------|-------------------|-----------------|-----------------|--------|
| Initial (first swap available) | true | true | true | Yes | Yes | Hidden (has own banner) | First Swap Available! |
| After first swap (cooldown) | false | false | true | No | No | Cooldown active | Matches In Progress |
| After cooldown expires | true | false | true | Yes | Yes | Ready to modify | Matches In Progress |
| After second swap (cooldown) | false | false | true | No | No | Cooldown active | Matches In Progress |

### Scenario Trace: Current Round Prediction (Locked Round)

User predicted for GW 22, round is now locked (`atRoundNumber=22`, `currentRound=22`, `roundState='locked'`):

| Step | `canSwap` | `atRoundNumber > currentRound` | Table interactive? | Actions visible? | Status indicator | Banner |
|------|-----------|-------------------------------|-------------------|-----------------|-----------------|--------|
| Round locked | true | false | No | No | Hidden | Round Locked |
| Round locked (in cooldown) | false | false | No | No | Hidden | Round Locked |

### Banner Messages by `atRoundNumber`

When round is locked and user has already predicted:

- **`atRoundNumber > currentRound`** (future round, blue banner):
  > 👋 Matches In Progress — Your prediction will be scored next round. You can still make swaps.

- **`atRoundNumber <= currentRound`** (current round, yellow banner):
  > 🔒 Round Locked — Matches are in progress. Predictions are locked until results are finalized.

## Interactive vs Historical View

The prediction table switches between interactive and historical views based on `seasonCompleted`:

- **Interactive table**: `isCurrentRound == true && seasonCompleted != true`
  - Shows for current round during open, locked, completed, and finalized states (as long as season is ongoing)
  - Table interactivity (swapping) is separately controlled by `canSwap` and round state

- **Historical table**: `(isCurrentRound == false || isCurrentRound == null) || (isCurrentRound == true && seasonCompleted == true)`
  - Shows for past rounds
  - Shows for current round when season is completed (last round finalized)
  - Displays scored results with hit indicators

**Key insight**: `seasonCompleted` replaces the old `roundState == 'finalized'` check for the view toggle. Mid-season finalized rounds advance `currentRoundId` to the next round, so the finalized round becomes historical naturally. Only the last round stays as `isCurrentRound` after finalization — that's the `seasonCompleted` case.

## Notes

- All boolean comparisons use explicit checks (`== true`, `== false || == null`) to avoid SpEL evaluation quirks
- Banners are mutually exclusive by design - only one primary banner shows at a time
- Guest users never see round navigation or swap controls
- First swap bonus takes precedence over locked state messaging
- `seasonCompleted` means: last round was current, was finalized, and no next round exists
- Scoring banner shows during `completed`/`finalized` states when season is not ended
- Season Ended banner replaces old "Season Completed" section of the Finalized banner
- Round navigation prevents viewing future rounds by capping `minRound` to `currentRound`
- Users without predictions can only view the current round

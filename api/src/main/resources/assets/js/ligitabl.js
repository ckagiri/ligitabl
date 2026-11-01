import './contests.js';

// HTMX Configuration
document.body.addEventListener("htmx:configRequest", (event) => {
    event.detail.headers["X-CSRF-TOKEN"] =
        document.querySelector('meta[name="_csrf"]')?.content;
});

// Smooth scroll on HTMX navigation
document.body.addEventListener("htmx:afterSwap", (event) => {
    if (event.detail.target.id === "main-content") {
        window.scrollTo({top: 0, behavior: "smooth"});
    }
});

window.Ligitabl = window.Ligitabl || {};
window.Ligitabl._MAX_INITIAL_SWAPS = 5;
window.Ligitabl._MAX_OPENING_SWAPS = 2;

// Mirrors the CSS block in base.html that blanks .swapping / .selected-pulse on touch: where those
// classes paint nothing, adding them and timing them out is work with no visible result.
window.Ligitabl._noRowWash = window.matchMedia('(hover: none), (max-width: 768px)').matches;

// --- Shared helpers for prediction components ---

window.Ligitabl._parseJSON = function (raw, fallback) {
    try {
        return JSON.parse(raw);
    } catch (e) {
        return fallback;
    }
};

/**
 * The team-lines block of the Final Table share text, from the rows given.
 *
 * ⚠️ Mirrors SharePredictionTextBuilder.appendTeamLines (HEAD_COUNT, TAIL_COUNT, the ellipsis line,
 * and the whole list when truncating saves nothing). A save reorders with no re-render, so the
 * lines are rebuilt client-side; change both together.
 */
window.Ligitabl._shareTeamLines = function (teams) {
    const HEAD = 5;
    const TAIL = 3;
    const line = (team, index) => `${index + 1} ${team.shortName || team.name || team.code}\n`;

    if (teams.length <= HEAD + TAIL) {
        return teams.map(line).join('');
    }
    return (
        teams.slice(0, HEAD).map(line).join('') +
        '...\n' +
        teams.slice(-TAIL).map((team, i) => line(team, teams.length - TAIL + i)).join('')
    );
};

window.Ligitabl._parseDataAttributes = function (el) {
    const p = Ligitabl._parseJSON;
    return {
        predictions: p(el?.dataset?.predictions, []),
        currentStandings: p(el?.dataset?.currentStandings, {}),
        fixtures: p(el?.dataset?.fixtures, {}),
        currentPoints: p(el?.dataset?.currentPoints, {}),
        currentGoalDifference: p(el?.dataset?.currentGoalDifference, {}),
        formData: p(el?.dataset?.form, {}) || {},
    };
};

// Opaque, per-submission token so a consumption can be applied exactly once even though the
// pages that write and read it are different page loads (and htmx can re-init the reader within
// one). crypto.randomUUID needs a secure context, which plain-HTTP local dev isn't.
window.Ligitabl._newNonce = function () {
    if (window.crypto?.randomUUID) return window.crypto.randomUUID();
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
};

// --- What-if swap reconciliation ---
//
// Shared by both sides: my-table reconciles the stored session when a submission succeeds, and
// the what-if page re-runs it against live state on its next load.

// The minimal set of swaps that turns `baseline` into `target`, as position-annotated entries
// in the shape the swap-history fragments render.
//
// Built from the net permutation, not the tap history: each cycle gives (cycleLength - 1) swaps,
// what getSwapCount() sums, so the list always matches the Swaps badge. Pairwise cancelling of
// taps can't: a 3-cycle is 2 swaps with no cancelling pair.
window.Ligitabl._minimalSwapEntries = function (baseline, target) {
    const work = (baseline || []).map((t) => ({...t}));
    const targetPosition = Object.fromEntries((target || []).map((t) => [t.code, t.position]));
    const log = [];
    // Team-major traversal, not position-major. Both are minimal, but they emit different pairs,
    // and these pairs are what submitChanges() sends and the server stores as swap history.
    for (const t of work) {
        const tgt = targetPosition[t.code];
        if (t.position === tgt) continue;
        const partner = work.find((w) => w.position === tgt);
        if (!partner) continue;
        const aFrom = t.position;
        const bFrom = partner.position;
        log.push({
            teamACode: t.code, teamAFrom: aFrom, teamATo: bFrom,
            teamBCode: partner.code, teamBFrom: bFrom, teamBTo: aFrom,
        });
        const tmp = t.position;
        t.position = partner.position;
        partner.position = tmp;
    }
    return log;
};

// The taps as made, rebased onto the current table. Not _minimalSwapEntries: swaps sharing a team
// don't commute, so a minimal set resequenced chronologically lands on a different table.
//
// Cancels a pair against the tap before it, as pushSwap() does. Adjacent only — the same pair with
// a swap in between acts on a table that moved, so those taps aren't redundant.
window.Ligitabl._displaySwapEntries = function (baseline, swapLog) {
    if (!Array.isArray(swapLog)) return [];

    const kept = [];
    for (const s of swapLog) {
        if (!s || !s.teamACode || !s.teamBCode) continue;
        const prev = kept[kept.length - 1];
        if (prev && Ligitabl._isSamePair(prev.teamACode, prev.teamBCode, s.teamACode, s.teamBCode)) kept.pop();
        else kept.push(s);
    }
    return Ligitabl._replaySwaps(baseline, kept).swapLog;
};

// A swap is its own inverse, so the pair is unordered: {A,B} and {B,A} are one operation.
window.Ligitabl._isSamePair = function (a1, b1, a2, b2) {
    return (a1 === a2 && b1 === b2) || (a1 === b2 && b1 === a2);
};

// Removes the log entries a submission has made real, matching on the unordered team-code pair:
// the only part that survives a rebase, since stored positions go stale when anything above moves.
// One-to-one against a multiset of the submitted pairs, so submitting A<->B once can't swallow two
// identical sandbox entries.
window.Ligitabl._consumeSwapPairs = function (log, pairs) {
    const key = (a, b) => (a < b ? `${a}|${b}` : `${b}|${a}`);
    const remaining = new Map();
    (pairs || []).forEach((p) => {
        if (!p || !p.teamACode || !p.teamBCode) return;
        const k = key(p.teamACode, p.teamBCode);
        remaining.set(k, (remaining.get(k) || 0) + 1);
    });

    const kept = [];
    let consumedCount = 0;
    for (const entry of log || []) {
        if (!entry || !entry.teamACode || !entry.teamBCode) continue; // malformed: drop
        const k = key(entry.teamACode, entry.teamBCode);
        const left = remaining.get(k) || 0;
        if (left > 0) {
            remaining.set(k, left - 1);
            consumedCount++;
            continue;
        }
        kept.push(entry);
    }
    return {kept, consumedCount};
};

// Replays surviving swaps onto a new baseline, recomputing each entry's positions as it goes.
// Entries carry absolute positions (teamAFrom/teamATo) that the swap-log fragments render, true
// only against the table they were made on; only the team-code pair survives a rebase.
//
// Pure, so it runs on a stored session (my-table, at submit) or live state (what-if, at restore).
// Returns the replayed team order too, which the what-if page uses as its table.
window.Ligitabl._replaySwaps = function (baseline, entries) {
    const teams = JSON.parse(JSON.stringify(baseline || []));
    const rebuilt = [];
    const stack = [];
    let moved = false;

    for (const entry of entries || []) {
        const i = teams.findIndex((t) => t.code === entry.teamACode);
        const j = teams.findIndex((t) => t.code === entry.teamBCode);
        // A team that's no longer on this table (it moved rounds, or the entry was half-written)
        // makes the swap unreplayable — drop it rather than render a half-swap or a "#4 -> #4".
        if (i < 0 || j < 0 || i === j) continue;

        const teamAFrom = teams[i].position;
        const teamBFrom = teams[j].position;
        const tmp = teams[i];
        teams[i] = teams[j];
        teams[j] = tmp;
        teams.forEach((t, idx) => (t.position = idx + 1));

        // Mirrors pushSwap's inverse-cancellation so the rebuilt stack is exactly what a live
        // user would have produced — undoLastSwap pops it and the log in lockstep.
        const top = stack[stack.length - 1];
        if (top && Ligitabl._isSamePair(top.a, top.b, entry.teamACode, entry.teamBCode)) stack.pop();
        else stack.push({a: entry.teamACode, b: entry.teamBCode});

        // Worth flagging to the user only if the replay actually landed this swap somewhere new;
        // a rebase that reproduces the same numbers has nothing to explain.
        if (entry.teamAFrom !== teamAFrom || entry.teamBFrom !== teamBFrom) moved = true;

        rebuilt.push({
            teamACode: entry.teamACode,
            teamAFrom,
            teamATo: teamBFrom,
            teamBCode: entry.teamBCode,
            teamBFrom,
            teamBTo: teamAFrom,
        });
    }

    return {teams, swapLog: rebuilt, swapStack: stack, moved};
};

// --- Persisted display preferences ---

window.Ligitabl._PREFS_KEY = "ligitabl.prefs";

window.Ligitabl._loadPrefs = function (key) {
    try {
        const saved = localStorage.getItem(key || Ligitabl._PREFS_KEY);
        if (saved) return JSON.parse(saved);
    } catch (e) {
        console.warn("Failed to load prefs:", e);
    }
    return null;
};

window.Ligitabl._savePrefs = function (prefs, key) {
    try {
        localStorage.setItem(key || Ligitabl._PREFS_KEY, JSON.stringify(prefs));
    } catch (e) {
        console.warn("Failed to save prefs:", e);
    }
};

// Cycle order for the delta column's reading. See deltaMode in _predictionBase.
window.Ligitabl._DELTA_MODES = ["normal", "inverted", "off"];

// Prefs written before the third state stored a boolean under deltaInverted; honour it once
// so an existing reader's inverted table does not silently reset.
window.Ligitabl._readDeltaMode = function (savedPrefs) {
    if (!savedPrefs) return "normal";
    if (Ligitabl._DELTA_MODES.includes(savedPrefs.deltaMode)) return savedPrefs.deltaMode;
    return savedPrefs.deltaInverted ? "inverted" : "normal";
};

// Shared base for predictionPage and guestPredictionPage
window.Ligitabl._predictionBase = function (parsed, userId, roundId) {
    const prefsKey = userId ? 'ligitabl.prefs.' + userId : 'ligitabl.prefs.guest';
    const savedPrefs = Ligitabl._loadPrefs(prefsKey);
    return {
        _prefsKey: prefsKey,
        roundId: roundId,
        teams: [],
        originalTeams: [],
        selectedTeam: null,
        swapStack: [],
        undoing: false,
        // Which list the Changes Made card shows: 'teams' (the default, "what did I move") or
        // 'swaps' (how those moves were made).
        changesView: 'teams',
        // Lives here rather than in the comparison-options fragment's own x-data so the table
        // toolbar can read it too.
        compareOptionsOpen: window.matchMedia('(min-width: 640px)').matches,
        positionsReversed: savedPrefs ? (savedPrefs.positionsReversed ?? false) : false,
        // How the delta column reads: 'normal', arrows say "move this row to match reality";
        // 'inverted', the team climbing or dropping toward the pick; 'off', only the accuracy
        // colour scale the read-only views use.
        deltaMode: Ligitabl._readDeltaMode(savedPrefs),
        alwaysHoverable: false,
        isInitialPrediction: false,
        showStandings: savedPrefs ? (savedPrefs.showStandings ?? true) : true,
        showFixtures: savedPrefs ? (savedPrefs.showFixtures ?? false) : false,
        showPoints: savedPrefs ? (savedPrefs.showPoints ?? false) : false,
        showGD: savedPrefs ? (savedPrefs.showGD ?? false) : false,
        showForm: savedPrefs ? (savedPrefs.showForm ?? false) : false,
        currentStandings: parsed.currentStandings,
        fixtures: parsed.fixtures,
        currentPoints: parsed.currentPoints,
        currentGoalDifference: parsed.currentGoalDifference,
        formData: parsed.formData,
        formPopup: null,
        formPopupClosing: false,

        showFormPopup(teamCode, teamName) {
            const entries = this.getForm(teamCode);
            if (entries.length > 0) {
                this.formPopupClosing = false;
                // Latest first — the modal answers "how are they doing lately". The inline badge
                // strip reads the same arrays and stays in kickoff order, hence the copy: reverse
                // mutates, and getForm hands back the live formData array.
                this.formPopup = { teamCode, teamName, entries: [...entries].reverse() };
            }
        },

        hideFormPopup() {
            if (!this.formPopup) return;
            this.formPopupClosing = true;
            setTimeout(() => {
                this.formPopup = null;
                this.formPopupClosing = false;
            }, 300);
        },

        formResultBefore(entry) {
            return entry.wasHome ? '' : entry.opponentCode + ' ' + this._formScore(entry) + ' ';
        },

        formResultAfter(entry) {
            return entry.wasHome ? ' ' + this._formScore(entry) + ' ' + entry.opponentCode : '';
        },

        _formScore(entry) {
            return entry.wasHome
                ? entry.goalsFor + '-' + entry.goalsAgainst
                : entry.goalsAgainst + '-' + entry.goalsFor;
        },

        fixtureScoreLabel(fixture) {
            return fixture.isHome
                ? fixture.goalsFor + '-' + fixture.goalsAgainst
                : fixture.goalsAgainst + '-' + fixture.goalsFor;
        },

        getCurrentPoints(teamCode) {
            const pts = this.currentPoints[teamCode];
            if (pts === undefined || pts === null) return "-";
            return pts;
        },

        getCurrentGD(teamCode) {
            const gd = this.currentGoalDifference[teamCode];
            if (gd === undefined || gd === null) return "-";
            return gd > 0 ? "+" + gd : gd;
        },

        getGDDirection(teamCode) {
            const gd = this.currentGoalDifference[teamCode];
            if (gd === undefined || gd === null) return null;
            if (gd > 0) return "positive";
            if (gd < 0) return "negative";
            return "neutral";
        },

        getFixtures(teamCode) {
            return this.fixtures[teamCode] || [];
        },

        hasFixtures(teamCode) {
            return this.getFixtures(teamCode).length > 0;
        },

        hasLiveFixture(teamCode) {
            return this.getFixtures(teamCode).some((f) => f.status === 'LIVE');
        },

        teamBadgeClasses(teamCode) {
            const fixtures = this.getFixtures(teamCode);
            if (this.hasLiveFixture(teamCode)) return 'bg-blue-50 text-blue-700';
            if (fixtures.some((f) => f.result === 'WIN')) return 'bg-green-50 text-green-700';
            if (fixtures.some((f) => f.result === 'LOSS')) return 'bg-red-50 text-red-700';
            if (fixtures.some((f) => f.result === 'DRAW')) return 'bg-yellow-100 text-yellow-700';
            if (fixtures.some((f) => f.status === 'POSTPONED')) return 'bg-violet-50 text-violet-700';
            return 'bg-gray-200 text-gray-700';
        },

        getForm(teamCode) {
            return this.formData[teamCode] || [];
        },

        isSelected(teamCode) {
            return this.selectedTeam === teamCode;
        },

        // Code -> position lookups: the row template calls them once per team per render, so linear
        // scans made each tap lag on slower phones. Valid while both the array identity and
        // _positionEpoch match; callers that reorder in place bump the epoch.
        _positionByCode: null,
        _positionSrc: null,
        _positionEpoch: 0,
        _positionCacheEpoch: -1,
        _originalPositionByCode: null,
        _originalPositionSrc: null,

        _reindexPositions() {
            const map = {};
            for (const t of this.teams) map[t.code] = t.position;
            this._positionByCode = map;
            this._positionSrc = this.teams;
            this._positionCacheEpoch = this._positionEpoch;
            return map;
        },

        _positions() {
            if (
                this._positionByCode &&
                this._positionSrc === this.teams &&
                this._positionCacheEpoch === this._positionEpoch
            ) {
                return this._positionByCode;
            }
            return this._reindexPositions();
        },

        _originalPositions() {
            if (this._originalPositionByCode && this._originalPositionSrc === this.originalTeams) {
                return this._originalPositionByCode;
            }
            const map = {};
            for (const t of this.originalTeams) map[t.code] = t.position;
            this._originalPositionByCode = map;
            this._originalPositionSrc = this.originalTeams;
            return map;
        },

        isDirty(teamCode) {
            const pos = this._positions()[teamCode];
            const orig = this._originalPositions()[teamCode];
            if (pos === undefined || orig === undefined) return false;
            return pos !== orig;
        },

        getDirtyCount() {
            return this.teams.filter((t) => this.isDirty(t.code)).length;
        },

        getSwapCount() {
            // Cycle decomposition: a cycle of length k needs k - 1 swaps.
            const visited = new Set();
            let swapCount = 0;

            // Reverse of _positions(); built once per call rather than scanned per cycle step.
            const origPositions = this._originalPositions();
            const codeAtPosition = {};
            for (const t of this.teams) codeAtPosition[t.position] = t.code;

            for (const team of this.teams) {
                if (visited.has(team.code) || !this.isDirty(team.code)) continue;

                let cycleLength = 0;
                let currentCode = team.code;

                while (!visited.has(currentCode)) {
                    visited.add(currentCode);
                    cycleLength++;

                    const originalPos = origPositions[currentCode];
                    const nextCode = codeAtPosition[originalPos];
                    if (!nextCode || nextCode === currentCode) break;

                    currentCode = nextCode;
                }

                if (cycleLength > 1) {
                    swapCount += cycleLength - 1;
                }
            }

            return swapCount;
        },

        // Default: guests follow MAX_INITIAL_SWAPS limit. Overridden by predictionPage for authenticated users.
        exceedsLimit() {
            return this.getSwapCount() > Ligitabl._MAX_INITIAL_SWAPS;
        },

        getChangedTeams() {
            return this.teams
                .filter((t) => this.isDirty(t.code))
                .map((t) => {
                    const from = this._originalPositions()[t.code];
                    const change = from - t.position;
                    return {
                        name: t.name,
                        code: t.code,
                        from,
                        to: t.position,
                        direction: change > 0 ? "up" : "down",
                        amount: Math.abs(change),
                    };
                })
                .sort((a, b) => a.from - b.from);
        },

        // The swaps behind the current diff, for the Changes Made card. Shares one helper with
        // submitChanges() so the card, the Swaps badge and the request all describe the same set
        // — the tap history would match none of them.
        getSwapEntries() {
            return Ligitabl._minimalSwapEntries(this.originalTeams, this.teams);
        },

        getPositionChange(teamCode) {
            const pos = this._positions()[teamCode];
            const orig = this._originalPositions()[teamCode];
            if (pos === undefined || orig === undefined) return null;
            const change = orig - pos;
            if (change === 0) return null;
            return change > 0 ? `↑${change}` : `↓${Math.abs(change)}`;
        },

        getSelectedTeamName() {
            if (!this.selectedTeam) return null;
            const team = this.teams.find((t) => t.code === this.selectedTeam);
            return team ? team.name : null;
        },

        getSelectedTeamShortName() {
            if (!this.selectedTeam) return null;
            const team = this.teams.find((t) => t.code === this.selectedTeam);
            return team ? (team.shortName || team.name) : null;
        },

        pushSwap(codeA, codeB) {
            const top = this.swapStack[this.swapStack.length - 1];
            if (top && Ligitabl._isSamePair(top.a, top.b, codeA, codeB)) {
                this.swapStack.pop();
            } else {
                this.swapStack.push({ a: codeA, b: codeB });
            }
        },

        _swapTeamsDirect(codeA, codeB, onSwapped) {
            const index1 = this.teams.findIndex((t) => t.code === codeA);
            const index2 = this.teams.findIndex((t) => t.code === codeB);
            if (index1 < 0 || index2 < 0) return;
            const temp = this.teams[index1];
            this.teams[index1] = this.teams[index2];
            this.teams[index2] = temp;
            // Only the two swapped slots change index, and position is always index + 1 — renumbering
            // all 20 would rewrite 18 unchanged values, invalidating every row's bindings per tap.
            this.teams[index1].position = index1 + 1;
            this.teams[index2].position = index2 + 1;
            this._positionEpoch++;
            if (onSwapped) onSwapped();
            if (Ligitabl._noRowWash) return;
            // After Alpine has updated the DOM, highlight the stable re-rendered rows
            this.$nextTick(() => {
                const row1 = document.querySelector(`[data-team-code='${codeA}']`);
                const row2 = document.querySelector(`[data-team-code='${codeB}']`);
                if (row1) row1.classList.add("swapping");
                if (row2) row2.classList.add("swapping");
                setTimeout(() => {
                    if (row1) row1.classList.remove("swapping");
                    if (row2) row2.classList.remove("swapping");
                }, 400);
            });
        },

        canUndo() {
            return this.swapStack.length > 0 && this.selectedTeam === null;
        },

        getActualPosition(teamCode) {
            return this.currentStandings[teamCode] || "?";
        },

        // Row order for rendering only.
        displayTeams() {
            if (!this.positionsReversed || !this.showStandings) return this.teams;
            const rank = (team) => {
                const actual = this.getActualPosition(team.code);
                return actual === "?" ? Number.MAX_SAFE_INTEGER : actual;
            };
            return [...this.teams].sort((a, b) => rank(a) - rank(b));
        },

        _canInteractRaw: false,
        get canInteract() {
            return this._canInteractRaw && !this.positionsReversed;
        },

        // Whether a tap would do anything — mirrors this component's teamClick gate. Overridden by
        // guestPredictionPage, which gates on positionsReversed alone (it never sets
        // _canInteractRaw, so canInteract is always false there).
        canPressRow() {
            return this.canInteract;
        },

        // Acknowledge the touch on pointerdown: the browser holds `click` back while it decides
        // whether the gesture is a scroll, and that gap is the delay the user feels. Only for the
        // swap tap (a team already selected), the one where rows move and nothing else marks the
        // press; dimming the first tap would just flicker before its own feedback.
        _pressRow(el, teamCode) {
            if (!el || !this.canPressRow()) return;
            if (this.selectedTeam === null || this.isSelected(teamCode)) return;
            el.classList.add('pressed');
        },

        _releaseRow(el) {
            if (el) el.classList.remove('pressed');
        },

        // The server's permission on its own, ignoring which view is showing. Status text
        // about the round itself — cooldown active, round not open — stays true whichever
        // table you are looking at, so it must not hide when the standings view is on.
        get roundAllowsEditing() {
            return this._canInteractRaw;
        },

        toggleStandingsView() {
            this.positionsReversed = !this.positionsReversed;
            // Drop a half-made selection — it can't be completed here, and a highlighted
            // row you can't act on reads as stuck. Unsaved swaps deliberately survive.
            if (this.positionsReversed) this.selectedTeam = null;
            // deltaMode deliberately survives the view switch — it lasts the visit, and the
            // legend shows in both views to explain however the delta is currently reading.
        },

        getDelta(teamCode) {
            const pos = this._positions()[teamCode];
            const actual = this.getActualPosition(teamCode);
            if (pos === undefined || actual === "?") return "-";
            return Math.abs(pos - actual);
        },

        getDeltaDirection(teamCode) {
            const pos = this._positions()[teamCode];
            const actual = this.getActualPosition(teamCode);
            if (pos === undefined || actual === "?") return null;
            return pos > actual ? "up" : "down";
        },

        // The delta as this table is currently displaying it. getDeltaDirection stays the raw
        // fact — scoring and the read-only views read that one — so the mode toggle cannot
        // leak into anything that computes a result.
        displayedDeltaDirection(teamCode) {
            // null is also what a row with no actual position returns, and the markup already
            // draws no arrow for it — so 'off' needs nothing beyond this.
            if (this.deltaMode === "off") return null;
            const dir = this.getDeltaDirection(teamCode);
            if (dir === null || this.deltaMode !== "inverted") return dir;
            return dir === "up" ? "down" : "up";
        },

        // Distance from reality rather than direction — the scale the read-only views use.
        deltaAccuracyClass(teamCode) {
            const d = this.getDelta(teamCode);
            if (d === 0) return "text-green-600";
            return d <= 2 ? "text-yellow-600" : "text-red-600";
        },

        // Colour for the delta number in whatever mode the table is in.
        deltaTextClass(teamCode) {
            if (this.deltaMode === "off") return this.deltaAccuracyClass(teamCode);
            if (this.getDelta(teamCode) === 0) return "text-green-600";
            return this.displayedDeltaDirection(teamCode) === "down"
                ? "text-red-600"
                : "text-green-600";
        },

        cycleDeltaMode() {
            const modes = Ligitabl._DELTA_MODES;
            this.deltaMode = modes[(modes.indexOf(this.deltaMode) + 1) % modes.length];
        },

        _performSwap(teamCode) {
            const team1Code = this.selectedTeam;
            const team2Code = teamCode;
            const index1 = this.teams.findIndex((t) => t.code === team1Code);
            const index2 = this.teams.findIndex((t) => t.code === team2Code);
            if (index1 < 0 || index2 < 0) {
                this.selectedTeam = null;
                return;
            }
            this.selectedTeam = null;
            this.pushSwap(team1Code, team2Code);
            this._swapTeamsDirect(team1Code, team2Code);
        },

        _selectTeam(teamCode) {
            this.selectedTeam = teamCode;
            if (Ligitabl._noRowWash) return;
            this.$nextTick(() => {
                const row = document.querySelector(`[data-team-code='${teamCode}']`);
                if (row) {
                    row.classList.add("selected-pulse");
                    setTimeout(() => row.classList.remove("selected-pulse"), 300);
                }
            });
        },

        _saveTimer: null,
        _pendingSaveKey: null,

        _writeToStorage(key) {
            try {
                localStorage.setItem(key, JSON.stringify({
                    roundId: this.roundId,
                    teams: this.teams,
                    swapStack: this.swapStack,
                }));
            } catch (e) {
                console.warn("Failed to save prediction:", e);
            }
        },

        // Deferred off the tap: serialising the whole table synchronously inside the click handler
        // delayed the frame the user is waiting on. Flushed on pagehide (see _installSaveFlush) and
        // before submit, so nothing is lost if the tab goes away inside the window.
        _saveToStorage(key) {
            this._pendingSaveKey = key;
            if (this._saveTimer) clearTimeout(this._saveTimer);
            this._saveTimer = setTimeout(() => {
                this._saveTimer = null;
                this._pendingSaveKey = null;
                this._writeToStorage(key);
            }, 200);
        },

        _flushSave() {
            if (!this._saveTimer) return;
            clearTimeout(this._saveTimer);
            this._saveTimer = null;
            const key = this._pendingSaveKey;
            this._pendingSaveKey = null;
            if (key) this._writeToStorage(key);
        },

        // Once per component; pagehide covers the mobile case where the tab is frozen without unload.
        _installSaveFlush() {
            if (this._saveFlushInstalled) return;
            this._saveFlushInstalled = true;
            const flush = () => this._flushSave();
            window.addEventListener('pagehide', flush);
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState === 'hidden') flush();
            });
        },

        _clearStorage(key) {
            // Drop any queued write first, or it lands after the clear and resurrects the state.
            if (this._saveTimer) {
                clearTimeout(this._saveTimer);
                this._saveTimer = null;
                this._pendingSaveKey = null;
            }
            try {
                localStorage.removeItem(key);
            } catch (e) {
                console.warn("Failed to clear prediction:", e);
            }
        },

        reset() {
            this.teams = JSON.parse(JSON.stringify(this.originalTeams));
            this.selectedTeam = null;
            this.swapStack = [];
        },
    };
};

window.Ligitabl._mapServerPredictions = function (predictions) {
    return (Array.isArray(predictions) ? predictions : []).map((p) => ({
        position: p.position,
        code: p.teamCode,
        name: p.teamName,
        shortName: p.teamShortName,
        shorterName: p.teamShorterName || p.teamShortName,
        crestUrl: p.crestUrl,
        originalPosition: p.position,
    }));
};

// --- Authenticated Prediction Page ---

window.Ligitabl.predictionPage = function (el) {
    const parsed = Ligitabl._parseDataAttributes(el);
    const predictions = parsed.predictions;
    const canSwapRaw = el?.dataset?.canSwap ?? "false";
    const canInteractRaw = el?.dataset?.canInteract ?? "false";
    const roundOpenRaw = el?.dataset?.roundOpen ?? "false";
    const isLastRoundRaw = el?.dataset?.isLastRound ?? "false";
    const isInitialRaw = el?.dataset?.isInitialPrediction ?? "false";
    const isOpeningRoundRaw = el?.dataset?.isOpeningRound ?? "false";
    const isPreSeasonRegistrationRaw = el?.dataset?.isPreSeasonRegistration ?? "false";
    const canSwap = canSwapRaw === "true" || canSwapRaw === "True";
    const canInteract = canInteractRaw === "true" || canInteractRaw === "True";
    const isRoundOpen = roundOpenRaw === "true" || roundOpenRaw === "True";
    const isLastRound = isLastRoundRaw === "true" || isLastRoundRaw === "True";
    const isInitialPrediction = isInitialRaw === "true" || isInitialRaw === "True";
    const isOpeningRound = isOpeningRoundRaw === "true" || isOpeningRoundRaw === "True";
    const isPreSeasonRegistration = isPreSeasonRegistrationRaw === "true" || isPreSeasonRegistrationRaw === "True";
    const MAX_INITIAL_SWAPS = Ligitabl._MAX_INITIAL_SWAPS;
    const MAX_OPENING_SWAPS = Ligitabl._MAX_OPENING_SWAPS;

    const userId = el?.dataset?.userId || 'unknown';
    const roundId = el?.dataset?.roundId || 'unknown';
    const GUEST_STORAGE_KEY = "ligitabl.guestPrediction";
    const AUTH_STORAGE_KEY = "ligitabl.prediction." + userId;

    function _validateSaved(saved) {
        if (!saved) return false;
        if (saved.roundId !== roundId) return false;
        // Team codes must still match the server set.
        const serverCodes = new Set(predictions.map((p) => p.teamCode));
        const teams = _extractTeams(saved);
        const savedCodes = new Set(teams.map((p) => p.code));
        return (
            serverCodes.size === savedCodes.size &&
            [...serverCodes].every((c) => savedCodes.has(c))
        );
    }

    function loadGuestPrediction() {
        try {
            const saved = localStorage.getItem(GUEST_STORAGE_KEY);
            if (saved) {
                const parsed = JSON.parse(saved);
                if (_validateSaved(parsed)) return parsed;
            }
        } catch (e) {
            console.warn("Failed to load guest prediction:", e);
        }
        return null;
    }

    function loadAuthPrediction() {
        try {
            const saved = localStorage.getItem(AUTH_STORAGE_KEY);
            if (saved) {
                const parsed = JSON.parse(saved);
                if (_validateSaved(parsed)) return parsed;
            }
        } catch (e) {
            console.warn("Failed to load auth prediction:", e);
        }
        return null;
    }

    function _extractTeams(saved) {
        return Array.isArray(saved) ? saved : (saved?.teams ?? []);
    }

    function _extractSwapStack(saved) {
        return Array.isArray(saved) ? [] : (saved?.swapStack ?? []);
    }

    const serverDataByCode = {};
    (Array.isArray(predictions) ? predictions : []).forEach((p) => {
        serverDataByCode[p.teamCode] = {
            position: p.position,
            code: p.teamCode,
            name: p.teamName,
            crestUrl: p.crestUrl,
        };
    });

    const base = Ligitabl._predictionBase(parsed, userId, roundId);

    // The swaps tried in the what-if sandbox for this round, shown as a read-only reminder. Same
    // key whatIfPage writes (ligitabl.whatif.<userId>.<roundId>), so it is scoped to this user and
    // round. Only reconcileWhatIfSwaps() below writes it. Entries are already in the shape the
    // swap-history fragment renders.
    const WHAT_IF_STORAGE_KEY = `ligitabl.whatif.${userId}.${roundId}`;

    // Rebased onto this page's server table, so it reads the same as the what-if page's list.
    function loadWhatIfSwaps(originalTeams) {
        try {
            const raw = localStorage.getItem(WHAT_IF_STORAGE_KEY);
            if (!raw) return [];
            const saved = JSON.parse(raw);
            if (!originalTeams?.length) return [];

            return Ligitabl._displaySwapEntries(originalTeams, saved?.swapLog);
        } catch (e) {
            console.warn("Failed to load what-if swaps:", e);
            return [];
        }
    }

    // Once a submission lands, its swaps are the table. Drop just the entries that went in and
    // replay the rest onto the submitted table, rather than wiping the sandbox. Done here, not on
    // the what-if page, because `newBaseline` is the new real table, so this page's own list is
    // right the moment it reloads. consumedCount lets what-if explain it on its next load.
    function reconcileWhatIfSwaps(pairs, newBaseline) {
        try {
            const cleaned = (Array.isArray(pairs) ? pairs : [])
                .filter((p) => p && p.teamACode && p.teamBCode && p.teamACode !== p.teamBCode)
                .map((p) => ({teamACode: p.teamACode, teamBCode: p.teamBCode}));
            if (cleaned.length === 0) return;

            const raw = localStorage.getItem(WHAT_IF_STORAGE_KEY);
            if (!raw) return;
            const saved = JSON.parse(raw);
            if (!saved || typeof saved !== "object") return;
            // No sandbox swaps to reconcile: nothing to consume, and what-if's own server
            // baseline already gives it the new table.
            if (!Array.isArray(saved.swapLog) || saved.swapLog.length === 0) return;

            const {kept, consumedCount} = Ligitabl._consumeSwapPairs(saved.swapLog, cleaned);
            // Runs even when nothing matched: the baseline moved regardless, so the survivors
            // still have to be re-seated on top of the new table.
            const replayed = Ligitabl._replaySwaps(newBaseline, kept);

            saved.swapLog = replayed.swapLog;
            saved.teams = replayed.teams;
            saved.swapStack = replayed.swapStack;
            // The projection was computed against the old arrangement; what-if recomputes it on
            // its next load rather than showing a score that no longer matches its own table.
            delete saved.hasComputed;
            delete saved.appliedScores;
            // What-if reads these once, to explain the change, then clears them.
            saved.submitOutcome = {
                nonce: Ligitabl._newNonce(),
                consumedCount,
                rebased: replayed.moved,
            };
            // Older sessions can still carry this marker, which would announce a sandbox reset
            // that no longer happens.
            delete saved.swapsClearedBySubmit;
            localStorage.setItem(WHAT_IF_STORAGE_KEY, JSON.stringify(saved));
        } catch (e) {
            console.warn("Failed to reconcile what-if swaps:", e);
        }
    }

    return Object.assign(base, {
        canSwap,
        // Raw server permission — Assigning canInteract here would clobber the getter.
        _canInteractRaw: canInteract,
        isRoundOpen,
        isLastRound,
        isInitialPrediction,
        isOpeningRound,
        isPreSeasonRegistration,
        isSaving: false,
        errorMessage: null,
        importedFromGuest: false,
        whatIfSwaps: [],

        init() {
            this._installSaveFlush();
            if (isInitialPrediction || isOpeningRound || isPreSeasonRegistration) {
                // 1. Auth localStorage first, pre-season registration included: the user has swapped
                // since signing up. _validateSaved already drops stale rounds and team sets.
                const authPrediction = loadAuthPrediction();
                if (authPrediction) {
                    this.teams = _extractTeams(authPrediction).map((t, idx) => ({...t, position: idx + 1}));
                    this.swapStack = _extractSwapStack(authPrediction);
                }

                // 2. No auth data — fall back to guest localStorage. Only for a genuine
                // initial-prediction signup, never for pre-season registration or the
                // opening round.
                if (this.teams.length === 0 && isInitialPrediction && !isOpeningRound) {
                    const guestPrediction = loadGuestPrediction();
                    if (guestPrediction) {
                        this.teams = _extractTeams(guestPrediction).map((t, idx) => {
                            const serverData = serverDataByCode[t.code];
                            return {
                                position: idx + 1,
                                code: t.code,
                                name: t.name,
                                crestUrl: t.crestUrl,
                                originalPosition: serverData ? serverData.position : idx + 1,
                            };
                        });
                        this.swapStack = _extractSwapStack(guestPrediction);
                        this.importedFromGuest = true;
                    }
                }
            } else {
                // Non-initial: load auth localStorage only
                const authPrediction = loadAuthPrediction();
                if (authPrediction) {
                    this.teams = _extractTeams(authPrediction).map((t, idx) => ({...t, position: idx + 1}));
                    this.swapStack = _extractSwapStack(authPrediction);
                }
            }

            // 3. Fall back to server predictions
            if (this.teams.length === 0) {
                this.teams = Ligitabl._mapServerPredictions(predictions);
            }

            // Past the one-time import above, guest storage is never the source of truth here, so
            // it is stale whichever branch populated this.teams.
            const hadGuestStorage = localStorage.getItem(GUEST_STORAGE_KEY) !== null;
            this._clearStorage(GUEST_STORAGE_KEY);
            if (hadGuestStorage && !this.importedFromGuest) {
                // Banners set `imported` in their own x-init, which can run before this. The
                // importedFromGuest case keeps its banner; reset()/submit clear it later.
                window.dispatchEvent(new CustomEvent('guest-storage-cleared'));
            }

            // originalTeams always reflects server state — diffs are against what was last submitted
            this.originalTeams = Ligitabl._mapServerPredictions(predictions);

            // After originalTeams above: that is the baseline the sandbox diff is derived from.
            this.whatIfSwaps = loadWhatIfSwaps(this.originalTeams);

            // Persist display preferences
            const savePrefs = () => Ligitabl._savePrefs({
                showStandings: this.showStandings,
                showFixtures: this.showFixtures,
                showPoints: this.showPoints,
                showGD: this.showGD,
                showForm: this.showForm,
                positionsReversed: this.positionsReversed,
                deltaMode: this.deltaMode,
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
            this.$watch("showForm", savePrefs);
            this.$watch("positionsReversed", savePrefs);
            this.$watch("deltaMode", savePrefs);
        },

        teamClick(teamCode) {
            if (!this.canInteract) return;

            if (this.selectedTeam === null) {
                this._selectTeam(teamCode);
                return;
            }
            if (this.selectedTeam === teamCode) {
                this.selectedTeam = null;
                return;
            }
            this._performSwap(teamCode);
            this._saveToStorage(AUTH_STORAGE_KEY);
        },

        canUpdate() {
            const swapCount = this.getSwapCount();
            const treatAsInitial = this.isInitialPrediction || this.isPreSeasonRegistration;
            if (!treatAsInitial && swapCount === 0) return false;
            if (treatAsInitial) {
                if (swapCount > MAX_INITIAL_SWAPS) return false;
            } else if (this.isOpeningRound) {
                if (swapCount > MAX_OPENING_SWAPS) return false;
            } else {
                if (swapCount > 1) return false;
            }
            return this.canSwap;
        },

        exceedsLimit() {
            if (this.isInitialPrediction || this.isPreSeasonRegistration) {
                return this.getSwapCount() > MAX_INITIAL_SWAPS;
            }
            if (this.isOpeningRound) {
                return this.getSwapCount() > MAX_OPENING_SWAPS;
            }
            return this.getSwapCount() > 1;
        },

        reset() {
            this.teams = JSON.parse(JSON.stringify(this.originalTeams));
            this.selectedTeam = null;
            this.swapStack = [];
            this._clearStorage(AUTH_STORAGE_KEY);
            if (this.importedFromGuest) {
                this._clearStorage(GUEST_STORAGE_KEY);
                this.importedFromGuest = false;
                window.dispatchEvent(new CustomEvent('guest-storage-cleared'));
            }
        },

        undoLastSwap() {
            if (!this.canUndo() || this.undoing) return;
            this.undoing = true;
            const last = this.swapStack.pop();
            setTimeout(() => {
                this._swapTeamsDirect(last.b, last.a, () => {
                    this._saveToStorage(AUTH_STORAGE_KEY);
                }); // reverse
                setTimeout(() => { this.undoing = false; }, 200);
            }, 200);
        },

        submitChanges() {
            // Settle any deferred write now: on success _clearStorage runs, and a timer firing
            // after that would write the just-submitted state back.
            this._flushSave();
            this.isSaving = true;
            const toast = document.getElementById("saving-toast");
            if (toast) toast.classList.remove("hidden");

            let url, body;

            // What reconcileWhatIfSwaps matches the sandbox against: the swaps actually tapped, in
            // the {teamACode, teamBCode} shape the sandbox log records.
            //
            // Not _derivedSwaps: that minimal reconstruction reaches the same table with pairs the
            // user never tapped, so once a team moves twice submitted swaps would survive as "not
            // submitted". Sliced to what each branch sends: the batch endpoints take the whole
            // stack, a standard swap only swapStack[0].
            const _submittedStack =
                this.isInitialPrediction || this.isPreSeasonRegistration || this.isOpeningRound
                    ? this.swapStack
                    : this.swapStack.slice(0, 1);
            const submittedPairs = _submittedStack.map((s) => ({teamACode: s.a, teamBCode: s.b}));

            // Minimal pairs from the net permutation — swapStack may hold redundant taps. Same
            // helper the Changes Made card renders, so what the user sees listed is what is sent.
            const _derivedSwaps = Ligitabl._minimalSwapEntries(this.originalTeams, this.teams)
                .map((e) => ({teamACode: e.teamACode, teamBCode: e.teamBCode}));

            if (this.isInitialPrediction || this.isPreSeasonRegistration) {
                url = "/seasonprediction";
                const _next = new URLSearchParams(window.location.search).get('next');
                if (_next) url += '?next=' + encodeURIComponent(_next);
                body = {swaps: _derivedSwaps};
            } else if (this.isOpeningRound) {
                url = "/seasonprediction/opening-swaps";
                body = {swaps: _derivedSwaps};
            } else {
                const entry = this.swapStack[0];
                url = "/seasonprediction/swap";
                body = {teamACode: entry.a, teamBCode: entry.b};
            }

            fetch(url, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(body),
            })
                .then((response) => response.json())
                .then((data) => {
                    if (data.success) {
                        this._clearStorage(AUTH_STORAGE_KEY);
                        if (this.importedFromGuest || ((this.isInitialPrediction || this.isPreSeasonRegistration) && !this.isOpeningRound)) {
                            this._clearStorage(GUEST_STORAGE_KEY);
                        }
                        // Only on success: a failed submit leaves the plan intact to retry from.
                        // this.teams is what was just submitted, so it's the new real table the
                        // surviving sandbox swaps get replayed onto.
                        reconcileWhatIfSwaps(submittedPairs, this.teams);
                        if (data.nextUrl) {
                            window.location.href = data.nextUrl;
                            return;
                        }
                        setTimeout(() => {
                            window.scrollTo({top: 0, behavior: "smooth"});
                        }, 300);
                        setTimeout(() => {
                            window.location.reload();
                        }, 800);
                    } else {
                        this.isSaving = false;
                        if (toast) toast.classList.add("hidden");
                        this.errorMessage = data.message || "Something went wrong";
                    }
                })
                .catch((error) => {
                    console.error("Error:", error);
                    this.isSaving = false;
                    if (toast) toast.classList.add("hidden");
                    this.errorMessage = "Failed to submit. Please check your connection and try again.";
                });
        },
    });
};

// Safety net for the row press state: the per-row pointerup/cancel/leave handlers miss the case
// where the pointer is released outside the window, which would strand a row dimmed. Cheap because
// the query only runs once a press is actually outstanding.
["pointerup", "pointercancel"].forEach((evt) => {
    window.addEventListener(evt, () => {
        const stuck = document.querySelectorAll(".prediction-row.pressed");
        if (stuck.length) stuck.forEach((el) => el.classList.remove("pressed"));
    });
});

// Re-init Alpine after HTMX swaps
document.body.addEventListener("htmx:afterSwap", (event) => {
    if (!window.Alpine || !event.detail?.target) return;
    const target = event.detail.target;
    if (!target.querySelector || !target.querySelector("[x-data]")) return;
    window.Alpine.initTree(target);
});

// Restoring from htmx's history cache replaces the DOM without firing afterSwap, so back/forward
// would otherwise land on markup Alpine never initialised — an inert page with cloaked content.
document.body.addEventListener("htmx:historyRestore", () => {
    if (!window.Alpine) return;
    document.querySelectorAll("[x-data]").forEach((el) => {
        if (!el._x_dataStack) window.Alpine.initTree(el);
    });
});

document.body.addEventListener('htmx:beforeRequest', function(e) {
    if (e.detail?.target?.id === 'user-detail-modal') {
        const spinner = document.getElementById('modal-loading');
        if (spinner) spinner.classList.remove('hidden');
    }
});

document.body.addEventListener('htmx:afterSwap', function(e) {
    if (e.detail?.target?.id === 'user-detail-modal') {
        const spinner = document.getElementById('modal-loading');
        if (spinner) spinner.classList.add('hidden');
    }
});

/**
 * Named toLocaleString option bags for [data-timestamp] elements. A closed set rather than JSON in
 * the attribute, so a template can't invent a new shape by accident.
 *
 * Changing `default` affects fragments/swap-history.html and matches.html.
 */
window.Ligitabl.TIMESTAMP_FORMATS = {
    default: { dateStyle: "medium", timeStyle: "short" },
    // "12 Aug 2026, 14:32" — the Final Table share card's footer, and the public page's settled
    // line, which sits beside it and must not disagree with it.
    settled: {
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    },
    // "12 Aug 2026" — swap history, where the clock time is noise: what matters is
    // which day a swap was made, not the minute.
    dateOnly: { day: "numeric", month: "short", year: "numeric" },
};

/**
 * Format one ISO instant in the viewer's own locale and timezone.
 *
 * Returns '' for absent/unparseable input so callers can omit the line rather than print "Invalid
 * Date". `format` names a key of TIMESTAMP_FORMATS; an unknown name falls back to `default`.
 */
window.Ligitabl.formatTimestamp = function (iso, format) {
    if (!iso) return "";
    try {
        const d = new Date(iso);
        if (isNaN(d.getTime())) return "";
        const options =
            Ligitabl.TIMESTAMP_FORMATS[format] || Ligitabl.TIMESTAMP_FORMATS.default;
        return d.toLocaleString(undefined, options);
    } catch (e) {
        return "";
    }
};

// Format [data-timestamp] elements to the user's local timezone and locale.
// Opt into a non-default shape with data-timestamp-format (see TIMESTAMP_FORMATS).
// Falls back to the ISO string if the date is invalid.
window.Ligitabl.formatTimestamps = function (root) {
    const scope = root && document.contains(root) ? root : document;
    const candidates = [];

    if (scope !== document && scope.matches?.("[data-timestamp]")) {
        candidates.push(scope);
    }
    scope.querySelectorAll("[data-timestamp]").forEach((el) => candidates.push(el));

    candidates.forEach((el) => {
        const iso = el.getAttribute("data-timestamp");
        if (!iso) return;
        const formatted = Ligitabl.formatTimestamp(iso, el.getAttribute("data-timestamp-format"));
        // Empty means unparseable — leave the ISO string as-is rather than blanking the element.
        if (formatted) el.textContent = formatted;
    });
};

document.addEventListener("DOMContentLoaded", () => Ligitabl.formatTimestamps());
document.body.addEventListener("htmx:afterSettle", () => Ligitabl.formatTimestamps());

// --- Guest Prediction Page (localStorage support) ---

window.Ligitabl.guestPredictionPage = function (el) {
    const STORAGE_KEY = "ligitabl.guestPrediction";
    const parsed = Ligitabl._parseDataAttributes(el);
    const serverPredictions = parsed.predictions;
    const roundId = el?.dataset?.roundId || 'unknown';

    function loadSavedPrediction() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved) {
                const p = JSON.parse(saved);
                if (p.roundId !== roundId) return null;
                const teams = Array.isArray(p) ? p : (p?.teams ?? []);
                const serverCodes = new Set(serverPredictions.map((s) => s.teamCode));
                const savedCodes = new Set(teams.map((s) => s.code));
                if (
                    serverCodes.size === savedCodes.size &&
                    [...serverCodes].every((c) => savedCodes.has(c))
                ) {
                    return p;
                }
            }
        } catch (e) {
            console.warn("Failed to load saved prediction:", e);
        }
        return null;
    }

    const base = Ligitabl._predictionBase(parsed, null, roundId);

    return Object.assign(base, {
        alwaysHoverable: true,
        isInitialPrediction: true,
        isOpeningRound: false,
        isPreSeasonRegistration: false,

        init() {
            this._installSaveFlush();
            const saved = loadSavedPrediction();
            if (saved) {
                const teams = Array.isArray(saved) ? saved : (saved?.teams ?? []);
                this.teams = teams.map((t, idx) => ({...t, position: idx + 1}));
                this.swapStack = Array.isArray(saved) ? [] : (saved?.swapStack ?? []);
            } else {
                this.teams = Ligitabl._mapServerPredictions(serverPredictions);
            }
            this.originalTeams = Ligitabl._mapServerPredictions(serverPredictions);

            // Persist display preferences
            const savePrefs = () => Ligitabl._savePrefs({
                showStandings: this.showStandings,
                showFixtures: this.showFixtures,
                showPoints: this.showPoints,
                showGD: this.showGD,
                showForm: this.showForm,
                positionsReversed: this.positionsReversed,
                deltaMode: this.deltaMode,
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
            this.$watch("showForm", savePrefs);
            this.$watch("positionsReversed", savePrefs);
            this.$watch("deltaMode", savePrefs);
        },

        // Same gate as teamClick below — the guest table is always editable.
        canPressRow() {
            return !this.positionsReversed;
        },

        teamClick(teamCode) {
            // Standings view is for reading the real table, not editing against it.
            if (this.positionsReversed) return;
            if (this.selectedTeam === null) {
                this._selectTeam(teamCode);
                return;
            }
            if (this.selectedTeam === teamCode) {
                this.selectedTeam = null;
                return;
            }
            this._performSwap(teamCode);
            this._saveToStorage(STORAGE_KEY);
        },

        reset() {
            this.teams = JSON.parse(JSON.stringify(this.originalTeams));
            this.selectedTeam = null;
            this.swapStack = [];
            this._clearStorage(STORAGE_KEY);
        },

        undoLastSwap() {
            if (!this.canUndo() || this.undoing) return;
            this.undoing = true;
            const last = this.swapStack.pop();
            setTimeout(() => {
                this._swapTeamsDirect(last.b, last.a, () => {
                    this._saveToStorage(STORAGE_KEY);
                }); // reverse
                setTimeout(() => { this.undoing = false; }, 200);
            }, 200);
        },
    });
};

window.Ligitabl.publicPredictionPage = function (el) {
    const parsed = Ligitabl._parseDataAttributes(el);
    const roundId = el?.dataset?.roundId || 'unknown';
    const base = Ligitabl._predictionBase(parsed, null, roundId);

    return Object.assign(base, {
        init() {
            this.teams = Ligitabl._mapServerPredictions(parsed.predictions);
            this.originalTeams = Ligitabl._mapServerPredictions(parsed.predictions);

            // Persist display preferences
            const savePrefs = () => Ligitabl._savePrefs({
                showStandings: this.showStandings,
                showFixtures: this.showFixtures,
                showPoints: this.showPoints,
                showGD: this.showGD,
                showForm: this.showForm,
                positionsReversed: this.positionsReversed,
                deltaMode: this.deltaMode,
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
            this.$watch("showForm", savePrefs);
            this.$watch("positionsReversed", savePrefs);
            this.$watch("deltaMode", savePrefs);
        },
    });
};

// --- Final Table Predictor ---------------------------------------------------
//
// The select-and-swap behaviour only, so finalTablePage can reuse it without inheriting
// predictionPage's cooldown, round navigation, what-if, results banners and access modes.
window.Ligitabl._selectAndSwap = function () {
    return {
        teams: [],
        selectedTeam: null,

        isSelected(teamCode) {
            return this.selectedTeam === teamCode;
        },

        // Tap a row to select, tap a second to swap them. Tapping the same row cancels.
        tapTeam(teamCode) {
            if (this.selectedTeam === null) {
                this._select(teamCode);
                return;
            }
            if (this.selectedTeam === teamCode) {
                this.selectedTeam = null;
                return;
            }
            const from = this.selectedTeam;
            this.selectedTeam = null;
            this._swap(from, teamCode);
        },

        _select(teamCode) {
            this.selectedTeam = teamCode;
            if (Ligitabl._noRowWash) return;
            this.$nextTick(() => {
                const row = document.querySelector(`[data-team-code='${teamCode}']`);
                if (row) {
                    row.classList.add('selected-pulse');
                    setTimeout(() => row.classList.remove('selected-pulse'), 300);
                }
            });
        },

        // Swaps the two rows and renumbers. onSwapped fires before the highlight so callers can
        // record the pair while positions are still fresh.
        _swap(codeA, codeB, onSwapped) {
            const indexA = this.teams.findIndex((t) => t.code === codeA);
            const indexB = this.teams.findIndex((t) => t.code === codeB);
            if (indexA < 0 || indexB < 0) return;

            const temp = this.teams[indexA];
            this.teams[indexA] = this.teams[indexB];
            this.teams[indexB] = temp;
            // Only the two swapped slots change index, and position is always index + 1.
            this.teams[indexA].position = indexA + 1;
            this.teams[indexB].position = indexB + 1;

            if (onSwapped) onSwapped();
            if (Ligitabl._noRowWash) return;

            this.$nextTick(() => {
                const rowA = document.querySelector(`[data-team-code='${codeA}']`);
                const rowB = document.querySelector(`[data-team-code='${codeB}']`);
                if (rowA) rowA.classList.add('swapping');
                if (rowB) rowB.classList.add('swapping');
                setTimeout(() => {
                    if (rowA) rowA.classList.remove('swapping');
                    if (rowB) rowB.classList.remove('swapping');
                }, 400);
            });
        },
    };
};

window.Ligitabl.finalTablePage = function (el) {
    const dataset = el?.dataset || {};
    const readBool = (value) => value === 'true';

    const base = Ligitabl._selectAndSwap();
    // Captured before the override below replaces it on the merged object.
    const baseSwap = base._swap;

    return Object.assign(base, {
        // Server truth, re-read after every successful save.
        hasEntry: readBool(dataset.hasEntry),
        entryOpen: readBool(dataset.entryOpen),
        originalTeams: [],
        // Declared here, not just assigned in init(): a property that only ever appears via
        // `this._zones = …` is outside Alpine's reactive data, so anything reading it renders once
        // and never updates.
        _zones: {},
        // Current standings position per team code, and whether there are any. Same reason as
        // _zones above: a property that only ever appears via assignment in init() is outside
        // Alpine's reactive data, so anything reading it renders once and never updates.
        _livePositions: {},
        hasLiveProgress: false,
        // The pairs tapped since the last save, in order. Replayed server-side.
        pendingSwaps: [],
        inFlight: false,
        message: null,
        messageKind: null,

        init() {
            // Rows carry code/name/shortName so a swap re-renders without another round trip.
            const rows = Ligitabl._parseJSON(dataset.rows, []);
            this.teams = rows.map((row, idx) => ({ ...row, position: idx + 1 }));
            // The last saved order. Drives the dirty tint and the ↑/↓ arrows, and is re-baselined
            // on every successful save so the markers reflect "since you last saved".
            this.originalTeams = JSON.parse(JSON.stringify(this.teams));
            this._zones = Ligitabl._parseJSON(dataset.zones, {});
            // Current standings position per code, for the locked-but-unscored view. Empty ([]) in
            // every other state, which is what keeps the live columns hidden.
            const live = Ligitabl._parseJSON(dataset.liveRows, []);
            this._livePositions = live.reduce((acc, row) => {
                if (row.current != null) acc[row.code] = row.current;
                return acc;
            }, {});
            this.hasLiveProgress = Object.keys(this._livePositions).length > 0;
        },

        // --- shared visual language with the main prediction table ---

        isDirty(teamCode) {
            const team = this.teams.find((t) => t.code === teamCode);
            const original = this.originalTeams.find((t) => t.code === teamCode);
            if (!team || !original) return false;
            return team.position !== original.position;
        },

        getPositionChange(teamCode) {
            const team = this.teams.find((t) => t.code === teamCode);
            const original = this.originalTeams.find((t) => t.code === teamCode);
            if (!team || !original) return null;
            const change = original.position - team.position;
            if (change === 0) return null;
            return change > 0 ? '↑' + change : '↓' + Math.abs(change);
        },

        // --- live progress (locked, not yet scored) ------------------------------
        //
        // No score, total or exact-hit count: those are the reveal, and this view does no arithmetic
        // for the player. Movement reuses getPositionChange's vocabulary.

        /** Where the team actually sits now, or null if standings do not list it. */
        livePosition(teamCode) {
            const current = this._livePositions[teamCode];
            return current == null ? null : current;
        },

        /** Predicted vs actual as ↑/↓N — "you had them 3rd, they are 1st" reads as ↑2. */
        liveMovement(teamCode) {
            const current = this._livePositions[teamCode];
            if (current == null) return null;
            const team = this.teams.find((t) => t.code === teamCode);
            if (!team) return null;
            const change = team.position - current;
            if (change === 0) return null;
            return change > 0 ? '↑' + change : '↓' + Math.abs(change);
        },

        /** Exact-position match against the live table. Per row only — never counted up. */
        liveOnTarget(teamCode) {
            const current = this._livePositions[teamCode];
            if (current == null) return false;
            const team = this.teams.find((t) => t.code === teamCode);
            return !!team && team.position === current;
        },

        // Neutral badge: pre-GW1 there are no results to tint by, unlike the main table's
        // form-driven colouring.
        teamBadgeClasses() {
            return 'bg-gray-200 text-gray-700';
        },

        // --- qualification zones -------------------------------------------------
        //
        // From data-zones, so the bands follow the competition. Shape:
        // {"CL":[1,5],"UEL":[6,7],"UECL":[8,8],"REL":[18,20]}, inclusive ranges, any subset, {} for none.
        zoneOf(position) {
            const zones = this._zones || {};
            for (const [code, range] of Object.entries(zones)) {
                if (Array.isArray(range) && position >= range[0] && position <= range[1]) {
                    return code;
                }
            }
            return null;
        },

        zoneLabel(position) {
            return this.zoneOf(position) || '';
        },

        // Left edge marker. Colour-only, so it never competes with the selected/dirty row tints.
        // MID is a zone with its own (grey) marker, not an absence — otherwise mid-table rows read
        // as having lost their bar rather than as a deliberate band.
        zoneBarClass(position) {
            switch (this.zoneOf(position)) {
                case 'CL':
                    return 'bg-blue-500';
                case 'UEL':
                    return 'bg-green-500';
                case 'UECL':
                    return 'bg-amber-500';
                case 'REL':
                    return 'bg-red-500';
                default:
                    return 'bg-gray-300';
            }
        },

        zoneTextClass(position) {
            switch (this.zoneOf(position)) {
                case 'CL':
                    return 'text-blue-600';
                case 'UEL':
                    return 'text-green-600';
                case 'UECL':
                    return 'text-amber-600';
                case 'REL':
                    return 'text-red-600';
                default:
                    return 'text-gray-400';
            }
        },

        /**
         * A faint zone wash at the -50 step, so it stays below the selected (blue-50) and dirty
         * (amber-50) tints, the states the player is acting on.
         */
        zoneRowClass(position) {
            switch (this.zoneOf(position)) {
                case 'CL':
                    return 'bg-blue-50/60';
                case 'UEL':
                    return 'bg-green-50/60';
                case 'UECL':
                    return 'bg-amber-50/60';
                case 'REL':
                    return 'bg-red-50/60';
                default:
                    return 'bg-white';
            }
        },

        getDirtyCount() {
            return this.teams.filter((t) => this.isDirty(t.code)).length;
        },

        // Back to the last saved order. Purely client-side: it clears the pending batch rather than
        // sending inverse swaps, so an undone edit never reaches the server and can never move
        // settledAt — the tiebreak only ever advances on swaps the player actually kept.
        /**
         * Back to the page as freshly rendered: table, tints, arrows and hints all cleared.
         *
         * <p>Restores from {@code originalTeams}, re-baselined on every save, not by re-running
         * init(): {@code data-rows} is the order first served, stale after a save. Clearing
         * message/messageKind drops the "N moved since last save" hint.
         *
         * <p>⚠️ Reorders the existing team objects instead of assigning copies: each row's
         * {@code :class} effect tracks the object it last read, so fresh copies leave
         * {@code zoneRowClass(team.position)} watching orphans and the old zone wash stays.
         */
        reset() {
            if (this.inFlight) return;
            const byCode = new Map(this.teams.map((team) => [team.code, team]));
            this.teams = this.originalTeams.map((original) => {
                const team = byCode.get(original.code);
                if (!team) return { ...original };
                team.position = original.position;
                return team;
            });
            this.teams.sort((a, b) => a.position - b.position);
            this.pendingSwaps = [];
            this.selectedTeam = null;
            this.message = null;
            this.messageKind = null;
        },

        // Rows are only interactive while the table can still change.
        canEdit() {
            return this.entryOpen && !this.inFlight;
        },

        onRowTap(teamCode) {
            if (!this.canEdit()) return;
            this.tapTeam(teamCode);
        },

        // Wraps the helper so each swap is also recorded for the server to replay.
        _swap(codeA, codeB) {
            baseSwap.call(this, codeA, codeB, () => {
                this.pendingSwaps.push({ teamA: codeA, teamB: codeB });
            });
        },

        // Enabled on the first save even with nothing pending — that is how a player accepts the
        // baseline, and the only empty batch the server accepts. Once a row exists, a clean table
        // means there is nothing to save, so the button goes flat rather than earning a 400.
        saveEnabled() {
            if (!this.entryOpen || this.inFlight) return false;
            // No row yet: the first save may be empty — that is how a player accepts the baseline.
            if (!this.hasEntry) return true;
            // Otherwise gate on the table differing from the saved order, not on pending entries:
            // A↔B and back leaves two entries but the same table, and posting it would advance
            // settledAt (the tiebreak) for nothing.
            return this.getDirtyCount() > 0;
        },

        currentOrder() {
            return this.teams
                .slice()
                .sort((a, b) => a.position - b.position)
                .map((t) => t.code);
        },

        save() {
            if (!this.saveEnabled()) return;

            // A net-zero batch (A↔B then B↔A) would still pass the server's expectedOrder checksum
            // and advance settledAt for a change that isn't there. saveEnabled() stops the button,
            // and this stops the request — the swap list is an audit trail of kept moves, so it
            // must not carry pairs the player undid.
            if (this.hasEntry && this.getDirtyCount() === 0) {
                this.pendingSwaps = [];
                return;
            }

            this.inFlight = true;
            this.message = null;
            // Captured before the response flips it, so the handler can tell a first save from a
            // subsequent one.
            const wasEntered = this.hasEntry;

            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken) headers['X-CSRF-TOKEN'] = csrfToken;

            fetch('/final-table', {
                method: 'POST',
                headers,
                body: JSON.stringify({
                    swaps: this.pendingSwaps,
                    // A checksum, not the payload: the server stores the result of replaying swaps.
                    expectedOrder: this.currentOrder(),
                }),
            })
                .then((r) => r.json().then((data) => ({ ok: r.ok, status: r.status, data })))
                .then(({ ok, status, data }) => {
                    this.inFlight = false;

                    if (ok && data.success) {
                        this.pendingSwaps = [];
                        // The saved order is the new baseline, so dirty tints and arrows clear.
                        this.originalTeams = JSON.parse(JSON.stringify(this.teams));
                        // Without this the button stays enabled on a now-existing clean row and the
                        // next press earns the NothingToSave 400 this rule exists to prevent.
                        const firstSave = !wasEntered;
                        this.hasEntry = true;
                        // After hasEntry: the card is x-show'd on it, and on a first save this is
                        // the moment it becomes visible. Its component is mounted from page load
                        // either way, so this only orders the update behind the reveal.
                        this._refreshShareCard(data);
                        this._flash(data.message || 'Saved', 'success');
                        // First save only: the card has just appeared, and a collapsed panel a
                        // player has never seen is easy to miss. Later saves leave it as they
                        // found it — reopening a panel someone deliberately closed is nagging.
                        if (firstSave) {
                            this.$nextTick(() =>
                                this.$dispatch('final-table-saved', { firstSave: true }));
                        }
                        return;
                    }

                    if (status === 409) {
                        this._flash(data.message || 'This table changed in another tab. Reloading.', 'error');
                        setTimeout(() => window.location.reload(), 1200);
                        return;
                    }

                    this._flash((data && data.message) || 'Could not save your table', 'error');
                })
                .catch(() => {
                    this.inFlight = false;
                    this._flash('Could not save your table', 'error');
                });
        },

        /**
         * Hand the share card what the server just told us, so it stops showing the old table.
         */
        _refreshShareCard(data) {
            const card = document.querySelector('[x-data*="finalTableShareCard"]');
            if (!card || !window.Alpine) return;
            try {
                Alpine.$data(card)?.applySaved?.(data);
            } catch (e) {
                // A missing or not-yet-initialised card is not worth failing a good save over.
            }
        },

        // Dev preview only: rendered behind devPreviewEnabled, and the endpoints do not exist as
        // beans outside non-prod profiles. Reloads so every read path picks up the new state.
        devScore() {
            this._devPost('/dev/final-table/score');
        },

        devClear() {
            this._devPost('/dev/final-table/clear');
        },

        _devPost(url) {
            if (this.inFlight) return;
            this.inFlight = true;

            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken) headers['X-CSRF-TOKEN'] = csrfToken;

            fetch(url, { method: 'POST', headers })
                .then((r) => r.json().then((data) => ({ ok: r.ok, data })))
                .then(({ ok, data }) => {
                    this.inFlight = false;
                    if (!ok) {
                        this._flash((data && data.message) || 'Dev action failed', 'error');
                        return;
                    }
                    window.location.reload();
                })
                .catch(() => {
                    this.inFlight = false;
                    this._flash('Dev action failed', 'error');
                });
        },

        _flash(message, kind) {
            this.message = message;
            this.messageKind = kind;
            setTimeout(() => {
                this.message = null;
                this.messageKind = null;
            }, 4000);
        },
    });
};

// Share card: draws the table to an offscreen canvas and offers it as an image. Separate from
// finalTablePage so the public read-only view can mount it. Text and colour blocks only: remote
// crest images would taint the canvas and make toBlob() throw SecurityError.
window.Ligitabl.finalTableShareCard = function (el) {
    const dataset = el?.dataset || {};

    return {
        // Open by default, unlike fragments/share-prediction.html: this game's whole point is the
        // shareable card, so the image sits in view rather than behind a disclosure.
        open: true,
        /**
         * Team codes in the last-saved order, or null to fall back to the seeded `data-rows`.
         * Seeded from the attribute at init() so a page load still honours a server-rendered
         * order, then overwritten by setSavedOrder() on each save.
         */
        savedOrder: null,
        /** The last-saved settle time, kept reactive for the same reason as savedOrder. */
        settledAt: null,
        rendering: false,
        copiedText: false,
        copiedLink: false,
        // Feature-detected once: navigator.canShare with files is Safari/Chrome-mobile only, and a
        // button that silently does nothing is worse than an absent one.
        canShareFiles: false,

        init() {
            const seededOrder = Ligitabl._parseJSON(dataset.order, null);
            this.savedOrder = Array.isArray(seededOrder) && seededOrder.length > 0
                ? seededOrder
                : null;
            this.settledAt = dataset.settledAt || null;

            try {
                this.canShareFiles =
                    typeof navigator !== 'undefined' &&
                    typeof navigator.canShare === 'function' &&
                    navigator.canShare({
                        files: [new File([new Blob()], 'p.jpg', { type: 'image/jpeg' })],
                    });
            } catch (e) {
                this.canShareFiles = false;
            }
        },

        /**
         * Hand the card what the server returned from a save.
         */
        applySaved(data) {
            if (Array.isArray(data?.order) && data.order.length > 0) {
                this.savedOrder = data.order;
            }
            if (data?.settledAt) {
                this.settledAt = data.settledAt;
            }
        },

        /**
         * The rows to draw, in the order the card should show. {@code data-rows} is rendered at
         * page load, so a save without reload leaves it stale; ordering comes from
         * _resolvedOrder(), and `actual`/`hit` (scored figures the client never recomputes) from
         * the attribute, merged back on by code.
         */
        rows() {
            const seeded = Ligitabl._parseJSON(dataset.rows, []);
            const order = this._resolvedOrder(seeded);
            if (!order) return seeded;

            const extrasByCode = seeded.reduce((acc, row) => {
                if (row.actual != null || row.hit != null) {
                    acc[row.code] = { actual: row.actual, hit: row.hit };
                }
                return acc;
            }, {});

            return order.map((team) => ({ ...team, ...(extrasByCode[team.code] || {}) }));
        },

        /**
         * The teams in the saved order, or null to use `data-rows` as seeded.
         *
         * ⚠️ Saved state only, blind to unsaved moves on screen: savedOrder (the server's replay
         * from the latest save, via applySaved() or `data-order`) when set; otherwise `data-rows`
         * is already the saved order. A code with no seeded row is dropped, and if that leaves the
         * wrong team count the seeded order wins.
         */
        _resolvedOrder(seeded) {
            const codes = this.savedOrder;
            if (!Array.isArray(codes) || codes.length === 0) return null;

            const byCode = seeded.reduce((acc, row) => {
                acc[row.code] = row;
                return acc;
            }, {});
            const ordered = codes.map((code) => byCode[code]).filter(Boolean);
            return ordered.length === seeded.length ? ordered : null;
        },

        title() {
            return dataset.title || 'My Final Table';
        },

        shareUrl() {
            return dataset.shareUrl || '';
        },

        /**
         * The share text, with the team lines re-listed in the saved order. `data-share-text` is
         * built at page load, so after a save it lists the old order. Only the team block is
         * rebuilt; the server's header and footer are reused verbatim, so the wording can't drift
         * from SharePredictionTextBuilder. The same _resolvedOrder() as rows() keeps the text,
         * image and public page in one order. Falls back to the server string on an unexpected shape.
         */
        shareText() {
            const seeded = dataset.shareText || '';
            const saved = this._resolvedOrder(Ligitabl._parseJSON(dataset.rows, []));
            if (!seeded || !saved) return seeded;

            // Header ends at the first blank line; footer starts at the last one.
            const headEnd = seeded.indexOf('\n\n');
            const footStart = seeded.lastIndexOf('\n\n');
            if (headEnd < 0 || footStart <= headEnd) return seeded;

            // +1 not +2 on the tail: the rebuilt team block already ends in a newline, and the
            // footer boundary is a "\n\n", so taking both would insert a blank line the server's
            // version does not have.
            return (
                seeded.slice(0, headEnd + 2) +
                Ligitabl._shareTeamLines(saved) +
                seeded.slice(footStart + 1)
            );
        },

        subtitle() {
            return dataset.subtitle || '';
        },

        /**
         * Where a viewer goes to make their own: always the production address, not the share URL's
         * host, since a card drawn on localhost or staging is still shared with real people.
         */
        buildYoursUrl() {
            return 'LigiPredictor.com/final-table';
        },

        /**
         * "settled 12 Aug 2026, 14:32", in the viewer's own locale and timezone, so formatted
         * client-side. Date and time because settledAt is the tiebreak. Shares the 'settled' preset
         * with final-table-public.html's line on the same page. '' when absent or unparseable.
         */
        settledAtLabel() {
            const formatted = Ligitabl.formatTimestamp(this.settledAt, 'settled');
            return formatted ? 'settled ' + formatted : '';
        },

        _drawCard() {
            // Two-column scorecard: a 20-row single column makes a tall, thin image that reads
            // badly in a timeline. Square is the social format.
            const rows = this.rows();
            const zones = Ligitabl._parseJSON(dataset.zones, {});
            // 1080 at 1x already exceeds every social service's display size; text is drawn at
            // final size, not scaled, so it stays sharp.
            const W = 1080;
            const H = 1080;

            const canvas = document.createElement('canvas');
            const scale = 1;
            canvas.width = W * scale;
            canvas.height = H * scale;
            const ctx = canvas.getContext('2d');
            ctx.scale(scale, scale);

            const FONT = 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif';
            // tailwind.config.js `brand` (indigo) — the card and the app share one palette.
            const BRAND = '#6366f1';
            const BRAND_DARK = '#312e81';
            const ACCENT = '#a5b4fc';
            // MID is a real zone on the card, not an absence: every row gets a left bar, so the
            // mid-table block reads as deliberate rather than as rows that lost their marker.
            const ZONES = {
                CL: { color: '#3b82f6', tint: 'rgba(59,130,246,0.14)', label: 'Champions League' },
                UEL: { color: '#22c55e', tint: 'rgba(34,197,94,0.14)', label: 'Europa League' },
                UECL: { color: '#f0b429', tint: 'rgba(240,180,41,0.14)', label: 'Conference League' },
                MID: { color: '#8b8fa3', tint: 'rgba(255,255,255,0.06)', label: 'Mid-table' },
                REL: { color: '#ef4444', tint: 'rgba(239,68,68,0.14)', label: 'Relegation' },
            };
            const zoneLabel = (position) => {
                for (const [code, range] of Object.entries(zones)) {
                    if (Array.isArray(range) && position >= range[0] && position <= range[1]) return code;
                }
                return 'MID';
            };
            const zoneColor = (position) => ZONES[zoneLabel(position)].color;
            // Only the zones this table actually uses, in table order, so a competition without a
            // Conference League place does not advertise one.
            const legendCodes = Object.keys(ZONES).filter((code) =>
                rows.some((_, idx) => zoneLabel(idx + 1) === code)
            );

            // Background: deep green, matching the app's dark surfaces.
            const bg = ctx.createLinearGradient(0, 0, W, H);
            bg.addColorStop(0, BRAND_DARK);
            bg.addColorStop(1, '#1a1740');
            ctx.fillStyle = bg;
            ctx.fillRect(0, 0, W, H);

            // Top rule.
            ctx.fillStyle = BRAND;
            ctx.fillRect(0, 0, W, 6);

            // Brand line.
            ctx.fillStyle = '#ffffff';
            ctx.font = '700 30px ' + FONT;
            ctx.fillText('LigiPredictor', 60, 92);

            ctx.fillStyle = 'rgba(255,255,255,0.55)';
            ctx.font = '600 15px ' + FONT;
            ctx.textAlign = 'right';
            ctx.fillText((dataset.competitionName || 'FINAL TABLE').toUpperCase(), W - 60, 90);
            ctx.textAlign = 'left';

            // Title block.
            ctx.fillStyle = 'rgba(255,255,255,0.55)';
            ctx.font = '600 15px ' + FONT;
            ctx.fillText((dataset.kicker || 'FINAL TABLE PREDICTION').toUpperCase(), 60, 190);

            // The callout box starts at CALLOUT_X, so the title shrinks to fit the remaining width
            // rather than truncating: the competition name is the point of the card.
            const CALLOUT_W = 320;
            const CALLOUT_X = W - 60 - CALLOUT_W;
            const titleMaxWidth = CALLOUT_X - 60 - 32;
            let titleSize = 62;
            ctx.fillStyle = '#ffffff';
            ctx.font = '700 ' + titleSize + 'px ' + FONT;
            while (ctx.measureText(this.title()).width > titleMaxWidth && titleSize > 26) {
                titleSize -= 2;
                ctx.font = '700 ' + titleSize + 'px ' + FONT;
            }
            ctx.fillText(this.title(), 60, 258);

            ctx.fillStyle = 'rgba(255,255,255,0.55)';
            ctx.font = '400 20px ' + FONT;
            ctx.fillText(this.subtitle(), 60, 296);

            // Champion callout, phrased as a claim: "I predict that / Arsenal / will win the
            // league". It is the line people screenshot, so it reads as a sentence rather than a
            // stat block.
            if (rows.length > 0) {
                const boxW = CALLOUT_W;
                const boxX = CALLOUT_X;
                const boxY = 150;
                const boxH = 150;
                ctx.strokeStyle = 'rgba(99,102,241,0.5)';
                ctx.fillStyle = 'rgba(255,255,255,0.04)';
                ctx.lineWidth = 2;
                if (ctx.roundRect) {
                    ctx.beginPath();
                    ctx.roundRect(boxX, boxY, boxW, boxH, 12);
                    ctx.fill();
                    ctx.stroke();
                } else {
                    ctx.fillRect(boxX, boxY, boxW, boxH);
                    ctx.strokeRect(boxX, boxY, boxW, boxH);
                }

                const cx = boxX + boxW / 2;
                ctx.textAlign = 'center';

                ctx.fillStyle = 'rgba(255,255,255,0.55)';
                ctx.font = '400 16px ' + FONT;
                ctx.fillText('I predict that', cx, boxY + 42);

                ctx.fillStyle = '#ffffff';
                ctx.font = '700 30px ' + FONT;
                ctx.fillText(rows[0].name || rows[0].code, cx, boxY + 88);

                ctx.fillStyle = 'rgba(255,255,255,0.55)';
                ctx.font = '400 16px ' + FONT;
                ctx.fillText('will win the league', cx, boxY + 122);

                ctx.textAlign = 'left';
            }

            // Legend: pill per zone, so the colour bars on the rows are readable standalone — the
            // card gets posted without the page around it.
            const legendY = 360;
            let legendX = 60;
            legendCodes.forEach((code) => {
                const zone = ZONES[code];
                ctx.font = '700 13px ' + FONT;
                const codeW = ctx.measureText(code).width;
                ctx.font = '400 14px ' + FONT;
                const labelW = ctx.measureText(zone.label).width;
                const pillW = 16 + codeW + 8 + labelW + 16;

                ctx.fillStyle = 'rgba(255,255,255,0.06)';
                if (ctx.roundRect) {
                    ctx.beginPath();
                    ctx.roundRect(legendX, legendY - 20, pillW, 30, 15);
                    ctx.fill();
                } else {
                    ctx.fillRect(legendX, legendY - 20, pillW, 30);
                }

                ctx.fillStyle = zone.color;
                ctx.font = '700 13px ' + FONT;
                ctx.fillText(code, legendX + 16, legendY);

                ctx.fillStyle = 'rgba(255,255,255,0.75)';
                ctx.font = '400 14px ' + FONT;
                ctx.fillText(zone.label, legendX + 16 + codeW + 8, legendY);

                legendX += pillW + 10;
            });

            // Rows: two columns, ten each.
            const half = Math.ceil(rows.length / 2);
            const rowH = 48;
            const gap = 24;
            const colW = (W - 120 - gap) / 2;
            const top = 420;

            rows.forEach((row, idx) => {
                const col = idx < half ? 0 : 1;
                const rowIndex = idx < half ? idx : idx - half;
                const x = 60 + col * (colW + gap);
                const y = top + rowIndex * rowH;
                const position = idx + 1;

                // Faint zone wash on the row, as on the page. Kept very low alpha: on a dark card
                // the bar and the tag already carry the colour, and a stronger fill would fight the
                // team names for attention.
                const zoneTint = ZONES[zoneLabel(position)].tint;
                ctx.fillStyle = zoneTint;
                if (ctx.roundRect) {
                    ctx.beginPath();
                    ctx.roundRect(x, y, colW, rowH - 6, 8);
                    ctx.fill();
                } else {
                    ctx.fillRect(x, y, colW, rowH - 6);
                }

                // Zone bar — always drawn, since MID is a zone with its own colour.
                const zc = zoneColor(position);
                ctx.fillStyle = zc;
                ctx.fillRect(x, y + 6, 4, rowH - 18);

                ctx.fillStyle = 'rgba(255,255,255,0.6)';
                ctx.font = '600 19px ' + FONT;
                ctx.textAlign = 'right';
                ctx.fillText(String(position), x + 52, y + 28);
                ctx.textAlign = 'left';

                ctx.fillStyle = '#ffffff';
                ctx.font = '700 20px ' + FONT;
                ctx.fillText(row.name || row.code, x + 72, y + 28);

                // Zone tag, or the actual finish once scored.
                ctx.textAlign = 'right';
                if (row.actual != null) {
                    ctx.fillStyle = row.hit === 0 ? '#4ade80' : 'rgba(255,255,255,0.5)';
                    ctx.font = '600 15px ' + FONT;
                    ctx.fillText('→ ' + row.actual, x + colW - 16, y + 28);
                } else {
                    ctx.fillStyle = zc;
                    ctx.font = '700 12px ' + FONT;
                    ctx.fillText(zoneLabel(position), x + colW - 16, y + 27);
                }
                ctx.textAlign = 'left';
            });

            // Footer.
            const footerY = H - 76;
            ctx.fillStyle = 'rgba(0,0,0,0.35)';
            ctx.fillRect(0, footerY, W, 76);

            // URL plus settled time make the image checkable: the link resolves to the server's
            // table and the timestamp is server-clock truth, so a doctored screenshot is contradicted
            // by something anyone can click.
            const settled = this.settledAtLabel();
            ctx.fillStyle = 'rgba(255,255,255,0.6)';
            ctx.font = '600 15px ' + FONT;
            ctx.fillText(this.shareUrl().replace(/^https?:\/\//, ''), 60, footerY + (settled ? 36 : 46));
            if (settled) {
                ctx.fillStyle = 'rgba(255,255,255,0.45)';
                ctx.font = '500 13px ' + FONT;
                ctx.fillText(settled, 60, footerY + 58);
            }

            // Two URLs share this footer: the personal table left, the way in right. "Play at" tells
            // them apart, and covers people who will only watch the leaderboard.
            ctx.fillStyle = ACCENT;
            ctx.font = '600 15px ' + FONT;
            ctx.textAlign = 'right';
            ctx.fillText('Play at ' + this.buildYoursUrl(), W - 60, footerY + 46);
            ctx.textAlign = 'left';

            return canvas;
        },

        // JPEG q0.9, not PNG: lossless encoding of a full-bleed gradient is close to PNG's worst
        // case, and JPEG handles flat colour and text well.
        _toBlob(callback) {
            this._drawCard().toBlob(callback, 'image/jpeg', 0.9);
        },

        // final_table_YYYYMMDDHHmm.jpg: a fixed name makes re-downloads land as "(1)", "(2)"; the
        // stamp orders them and says when. Local time, since the person who pressed it reads it.
        _cardFilename() {
            const d = new Date();
            const pad = (n) => String(n).padStart(2, '0');
            const stamp =
                d.getFullYear() +
                pad(d.getMonth() + 1) +
                pad(d.getDate()) +
                pad(d.getHours()) +
                pad(d.getMinutes());
            return 'final_table_' + stamp + '.jpg';
        },

        downloadCard() {
            if (this.rendering) return;
            this.rendering = true;
            try {
                this._toBlob((blob) => {
                    this.rendering = false;
                    if (!blob) return;
                    const url = URL.createObjectURL(blob);
                    const link = document.createElement('a');
                    link.href = url;
                    link.download = this._cardFilename();
                    link.click();
                    URL.revokeObjectURL(url);
                });
            } catch (e) {
                this.rendering = false;
                console.warn('Failed to render share card:', e);
            }
        },

        shareCard() {
            // The button only renders when canShareFiles is true, but re-check: the panel may have
            // been open across a navigation.
            if (!this.canShareFiles) {
                this.copyLink();
                return;
            }
            this._toBlob((blob) => {
                if (!blob) return;
                const file = new File([blob], this._cardFilename(), { type: 'image/jpeg' });
                if (!navigator.canShare({ files: [file] })) {
                    this.copyLink();
                    return;
                }
                navigator
                    .share({ files: [file], text: this.shareText(), url: this.shareUrl() })
                    .catch(() => {});
            });
        },

        displayUrl() {
            return this.shareUrl().replace(/^https?:\/\//, '');
        },

        copyText() {
            this._copy(this.shareText(), 'copiedText');
        },

        copyLink() {
            this._copy(this.shareUrl(), 'copiedLink');
        },

        // Ticks the matching icon for 2s, the same feedback the main share panel gives.
        _copy(value, flag) {
            if (!navigator.clipboard || !value) return;
            navigator.clipboard.writeText(value).then(
                () => {
                    this[flag] = true;
                    setTimeout(() => (this[flag] = false), 2000);
                },
                () => {}
            );
        },
    };
};

// Three plausible scores per outcome, for both the quick-pick chip strip and the reroll dice.
const WHAT_IF_CHIPS = {
    H: [[1, 0], [2, 0], [2, 1]],
    D: [[1, 1], [0, 0], [2, 2]],
    A: [[0, 1], [1, 2], [0, 2]]
};

// ⚠️ Mirrors ComputeWhatIfUseCase.isScoreable; change both together. Called-off matches carry no
// hypothesis. Matches already kicked off or finished stay scoreable: on a locked round the page
// replays the saved guess, never the real result.
const WHAT_IF_UNSCOREABLE_STATUSES = ["POSTPONED", "CANCELLED", "SUSPENDED"];
const isWhatIfScoreable = (match) => !WHAT_IF_UNSCOREABLE_STATUSES.includes(match.status);

const isWhatIfMatchLive = (match) => match.status === "LIVE" || match.status === "SUSPENDED";

const whatIfOutcomeOf = (home, away) => (home > away ? "HOME" : home < away ? "AWAY" : "DRAW");

const whatIfGrade = (guessHome, guessAway, actualHome, actualAway) => {
    const guessed = whatIfOutcomeOf(guessHome, guessAway);
    const happened = whatIfOutcomeOf(actualHome, actualAway);

    if (guessed !== "DRAW") {
        return guessed === happened ? "WIN" : "LOSS";
    }
    if (happened === "DRAW") {
        return "WIN";
    }
    return Math.abs(actualHome - actualAway) === 1 ? "DRAW" : "LOSS";
};

const whatIfGradeMark = (grade, exact) => {
    if (grade === "WIN") return exact ? "★" : "✓";
    return ({ DRAW: "~", LOSS: "–" })[grade] || "";
};

const whatIfGradeMarkClass = (grade, exact) => {
    if (grade === "WIN") return exact ? "bg-green-100 text-green-800" : "bg-green-50 text-green-700";
    return ({
        DRAW: "bg-amber-50 text-amber-700",
        LOSS: "bg-red-50 text-red-700"
    })[grade] || "bg-gray-100 text-gray-500";
};

// Did the guess land on the exact scoreline, not just the right outcome?
const whatIfExact = (guessHome, guessAway, actualHome, actualAway) =>
    guessHome === actualHome && guessAway === actualAway;

window.Ligitabl.whatIfPage = function (el) {
    const parsed = Ligitabl._parseDataAttributes(el);
    const matches = Ligitabl._parseJSON(el?.dataset?.whatIfMatches, []);
    const roundId = el?.dataset?.roundId || "unknown";
    const userId = el?.dataset?.userId || "guest";
    const maxHitPoints = Number(el?.dataset?.maxHitPoints || 0);
    const roundOpen = el?.dataset?.roundOpen === "true";
    const currentRound = el?.dataset?.currentRound || "";
    const base = Ligitabl._predictionBase(parsed, userId, roundId);
    const originalPerformSwap = base._performSwap;

    // Called-off matches contribute nothing to the hypothesis — never tracked here, so a postponed
    // match is hidden entirely (see visibleMatches()) while cancelled/suspended ones render as a
    // status badge, and nothing for any of them ever reaches localStorage.
    const scores = {};
    matches.forEach((m) => {
        if (!isWhatIfScoreable(m)) return;
        scores[m.matchId] = { home: null, away: null };
    });

    return Object.assign(base, {
        matches,
        scores,
        maxHitPoints,
        roundOpen,
        currentRound,
        // Raw tap history, and it must stay raw: _consumeSwapPairs() matches these against the
        // pairs a submission actually carried. See "Deliberately NOT _derivedSwaps" in
        // submitChanges().
        swapLog: [],
        // A method, not a getter — the fragment's own x-data="{ open: true }" would bind a
        // getter's `this` to that child scope, where originalTeams/teams do not exist.
        displayedSwapLog() {
            return Ligitabl._displaySwapEntries(this.originalTeams, this.swapLog);
        },
        activeTab: "standings",
        hasComputed: false,
        isComputing: false,
        isReverting: false,
        isRefreshing: false,
        // Transient "Already up to date" / "Updated from your last apply" note next to Refresh.
        refreshMessage: null,
        errorMessage: null,
        // `scores` as of the last successful apply(); scoresDirty() compares against it so the
        // Apply button can show '*Apply' rather than a stale result.
        appliedScores: null,
        // Score-picker state
        openId: null,
        openSeg: null,
        focusSide: "home",
        hasEdited: false,
        rollingId: null,
        // Set when a restored session had swaps that a fixture change threw away, so the page can
        // explain why the table (and the score with it) came back different. Not persisted: the
        // session is re-saved without those swaps, so the next load has nothing to explain.
        swapsClearedByFixtureChange: false,
        // Set when a my-table submission consumed swaps from this sandbox (see
        // predictionPage.reconcileWhatIfSwaps) and something actually matched.
        swapsClearedBySubmit: false,
        // How many sandbox swaps that submission consumed, so the notice can name the count
        // instead of implying the whole sandbox went.
        swapsConsumedCount: 0,
        // Set when surviving swaps were replayed onto a table that moved under them, so their
        // positions changed untouched. Drives a quiet line next to the list, not the notice.
        swapsRebased: false,
        // Nonce of the last submission whose outcome has been announced. Persisted, because
        // init() can run twice in one page load (htmx:afterSwap re-inits Alpine over this
        // fragment) and the second pass must not show the notice again.
        lastSubmitNonce: null,
        init() {
            this._pruneStaleWhatIfSessions();
            this.teams = Ligitabl._mapServerPredictions(parsed.predictions);
            this.originalTeams = Ligitabl._mapServerPredictions(parsed.predictions);
            this._restoreWhatIfSession();
            this._reconcileWithServer();
            this._applyIfComplete();
        },
        // The sandbox key is round-scoped, so last round's entry is never read again once
        // roundId moves on — it just sits there, one dead key per round forever. Drop this
        // user's stale rounds on the way in.
        _pruneStaleWhatIfSessions() {
            const prefix = `ligitabl.whatif.${userId}.`;
            const keep = this._whatIfStorageKey();
            try {
                Object.keys(localStorage)
                    .filter((k) => k.startsWith(prefix) && k !== keep)
                    .forEach((k) => localStorage.removeItem(k));
            } catch (e) {
                console.warn("Failed to prune stale what-if sessions:", e);
            }
        },
        _applyIfComplete() {
            if (this.hasComputed || this.isComputing) return;
            if (!this.allScoresEntered()) return;
            this.apply();
        },
        // Cross-device sync at page load, from the scores the server rendered into the page. Silent
        // by design — a confirm dialog on page load would be intolerable, and there's nothing on
        // screen yet for the user to weigh it against. The Refresh button does ask (see below).
        _reconcileWithServer() {
            const serverScores = this._normalizeServerScores(
                Ligitabl._parseJSON(el?.dataset?.savedWhatIfScores, null)
            );
            if (!serverScores || this._scoresMatchServer(serverScores)) return;
            this._adoptServerScores(serverScores);
        },
        // Server list -> the `{matchId: {home, away}}` shape `scores` uses. Null when there's
        // nothing usable to reconcile against.
        _normalizeServerScores(savedScores) {
            if (!Array.isArray(savedScores) || savedScores.length === 0) return null;
            const serverScores = {};
            savedScores.forEach((s) => {
                // A match postponed since the save is no longer tracked in `scores` — skip it,
                // same exclusion rule the rest of the page uses.
                if (!this.scores[s.matchId]) return;
                serverScores[s.matchId] = { home: s.homeGoals, away: s.awayGoals };
            });
            return Object.keys(serverScores).length > 0 ? serverScores : null;
        },
        // The server wins on scores, and the standings go back to the real ones. Swaps stay: they
        // arrange the user's own table. Callers follow with _applyIfComplete().
        _adoptServerScores(serverScores) {
            Object.keys(this.scores).forEach((matchId) => {
                this.scores[matchId] = serverScores[matchId] || { home: null, away: null };
            });
            this.hasEdited = true;
            this.selectedTeam = null;
            this.currentStandings = parsed.currentStandings;
            this.currentPoints = parsed.currentPoints;
            this.currentGoalDifference = parsed.currentGoalDifference;
            this.hasComputed = false;
            this.appliedScores = null;
            this.activeTab = "standings";
            this.openId = null;
            this.openSeg = null;
            this.rollingId = null;
            this._saveWhatIfSession();
        },
        // Refresh: same server-wins reconciliation as page load, but against a live fetch rather
        // than the scores baked into the page when it was rendered — and it asks first when there's
        // local work to lose, since the user pressed a button rather than just opening the page.
        refreshFromServer() {
            if (this.isRefreshing || this.isComputing) return;
            this.isRefreshing = true;
            this.refreshMessage = null;
            this.errorMessage = null;

            fetch("/predictions/user/what-if/saved", { headers: { Accept: "application/json" } })
                .then((r) => r.json().then((data) => ({ ok: r.ok, data })))
                .then(({ ok, data }) => {
                    this.isRefreshing = false;
                    if (!ok || !data.success) {
                        this.errorMessage = (data && data.message) || "Couldn't refresh";
                        return;
                    }

                    const fixturesChanged = this._adoptServerFixtures(data.matches, data.roundOpen);

                    const serverScores = this._normalizeServerScores(data.scores);
                    if (!serverScores) {
                        this._flashRefreshMessage(fixturesChanged ? "Fixtures updated" : "Nothing saved yet");
                        return;
                    }
                    if (this._scoresMatchServer(serverScores)) {
                        // Same scores, but a fixture change cleared the result they were projected
                        // into — recompute rather than leave the card empty.
                        if (fixturesChanged) this._applyIfComplete();
                        this._flashRefreshMessage(fixturesChanged ? "Fixtures updated" : "Already up to date");
                        return;
                    }
                    if (this._hasLocalWork() && !window.confirm(
                        "Load your last applied scores? This replaces the scores you have here. Your swaps stay."
                    )) {
                        this._flashRefreshMessage("Kept what you have");
                        return;
                    }

                    this._adoptServerScores(serverScores);
                    this._applyIfComplete();
                    this._flashRefreshMessage("Updated from your last apply");
                })
                .catch(() => {
                    this.isRefreshing = false;
                    this.errorMessage = "Couldn't refresh. Check your connection.";
                });
        },
        // Anything the user would actually lose to a server-wins overwrite — which is the entered
        // scores and nothing else now that swaps survive a Refresh. A page with no scores entered
        // has nothing at stake, so it syncs without asking.
        _hasLocalWork() {
            return this.hasEdited;
        },
        _flashRefreshMessage(message) {
            this.refreshMessage = message;
            setTimeout(() => {
                if (this.refreshMessage === message) this.refreshMessage = null;
            }, 2500);
        },
        // Compared match-by-match rather than by JSON.stringify: `scores` is keyed in round order,
        // the server's list carries no such guarantee, and key order would make identical scores
        // look different.
        _scoresMatchServer(serverScores) {
            const localIds = Object.keys(this.scores);
            if (localIds.length !== Object.keys(serverScores).length) return false;
            return localIds.every((id) => {
                const server = serverScores[id];
                return server && this.scores[id].home === server.home && this.scores[id].away === server.away;
            });
        },
        _whatIfStorageKey() {
            return `ligitabl.whatif.${userId}.${roundId}`;
        },
        _saveWhatIfSession() {
            try {
                localStorage.setItem(this._whatIfStorageKey(), JSON.stringify({
                    scores: this.scores,
                    teams: this.teams,
                    swapStack: this.swapStack,
                    swapLog: this.swapLog,
                    hasComputed: this.hasComputed,
                    currentStandings: this.currentStandings,
                    currentPoints: this.currentPoints,
                    currentGoalDifference: this.currentGoalDifference,
                    appliedScores: this.appliedScores,
                    matchStatuses: this._matchStatuses(),
                    // Persisted so a reload doesn't repeat the notice. submitOutcome is left out
                    // of this fixed field list, so writing the session retires it.
                    lastSubmitNonce: this.lastSubmitNonce,
                }));
            } catch (e) {
                console.warn("Failed to save what-if session:", e);
            }
        },
        _clearWhatIfSession() {
            try {
                localStorage.removeItem(this._whatIfStorageKey());
            } catch (e) {
                console.warn("Failed to clear what-if session:", e);
            }
        },
        _restoreWhatIfSession() {
            let saved;
            try {
                const raw = localStorage.getItem(this._whatIfStorageKey());
                if (!raw) return false;
                saved = JSON.parse(raw);
            } catch (e) {
                console.warn("Failed to read what-if session:", e);
                return false;
            }
            if (!saved) return false;
            this.scores = saved.scores || this.scores;
            this.teams = saved.teams || this.teams;
            this.swapStack = saved.swapStack || [];
            this.swapLog = Array.isArray(saved.swapLog) ? saved.swapLog : [];
            this.hasComputed = !!saved.hasComputed;
            this.currentStandings = saved.currentStandings || this.currentStandings;
            this.currentPoints = saved.currentPoints || this.currentPoints;
            this.currentGoalDifference = saved.currentGoalDifference || this.currentGoalDifference;
            this.appliedScores = saved.appliedScores || null;
            this.lastSubmitNonce = saved.lastSubmitNonce || null;

            // A my-table submission since this session was saved already consumed its entries and
            // replayed the rest, so swapLog/teams are current; only the explanation is left. The
            // nonce check stops a reload or htmx re-init replaying the notice.
            const outcome = saved.submitOutcome;
            if (outcome && outcome.nonce && outcome.nonce !== this.lastSubmitNonce) {
                // Only announce a consumption that actually happened — a rebase with nothing
                // matched is invisible bookkeeping, not news.
                this.swapsClearedBySubmit = (outcome.consumedCount || 0) > 0;
                this.swapsConsumedCount = outcome.consumedCount || 0;
                this.swapsRebased = !!outcome.rebased;
                this.lastSubmitNonce = outcome.nonce;
            }

            if (this._fixturesChangedSince(saved)) {
                this.swapsClearedByFixtureChange = this.swapLog.length > 0;
                this.reset(); // teams = originalTeams, selectedTeam = null, swapStack = []
                this.swapLog = [];
                // The projection was computed against the swaps just thrown away, so it can't
                // stand either — same invalidation _adoptServerFixtures does for this case.
                this._invalidateProjection();
                // The fixture change wiped whatever the submit notice would describe, so it
                // would only compete with the amber notice for the same explanation.
                this.swapsClearedBySubmit = false;
                this.swapsConsumedCount = 0;
                // No surviving swaps left to have moved.
                this.swapsRebased = false;
            }
            this._reconcileMatches();
            this._saveWhatIfSession();
            this.hasEdited = Object.values(this.scores).some((s) => s.home !== null || s.away !== null);
            if (this.hasComputed) this.activeTab = "result";
            return true;
        },
        // The projection was computed against the previous arrangement, so it no longer describes
        // what's on screen. Drop it and let init()'s _applyIfComplete() recompute.
        _invalidateProjection() {
            this.hasComputed = false;
            this.appliedScores = null;
            this.currentStandings = parsed.currentStandings;
            this.currentPoints = parsed.currentPoints;
            this.currentGoalDifference = parsed.currentGoalDifference;
            this.activeTab = "standings";
        },
        // Resyncs the fixtures and open state from Refresh's payload. Returns whether the change
        // invalidates the sandbox (a tracked match off SCHEDULED, or gone), taking the swaps and
        // projection with it.
        _adoptServerFixtures(serverMatches, serverRoundOpen) {
            if (!Array.isArray(serverMatches) || serverMatches.length === 0) return false;

            const before = { scores: this.scores, matchStatuses: this._matchStatuses() };
            this.matches = serverMatches;
            if (typeof serverRoundOpen === "boolean") this.roundOpen = serverRoundOpen;

            const changed = this._fixturesChangedSince(before);
            this._reconcileMatches();
            if (!this.roundOpen) this.closeScorePicker();

            if (changed) {
                this.swapsClearedByFixtureChange = this.swapLog.length > 0;
                this.reset(); // teams = originalTeams, selectedTeam = null, swapStack = []
                this.swapLog = [];
                this.hasComputed = false;
                this.appliedScores = null;
                this.currentStandings = parsed.currentStandings;
                this.currentPoints = parsed.currentPoints;
                this.currentGoalDifference = parsed.currentGoalDifference;
                this.activeTab = "standings";
            }

            this._saveWhatIfSession();
            return changed;
        },
        _matchStatuses() {
            const statuses = {};
            this.matches.forEach((m) => {
                statuses[m.matchId] = m.status;
            });
            return statuses;
        },
        // Swaps survive Reset and Refresh: different scores don't make an arrangement wrong. A
        // match leaving SCHEDULED or dropping out of the round does, because the table is then
        // scored against different games.
        _fixturesChangedSince(saved) {
            const current = this._matchStatuses();
            const savedStatuses = saved.matchStatuses || null;
            return Object.keys(saved.scores || {}).some((matchId) => {
                const now = current[matchId];
                if (now === undefined) return true;
                const was = savedStatuses ? savedStatuses[matchId] : "SCHEDULED";
                return was === "SCHEDULED" && now !== "SCHEDULED";
            });
        },
        // A session saved before the round locked still carries scores for matches that have since
        // kicked off or finished — those stay, they're the hypothesis. Only matches called off since
        // the save drop out.
        _reconcileMatches() {
            this.matches
                .filter((m) => !isWhatIfScoreable(m))
                .forEach((m) => delete this.scores[m.matchId]);
            this.matches.filter(isWhatIfScoreable).forEach((m) => {
                if (!this.scores[m.matchId]) this.scores[m.matchId] = { home: null, away: null };
            });
        },
        // Postponed matches are excluded from the rendered list entirely (see scores init above
        // and this getter) rather than shown blanked out.
        visibleMatches() {
            return this.matches.filter((m) => m.status !== "POSTPONED");
        },
        // The matches a hypothesis is made of — what Apply sends and what "all scores entered"
        // measures against. Mirrors the server's scoreable set exactly.
        scoreableMatches() {
            return this.matches.filter(isWhatIfScoreable);
        },
        // Whether this match takes score input right now: while the round is open only fixtures that
        // haven't kicked off do (the rest show a status badge), and once it's closed every scoreable
        // match shows its locked-in guess.
        acceptsScore(match) {
            return isWhatIfScoreable(match) && (!this.roundOpen || match.status === "SCHEDULED");
        },

        matchIsLive(match) {
            return isWhatIfMatchLive(match);
        },
        hasActualScore(match) {
            return match.homeGoals !== null && match.homeGoals !== undefined
                && match.awayGoals !== null && match.awayGoals !== undefined;
        },
        actualScoreLabel(match) {
            return this.hasActualScore(match) ? `${match.homeGoals} - ${match.awayGoals}` : null;
        },
        // A mark is a verdict, so only a finished match earns one — a live score can still flip.
        matchGrade(match) {
            if (match.status !== "FINISHED" || !this.hasActualScore(match)) return null;
            if (!this.scoreAnswered(match.matchId)) return null;
            const s = this.scores[match.matchId];
            return whatIfGrade(s.home, s.away, match.homeGoals, match.awayGoals);
        },
        showsActuals(match) {
            return !this.roundOpen && isWhatIfScoreable(match) && this.hasActualScore(match);
        },
        isExactMatch(match) {
            const s = this.scores[match.matchId];
            if (!s || !this.hasActualScore(match)) return false;
            return whatIfExact(s.home, s.away, match.homeGoals, match.awayGoals);
        },
        gradeMark(match) {
            return whatIfGradeMark(this.matchGrade(match), this.isExactMatch(match));
        },
        gradeMarkClass(match) {
            return whatIfGradeMarkClass(this.matchGrade(match), this.isExactMatch(match));
        },
        gradeTitle(match) {
            const grade = this.matchGrade(match);
            if (grade === "WIN") return this.isExactMatch(match) ? "Exact score" : "Win";
            return { DRAW: "Draw — near miss", LOSS: "Loss" }[grade] || "";
        },

        buildShareText() {
            const outcomeMap = { H: "1", D: "X", A: "2" };
            const visible = this.visibleMatches();
            const width = String(visible.length).length;
            const lines = visible.map(
                (m, i) => `${String(i + 1).padStart(width, "0")}. ${m.homeTeamCode} – ${m.awayTeamCode}  ${outcomeMap[this.scoreOutcome(m.matchId)] ?? "?"}`
            );
            return `My Gameweek ${this.currentRound} Predictions ⚽\n${lines.join("\n")}\n\nPredict the table — LigiPredictor.com`;
        },
        scoreOutcome(matchId) {
            const s = this.scores[matchId];
            if (!s || s.home === null || s.away === null) return null;
            if (s.home > s.away) return "H";
            if (s.home === s.away) return "D";
            return "A";
        },
        scoreAnswered(matchId) {
            const s = this.scores[matchId];
            return !!s && s.home !== null && s.away !== null;
        },
        isScorePickerOpen(matchId) {
            return this.openId === matchId;
        },
        scoreChips() {
            return this.openSeg ? WHAT_IF_CHIPS[this.openSeg] : [];
        },
        isChipSelected(matchId, home, away) {
            const s = this.scores[matchId];
            return !!s && s.home === home && s.away === away;
        },
        openScorePicker(matchId, seg, side) {
            this.openId = matchId;
            this.openSeg = seg;
            this.focusSide = side ?? "home";
        },
        closeScorePicker() {
            this.openId = null;
            this.openSeg = null;
        },
        focusScoreBox(matchId, side) {
            if (!this.roundOpen) return;

            if (this.isScorePickerOpen(matchId) && this.focusSide === side) {
                this.closeScorePicker();
                return;
            }

            if (!this.scoreAnswered(matchId)) {
                const seg = this.isScorePickerOpen(matchId) && this.openSeg
                    ? this.openSeg
                    : ["H", "D", "A"][Math.floor(Math.random() * 3)];
                this.openScorePicker(matchId, seg, side);
                return;
            }
            this.openScorePicker(matchId, this.scoreOutcome(matchId) ?? this.openSeg ?? "H", side);
        },
        setMatchScore(matchId, home, away) {
            this.scores[matchId] = { home, away };
            this.hasEdited = true;
            this._saveWhatIfSession();
        },
        pickScore(matchId, home, away) {
            if (this.isChipSelected(matchId, home, away)) {
                this.closeScorePicker();
                return;
            }
            this.setMatchScore(matchId, home, away);
        },
        // Excludes the current score so a roll always visibly changes something.
        rollChip(matchId, seg) {
            const s = this.scores[matchId];
            const options = WHAT_IF_CHIPS[seg].filter(([h, a]) => !(s && h === s.home && a === s.away));
            return options[Math.floor(Math.random() * options.length)];
        },
        toggleSeg(matchId, seg) {
            if (!this.roundOpen) return;
            if (this.openId === matchId && this.openSeg === seg) {
                this.closeScorePicker();
                return;
            }
            this.openScorePicker(matchId, seg, seg === "A" ? "away" : "home");
            // Never clobber a score that already matches this outcome.
            if (this.scoreOutcome(matchId) !== seg) {
                const [h, a] = this.rollChip(matchId, seg);
                this.setMatchScore(matchId, h, a);
            }
        },
        rerollScore(matchId) {
            if (!this.openSeg) return;
            const [h, a] = this.rollChip(matchId, this.openSeg);
            this.setMatchScore(matchId, h, a);
            this.rollingId = matchId;
            setTimeout(() => {
                this.rollingId = null;
            }, 300);
        },
        bumpScore(matchId, delta) {
            if (!this.roundOpen) return;
            const s = this.scores[matchId];
            const current = s[this.focusSide];
            s[this.focusSide] = Math.min(9, Math.max(0, (current ?? 0) + delta));
            if (s.home === null) s.home = 0;
            if (s.away === null) s.away = 0;
            this.hasEdited = true;
            this._saveWhatIfSession();
        },
        remainingCount() {
            return this.scoreableMatches().filter((m) => !this.scoreAnswered(m.matchId)).length;
        },
        scoresDirty() {
            if (!this.hasComputed || !this.appliedScores) return false;
            return JSON.stringify(this.scores) !== JSON.stringify(this.appliedScores);
        },
        // The 200ms lead-in gives "Reverting…" time to register as the cause of the change.
        revertScores() {
            if (!this.appliedScores || this.isReverting) return;
            this.isReverting = true;
            setTimeout(() => {
                this.scores = JSON.parse(JSON.stringify(this.appliedScores));
                this.hasEdited = true;
                this.openId = null;
                this.openSeg = null;
                this.rollingId = null;
                this._saveWhatIfSession();
                setTimeout(() => {
                    this.isReverting = false;
                }, 200);
            }, 200);
        },
        confirmReset() {
            if (window.confirm("Reset your what-if? This clears all entered scores. Your swaps stay.")) {
                this.resetWhatIf();
            }
        },
        // Sandbox swaps are unlimited — there's no real swap quota to enforce here.
        exceedsLimit() {
            return false;
        },
        teamClick(teamCode) {
            // Locked round: the table below is the prediction the user is now stuck with, so
            // rearranging it would only produce a score they can't have. Read-only from here.
            if (!this.roundOpen) return;
            if (this.selectedTeam === null) {
                this._selectTeam(teamCode);
                return;
            }
            if (this.selectedTeam === teamCode) {
                this.selectedTeam = null;
                return;
            }
            this._performSwap(teamCode);
        },
        // Wraps the inherited swap mechanics to also log the swap in the same
        // {teamACode, teamAFrom, teamATo, ...} shape predictions.html's "Swap History" uses.
        _performSwap(teamCode) {
            const teamACode = this.selectedTeam;
            const teamBCode = teamCode;
            const teamA = this.teams.find((t) => t.code === teamACode);
            const teamB = this.teams.find((t) => t.code === teamBCode);
            const teamAFrom = teamA ? teamA.position : null;
            const teamBFrom = teamB ? teamB.position : null;

            originalPerformSwap.call(this, teamCode);

            if (teamAFrom !== null && teamBFrom !== null) {
                this.swapLog.push({
                    teamACode,
                    teamAFrom,
                    teamATo: teamBFrom,
                    teamBCode,
                    teamBFrom,
                    teamBTo: teamAFrom,
                });
            }
            this._saveWhatIfSession();
        },
        // _predictionBase has canUndo()/swapStack/_swapTeamsDirect/undoing, but undoLastSwap()
        // lives on predictionPage/guestPredictionPage. Ported with this page's storage, popping
        // swapLog in step so the log never shows an undone swap.
        undoLastSwap() {
            if (!this.canUndo() || this.undoing) return;
            this.undoing = true;
            const last = this.swapStack.pop();
            this.swapLog.pop();
            setTimeout(() => {
                this._swapTeamsDirect(last.b, last.a);
                this._saveWhatIfSession();
                setTimeout(() => {
                    this.undoing = false;
                }, 200);
            }, 200);
        },
        swapHint() {
            // Deliberately terse: the swap icon beside it carries the meaning, and the gesture
            // is self-teaching after a use or two. Only the selected state needs to say more,
            // since that is the one moment the next tap does something non-obvious.
            if (this.selectedTeam) {
                return `${this.selectedTeam} selected — tap another`;
            }
            return "Tap teams to swap";
        },
        resetSwaps() {
            this.reset();
            this.swapLog = [];
            // Nothing left whose positions could need explaining.
            this.swapsRebased = false;
            this._saveWhatIfSession();
        },
        allScoresEntered() {
            const scoreable = this.scoreableMatches();
            return (
                scoreable.length > 0 &&
                scoreable.every((m) => {
                    const s = this.scores[m.matchId];
                    return s && Number.isInteger(s.home) && s.home >= 0 && Number.isInteger(s.away) && s.away >= 0;
                })
            );
        },
        // Sorted by whatever currentStandings currently holds (real before Apply,
        // what-if after apply() reassigns it).
        standingsRows() {
            return Object.keys(this.currentStandings)
                .map((code) => {
                    const team = this.teams.find((t) => t.code === code);
                    return {
                        teamCode: code,
                        teamShortName: team ? team.shortName || team.name : code,
                        position: this.currentStandings[code],
                        points: this.currentPoints[code],
                        gd: this.currentGoalDifference[code],
                    };
                })
                .sort((a, b) => a.position - b.position);
        },
        totalHit() {
            return this.teams.reduce((sum, t) => {
                const d = this.getDelta(t.code);
                return sum + (typeof d === "number" ? d : 0);
            }, 0);
        },
        totalScore() {
            return Math.max(0, this.maxHitPoints - this.totalHit());
        },
        // The only network call this page makes — every swap afterward recomputes purely
        // client-side via the reactive getters above (standingsRows/totalHit/totalScore).
        apply() {
            if (!this.allScoresEntered() || this.isComputing) return;
            this.isComputing = true;
            this.errorMessage = null;
            // Every score is in by this point, so an open picker is finished with — and the result
            // it is about to be replaced by wants the room. Closed here rather than on success, so
            // a failed compute doesn't leave a stray panel open over the error.
            this.closeScorePicker();

            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
            const headers = { "Content-Type": "application/json" };
            if (csrfToken) headers["X-CSRF-TOKEN"] = csrfToken;

            const body = {
                scores: this.scoreableMatches().map((m) => ({
                    matchId: m.matchId,
                    homeGoals: this.scores[m.matchId].home,
                    awayGoals: this.scores[m.matchId].away
                }))
            };

            fetch("/predictions/user/what-if/compute", {
                method: "POST",
                headers,
                body: JSON.stringify(body)
            })
                .then((r) => r.json().then((data) => ({ ok: r.ok, data })))
                .then(({ ok, data }) => {
                    this.isComputing = false;
                    if (!ok || !data.success) {
                        this.errorMessage = (data && data.message) || "Something went wrong";
                        return;
                    }
                    this.currentStandings = data.standingsMap;
                    this.currentPoints = data.pointsMap;
                    this.currentGoalDifference = data.goalDifferenceMap;
                    this.hasComputed = true;
                    this.appliedScores = JSON.parse(JSON.stringify(this.scores));
                    this.activeTab = "result";
                    this._saveWhatIfSession();
                })
                .catch(() => {
                    this.isComputing = false;
                    this.errorMessage = "Failed to compute. Check your connection.";
                });
        },
        // Clears the scores, not the swaps: the swap controls in the tables section have their own
        // Reset for those, and this button sits under the score entry it undoes.
        resetWhatIf() {
            // Blanks only what's tracked — keying off every match would put called-off ones back
            // into `scores`, which nothing else on the page (or the server) counts.
            this.scoreableMatches().forEach((m) => {
                this.scores[m.matchId] = { home: null, away: null };
            });
            this.hasEdited = false;
            this.openId = null;
            this.openSeg = null;
            this.rollingId = null;
            this.selectedTeam = null;

            this.currentStandings = parsed.currentStandings;
            this.currentPoints = parsed.currentPoints;
            this.currentGoalDifference = parsed.currentGoalDifference;
            this.hasComputed = false;
            this.appliedScores = null;
            this.activeTab = "standings";
            this.errorMessage = null;
            this._clearWhatIfSession();
        }
    });
};

// --- Historical What-If recap card ---

// Standalone component (its own top-level card, outside the predictionPage scope) covering the
// collapsible, the per-bucket modal, and the share text. The modal is teleported to <body>, so its
// state has to live here rather than in the collapsible's own throwaway x-data.
window.Ligitabl.whatIfRecapCard = function (el) {
    return {
        open: false,
        whatIfRecapData: { round: null, played: 0, all: [], wins: [], draws: [], losses: [] },
        whatIfRecapPopup: null,
        whatIfRecapPopupClosing: false,
        whatIfRecapCopied: false,

        init() {
            this.whatIfRecapData = Ligitabl._parseJSON(el?.dataset?.whatIfRecap, this.whatIfRecapData);
        },

        showWhatIfRecapPopup(bucket) {
            const lines = this.whatIfRecapData[bucket] || [];
            if (lines.length === 0) return;
            this.whatIfRecapPopupClosing = false;
            this.whatIfRecapCopied = false;
            this.whatIfRecapPopup = { bucket, lines };
        },

        hideWhatIfRecapPopup() {
            if (!this.whatIfRecapPopup) return;
            this.whatIfRecapPopupClosing = true;
            setTimeout(() => {
                this.whatIfRecapPopup = null;
                this.whatIfRecapPopupClosing = false;
            }, 300);
        },

        whatIfRecapTitle() {
            if (!this.whatIfRecapPopup) return "";
            const labels = { all: "What-If recap", wins: "Wins", draws: "Draws", losses: "Losses" };
            return labels[this.whatIfRecapPopup.bucket] || "";
        },

        // Take the whole line, not just its grade: an exact call is drawn differently from a
        // merely-correct one, and `exact` rides along on the line from the server.
        whatIfRecapMark(line) {
            return whatIfGradeMark(line.grade, line.exact);
        },

        whatIfRecapMarkClass(line) {
            return whatIfGradeMarkClass(line.grade, line.exact);
        },

        whatIfRecapNumber(index) {
            return String(index + 1).padStart(2, "0");
        },

        // Shareable summary of the whole round — always the full list, not just the open bucket:
        // "3W 1D 1L" only reads correctly against every match.
        buildWhatIfRecapShareText() {
            const d = this.whatIfRecapData;
            const emoji = { WIN: "✅", DRAW: "🟡", LOSS: "❌" };
            const header =
                `My GW${d.round} Predictions — ` +
                `${d.wins.length}W ${d.draws.length}D ${d.losses.length}L ⚽`;
            const lines = d.all.map(
                (l, i) =>
                    `${this.whatIfRecapNumber(i)}. ${l.homeTeamCode} – ${l.awayTeamCode}  ${l.actualScore.replace(/ /g, "")}  ${l.guessedOutcome} ${emoji[l.grade] || ""}`
            );
            return `${header}\n${lines.join("\n")}\n\nPredict the table — LigiPredictor.com`;
        },

        copyWhatIfRecap() {
            navigator.clipboard.writeText(this.buildWhatIfRecapShareText());
            this.whatIfRecapCopied = true;
            setTimeout(() => {
                this.whatIfRecapCopied = false;
            }, 2000);
        }
    };
};

// --- Results Banner Dismissal ---

(function () {
    function dismissResultsBanner(roundNumber) {
        var csrfToken =
            document.querySelector('meta[name="_csrf"]')?.content;
        var headers = {"Content-Type": "application/json"};
        if (csrfToken) {
            headers["X-CSRF-TOKEN"] = csrfToken;
        }

        fetch(
            "/my-table/latest-result-banner/dismiss?round=" +
                roundNumber,
            {method: "POST", headers: headers},
        ).catch(function(err) {
            console.warn("Failed to dismiss results banner:", err);
        });

        var host = document.getElementById("results-banner");
        if (host) host.remove();
    }

    // Handle direct clicks on dismiss buttons
    document.addEventListener("click", function (event) {
        var trigger = event.target.closest
            ? event.target.closest('[data-dismiss-results-banner="true"]')
            : null;
        if (!trigger) return;

        var banner = document.querySelector("[data-result-round]");
        var roundNumber = banner
            ? banner.getAttribute("data-result-round")
            : null;
        if (roundNumber) {
            dismissResultsBanner(roundNumber);
        }
    }, true); // Use capture phase to ensure we catch it before HTMX

    // Also dismiss when HTMX navigation happens from within the banner
    document.body.addEventListener("htmx:beforeRequest", function(event) {
        var trigger = event.detail.elt;
        if (!trigger) return;

        var hasDismissAttr = trigger.getAttribute('data-dismiss-results-banner') === 'true';
        if (!hasDismissAttr) return;

        var banner = document.querySelector("[data-result-round]");
        var roundNumber = banner
            ? banner.getAttribute("data-result-round")
            : null;
        if (roundNumber) {
            dismissResultsBanner(roundNumber);
        }
    });
})();

// --- Round Navigation Loading Bar ---
// NProgress-style sliding bar for HTMX round navigation swaps
(function () {
    const BAR_ID = 'nav-loading-bar';
    let timer = null;

    function getBar() {
        let bar = document.getElementById(BAR_ID);
        if (!bar) {
            bar = document.createElement('div');
            bar.id = BAR_ID;
            const isMobile = window.matchMedia('(max-width: 767px)').matches;
            bar.style.cssText = [
                'position:fixed',
                'top:0',
                'left:0',
                'width:0%',
                'height:' + (isMobile ? '5px' : '3px'),
                'background:#6366f1',
                'z-index:9999',
                'transition:width 0.3s ease,opacity 0.4s ease',
                'opacity:0',
                'pointer-events:none',
            ].join(';');
            document.body.appendChild(bar);
        }
        return bar;
    }

    function start() {
        const bar = getBar();
        // Reset
        bar.style.transition = 'none';
        bar.style.width = '0%';
        bar.style.opacity = '1';
        // Force reflow so transition kicks in
        bar.offsetWidth;
        bar.style.transition = 'width 0.3s ease, opacity 0.4s ease';
        bar.style.width = '70%';
        // Creep toward 90% slowly to show activity
        if (timer) clearTimeout(timer);
        timer = setTimeout(() => { bar.style.width = '90%'; }, 1000);
    }

    function finish() {
        if (timer) clearTimeout(timer);
        const bar = getBar();
        bar.style.width = '100%';
        setTimeout(() => { bar.style.opacity = '0'; }, 300);
        setTimeout(() => {
            bar.style.transition = 'none';
            bar.style.width = '0%';
        }, 700);
    }

    const NAV_TARGETS = ['prediction-page', 'matches-page', 'standings-page'];
    const PAGINATION_TARGETS = ['leaderboard-content'];

    document.body.addEventListener('htmx:beforeRequest', function (e) {
        const targetId = e.detail?.target?.id;
        if (NAV_TARGETS.includes(targetId) || PAGINATION_TARGETS.includes(targetId)) {
            start();
        }
    });

    document.body.addEventListener('htmx:afterSwap', function (e) {
        const targetId = e.detail?.target?.id;
        if (NAV_TARGETS.includes(targetId) || PAGINATION_TARGETS.includes(targetId)) {
            finish();
            // Whole-page swaps, so land at the top with 'auto': smooth-scrolling a long table reads
            // as drift, not arriving somewhere new.
            window.scrollTo({ top: 0, behavior: 'auto' });
        }
    });

    document.body.addEventListener('htmx:responseError', function () {
        finish();
    });

    // Exposed so plain (non-htmx) full-page navigations — e.g. clicking a contest
    // name link — can flash the bar on click. No matching finish() call is needed:
    // the page unload naturally clears it, and the new page starts with a fresh bar.
    window.flashNavLoadingBar = start;

    // Same treatment for every other same-tab link, delegated rather than an onclick per anchor:
    // a full-page navigation shows nothing until the new document paints, which on a slow
    // connection is the same dead air the bar exists to fill.
    document.addEventListener('click', function (e) {
        if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;

        const link = e.target.closest && e.target.closest('a[href]');
        if (!link) return;

        // htmx drives these itself and already triggers the bar via beforeRequest.
        if (link.hasAttribute('hx-get') || link.hasAttribute('hx-post')) return;
        // Opens elsewhere, or isn't a navigation at all.
        if (link.target && link.target !== '_self') return;
        if (link.hasAttribute('download')) return;

        const href = link.getAttribute('href');
        if (!href || href.startsWith('#') || /^(mailto|tel|javascript):/i.test(href)) return;

        // Off-site: the bar would be left running against a page we never control.
        if (link.origin && link.origin !== window.location.origin) return;
        // Same page — no navigation, so nothing to wait for.
        if (link.href === window.location.href) return;

        start();
    });
})();

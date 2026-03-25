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

// --- Shared helpers for prediction components ---

window.Ligitabl._parseJSON = function (raw, fallback) {
    try {
        return JSON.parse(raw);
    } catch (e) {
        return fallback;
    }
};

window.Ligitabl._parseDataAttributes = function (el) {
    const p = Ligitabl._parseJSON;
    return {
        predictions: p(el?.dataset?.predictions, []),
        currentStandings: p(el?.dataset?.currentStandings, {}),
        fixtures: p(el?.dataset?.fixtures, {}),
        currentPoints: p(el?.dataset?.currentPoints, {}),
        currentGoalDifference: p(el?.dataset?.currentGoalDifference, {}),
    };
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
        alwaysHoverable: false,
        isInitialPrediction: false,
        showStandings: savedPrefs ? (savedPrefs.showStandings ?? false) : false,
        showFixtures: savedPrefs ? (savedPrefs.showFixtures ?? false) : false,
        showPoints: savedPrefs ? (savedPrefs.showPoints ?? false) : false,
        showGD: savedPrefs ? (savedPrefs.showGD ?? false) : false,
        currentStandings: parsed.currentStandings,
        fixtures: parsed.fixtures,
        currentPoints: parsed.currentPoints,
        currentGoalDifference: parsed.currentGoalDifference,

        getCurrentPoints(teamCode) {
            return this.currentPoints[teamCode] || "-";
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

        isSelected(teamCode) {
            return this.selectedTeam === teamCode;
        },

        isDirty(teamCode) {
            const team = this.teams.find((t) => t.code === teamCode);
            const original = this.originalTeams.find((t) => t.code === teamCode);
            if (!team || !original) return false;
            return team.position !== original.position;
        },

        getDirtyCount() {
            return this.teams.filter((t) => this.isDirty(t.code)).length;
        },

        getSwapCount() {
            // Use permutation cycle decomposition: for a cycle of length k,
            // the minimum swaps needed = k - 1. Sum across all cycles.
            const visited = new Set();
            let swapCount = 0;

            for (const team of this.teams) {
                if (visited.has(team.code) || !this.isDirty(team.code)) continue;

                // Trace the full cycle starting from this team
                let cycleLength = 0;
                let currentCode = team.code;

                while (!visited.has(currentCode)) {
                    visited.add(currentCode);
                    cycleLength++;

                    // Find the original position of the current team
                    const originalPos = this.originalTeams.find(
                        (t) => t.code === currentCode
                    )?.position;

                    // Find who is currently occupying that original position
                    const next = this.teams.find((t) => t.position === originalPos);
                    if (!next || next.code === currentCode) break;

                    currentCode = next.code;
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
                    const original = this.originalTeams.find((o) => o.code === t.code);
                    const change = original.position - t.position;
                    return {
                        name: t.name,
                        code: t.code,
                        from: original.position,
                        to: t.position,
                        direction: change > 0 ? "up" : "down",
                        amount: Math.abs(change),
                    };
                })
                .sort((a, b) => a.from - b.from);
        },

        getPositionChange(teamCode) {
            const team = this.teams.find((t) => t.code === teamCode);
            const original = this.originalTeams.find((t) => t.code === teamCode);
            if (!team || !original) return null;
            const change = original.position - team.position;
            if (change === 0) return null;
            return change > 0 ? `↑${change}` : `↓${Math.abs(change)}`;
        },

        getSelectedTeamName() {
            if (!this.selectedTeam) return null;
            const team = this.teams.find((t) => t.code === this.selectedTeam);
            return team ? team.name : null;
        },

        pushSwap(codeA, codeB) {
            const top = this.swapStack[this.swapStack.length - 1];
            if (top && top.a === codeB && top.b === codeA) {
                // Exact reverse of last swap — cancel it out
                this.swapStack.pop();
            } else {
                this.swapStack.push({ a: codeA, b: codeB });
            }
        },

        _swapTeamsDirect(codeA, codeB) {
            const index1 = this.teams.findIndex((t) => t.code === codeA);
            const index2 = this.teams.findIndex((t) => t.code === codeB);
            if (index1 < 0 || index2 < 0) return;
            const row1 = document.querySelector(`[data-team-code='${codeA}']`);
            const row2 = document.querySelector(`[data-team-code='${codeB}']`);
            if (row1) row1.classList.add("swapping");
            if (row2) row2.classList.add("swapping");
            // Let the browser paint the class before mutating data
            requestAnimationFrame(() => {
                const temp = this.teams[index1];
                this.teams[index1] = this.teams[index2];
                this.teams[index2] = temp;
                this.teams.forEach((team, idx) => (team.position = idx + 1));
                setTimeout(() => {
                    if (row1) row1.classList.remove("swapping");
                    if (row2) row2.classList.remove("swapping");
                }, 200);
            });
        },

        canUndo() {
            return this.swapStack.length > 0 && this.selectedTeam === null;
        },

        getActualPosition(teamCode) {
            return this.currentStandings[teamCode] || "?";
        },

        getDelta(teamCode) {
            const team = this.teams.find((t) => t.code === teamCode);
            const actual = this.getActualPosition(teamCode);
            if (!team || actual === "?") return "-";
            return Math.abs(team.position - actual);
        },

        getDeltaDirection(teamCode) {
            const team = this.teams.find((t) => t.code === teamCode);
            const actual = this.getActualPosition(teamCode);
            if (!team || actual === "?") return null;
            return team.position > actual ? "up" : "down";
        },

        // Shared swap mechanics (visual feedback + array swap)
        _performSwap(teamCode, usePreSwapAnimation) {
            const index1 = this.teams.findIndex((t) => t.code === this.selectedTeam);
            const index2 = this.teams.findIndex((t) => t.code === teamCode);
            if (index1 < 0 || index2 < 0) {
                this.selectedTeam = null;
                return;
            }

            const team1Code = this.selectedTeam;
            const team2Code = teamCode;
            const row1 = document.querySelector(`[data-team-code='${team1Code}']`);
            const row2 = document.querySelector(`[data-team-code='${team2Code}']`);

            if (usePreSwapAnimation) {
                if (row1) row1.classList.add("pre-swapping");
                if (row2) row2.classList.add("pre-swapping");
                setTimeout(() => {
                    this.selectedTeam = null;
                }, 10);
                setTimeout(() => {
                    if (row1) {
                        row1.classList.remove("pre-swapping");
                        row1.classList.add("swapping");
                        setTimeout(() => row1.classList.remove("swapping"), 600);
                    }
                    if (row2) {
                        row2.classList.remove("pre-swapping");
                        row2.classList.add("swapping");
                        setTimeout(() => row2.classList.remove("swapping"), 600);
                    }
                }, 80);
            } else {
                if (row1) row1.classList.add("swapping");
                if (row2) row2.classList.add("swapping");
                setTimeout(() => {
                    if (row1) row1.classList.remove("swapping");
                    if (row2) row2.classList.remove("swapping");
                }, 600);
                this.selectedTeam = null;
            }

            const temp = this.teams[index1];
            this.teams[index1] = this.teams[index2];
            this.teams[index2] = temp;
            this.teams.forEach((team, idx) => (team.position = idx + 1));
            this.pushSwap(team1Code, team2Code);
        },

        // Shared selection handling
        _selectTeam(teamCode) {
            this.selectedTeam = teamCode;
            this.$nextTick(() => {
                const row = document.querySelector(`[data-team-code='${teamCode}']`);
                if (row) {
                    row.classList.add("selected-pulse");
                    setTimeout(() => row.classList.remove("selected-pulse"), 300);
                }
            });
        },

        _saveToStorage(key) {
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

        _clearStorage(key) {
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

// Helper to build team array from server predictions
window.Ligitabl._mapServerPredictions = function (predictions) {
    return (Array.isArray(predictions) ? predictions : []).map((p) => ({
        position: p.position,
        code: p.teamCode,
        name: p.teamName,
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
    const canSwap = canSwapRaw === "true" || canSwapRaw === "True";
    const canInteract = canInteractRaw === "true" || canInteractRaw === "True";
    const isRoundOpen = roundOpenRaw === "true" || roundOpenRaw === "True";
    const isLastRound = isLastRoundRaw === "true" || isLastRoundRaw === "True";
    const isInitialPrediction = isInitialRaw === "true" || isInitialRaw === "True";
    const MAX_INITIAL_SWAPS = Ligitabl._MAX_INITIAL_SWAPS;

    const userId = el?.dataset?.userId || 'unknown';
    const roundId = el?.dataset?.roundId || 'unknown';
    const GUEST_STORAGE_KEY = "ligitabl.guestPrediction";
    const AUTH_STORAGE_KEY = "ligitabl.prediction." + userId;

    function _validateSaved(saved) {
        if (!saved) return false;
        // Discard immediately if this data is from a different round
        if (saved.roundId !== roundId) return false;
        // Then verify team codes still match the server set
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

    return Object.assign(base, {
        canSwap,
        canInteract,
        isRoundOpen,
        isLastRound,
        isInitialPrediction,
        isSaving: false,
        errorMessage: null,
        importedFromGuest: false,

        init() {
            if (isInitialPrediction) {
                // 1. Auth localStorage takes priority — user has already made swaps after signing up
                const authPrediction = loadAuthPrediction();
                if (authPrediction) {
                    this.teams = _extractTeams(authPrediction).map((t, idx) => ({...t, position: idx + 1}));
                    this.swapStack = _extractSwapStack(authPrediction);
                    // Clear stale guest storage since auth has taken over
                    this._clearStorage(GUEST_STORAGE_KEY);
                }

                // 2. No auth data — fall back to guest localStorage (just signed up, no auth swaps yet)
                if (this.teams.length === 0) {
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

            // originalTeams always reflects server state — diffs are against what was last submitted
            this.originalTeams = Ligitabl._mapServerPredictions(predictions);

            // Persist display preferences
            const savePrefs = () => Ligitabl._savePrefs({
                showStandings: this.showStandings,
                showFixtures: this.showFixtures,
                showPoints: this.showPoints,
                showGD: this.showGD,
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
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
            this._performSwap(teamCode, true);
            this._saveToStorage(AUTH_STORAGE_KEY);
        },

        canUpdate() {
            const swapCount = this.getSwapCount();
            if (swapCount === 0) return false;
            if (this.isInitialPrediction) {
                if (swapCount > MAX_INITIAL_SWAPS) return false;
            } else {
                if (swapCount > 1) return false;
            }
            return this.canSwap;
        },

        exceedsLimit() {
            if (this.isInitialPrediction) {
                return this.getSwapCount() > MAX_INITIAL_SWAPS;
            }
            return this.getSwapCount() > 1;
        },

        getChangeSummary() {
            const changed = this.getChangedTeams();
            if (changed.length === 0) return null;
            return {
                teamCount: changed.length,
                swapCount: this.getSwapCount(),
                pairs: this.inferSwapPairs(changed),
            };
        },

        inferSwapPairs(changedTeams) {
            const pairs = [];
            const processed = new Set();
            for (const team of changedTeams) {
                if (processed.has(team.code)) continue;
                const partner = changedTeams.find(
                    (t) =>
                        !processed.has(t.code) && t.to === team.from && t.from === team.to,
                );
                if (partner) {
                    pairs.push({
                        team1: team.name,
                        team2: partner.name,
                        pos1: team.from,
                        pos2: partner.from,
                    });
                    processed.add(team.code);
                    processed.add(partner.code);
                } else {
                    pairs.push({
                        team1: team.name,
                        team2: null,
                        pos1: team.from,
                        pos2: team.to,
                        isComplex: true,
                    });
                    processed.add(team.code);
                }
            }
            return pairs;
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
                this._swapTeamsDirect(last.b, last.a); // reverse
                this._saveToStorage(AUTH_STORAGE_KEY);
                setTimeout(() => { this.undoing = false; }, 200);
            }, 200);
        },

        submitChanges() {
            this.isSaving = true;
            const toast = document.getElementById("saving-toast");
            if (toast) toast.classList.remove("hidden");

            let url, body;

            if (this.isInitialPrediction) {
                // Initial prediction: send all swap pairs (1-5) as a list
                url = "/seasonprediction";
                body = {
                    swaps: this.swapStack.map((entry) => ({
                        teamACode: entry.a,
                        teamBCode: entry.b,
                    })),
                };
            } else {
                // Swap: send the single pair of team codes
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
                        if (this.importedFromGuest || this.isInitialPrediction) {
                            this._clearStorage(GUEST_STORAGE_KEY);
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

// Re-init Alpine after HTMX swaps
document.body.addEventListener("htmx:afterSwap", (event) => {
    if (!window.Alpine || !event.detail?.target) return;
    const target = event.detail.target;
    if (!target.querySelector || !target.querySelector("[x-data]")) return;
    window.Alpine.initTree(target);
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

// Format [data-timestamp] elements to the user's local timezone and locale.
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
        try {
            const d = new Date(iso);
            if (isNaN(d.getTime())) return;
            el.textContent = d.toLocaleString(undefined, {
                dateStyle: "medium",
                timeStyle: "short",
            });
        } catch (e) {
            // leave ISO string as-is
        }
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
                // Discard immediately if this data is from a different round
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

        init() {
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
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
        },

        teamClick(teamCode) {
            if (this.selectedTeam === null) {
                this._selectTeam(teamCode);
                return;
            }
            if (this.selectedTeam === teamCode) {
                this.selectedTeam = null;
                return;
            }
            this._performSwap(teamCode, false);
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
                this._swapTeamsDirect(last.b, last.a); // reverse
                this._saveToStorage(STORAGE_KEY);
                setTimeout(() => { this.undoing = false; }, 200);
            }, 200);
        },
    });
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
            bar.style.cssText = [
                'position:fixed',
                'top:0',
                'left:0',
                'width:0%',
                'height:3px',
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
        }
        if (PAGINATION_TARGETS.includes(targetId)) {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    });

    document.body.addEventListener('htmx:responseError', function () {
        finish();
    });
})();

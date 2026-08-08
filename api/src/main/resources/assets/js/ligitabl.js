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
        formData: p(el?.dataset?.form, {}) || {},
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
                this.formPopup = { teamCode, teamName, entries };
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

        formResultLabel(entry, teamCode) {
            return entry.wasHome
                ? teamCode + ' ' + entry.goalsFor + '–' + entry.goalsAgainst + ' ' + entry.opponentCode
                : entry.opponentCode + ' ' + entry.goalsAgainst + '–' + entry.goalsFor + ' ' + teamCode;
        },

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

        // WIN/LOSS are inverted relative to the fixture chip (chip green/WIN -> badge red,
        // chip red/LOSS -> badge green); LIVE/DRAW/POSTPONED stay the same on both.
        teamBadgeClasses(teamCode) {
            const fixtures = this.getFixtures(teamCode);
            if (fixtures.some((f) => f.status === 'LIVE')) return 'bg-blue-50 text-blue-700';
            if (fixtures.some((f) => f.result === 'WIN')) return 'bg-red-50 text-red-700';
            if (fixtures.some((f) => f.result === 'LOSS')) return 'bg-green-50 text-green-700';
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

        getSelectedTeamShortName() {
            if (!this.selectedTeam) return null;
            const team = this.teams.find((t) => t.code === this.selectedTeam);
            return team ? (team.shortName || team.name) : null;
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

        _swapTeamsDirect(codeA, codeB, onSwapped) {
            const index1 = this.teams.findIndex((t) => t.code === codeA);
            const index2 = this.teams.findIndex((t) => t.code === codeB);
            if (index1 < 0 || index2 < 0) return;
            // Mutate first — triggers Alpine re-render
            const temp = this.teams[index1];
            this.teams[index1] = this.teams[index2];
            this.teams[index2] = temp;
            this.teams.forEach((team, idx) => (team.position = idx + 1));
            if (onSwapped) onSwapped();
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
        isOpeningRound,
        isPreSeasonRegistration,
        isSaving: false,
        errorMessage: null,
        importedFromGuest: false,

        init() {
            if (isInitialPrediction || isOpeningRound || isPreSeasonRegistration) {
                // 1. Auth localStorage takes priority — user has already made swaps after signing up.
                // Defensive: a pre-season registration is never supposed to have auth storage of its
                // own (it's a fresh, still-unedited round-0 row).
                const authPrediction = loadAuthPrediction();
                if (isPreSeasonRegistration && authPrediction) {
                    this._clearStorage(AUTH_STORAGE_KEY);
                } else if (authPrediction) {
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

            // This is the authenticated prediction page — guest storage is never the source
            // of truth here past the one-time import above, so it's always stale from this
            // point on regardless of which branch populated this.teams.
            this._clearStorage(GUEST_STORAGE_KEY);

            // originalTeams always reflects server state — diffs are against what was last submitted
            this.originalTeams = Ligitabl._mapServerPredictions(predictions);

            // Persist display preferences
            const savePrefs = () => Ligitabl._savePrefs({
                showStandings: this.showStandings,
                showFixtures: this.showFixtures,
                showPoints: this.showPoints,
                showGD: this.showGD,
                showForm: this.showForm,
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
            this.$watch("showForm", savePrefs);
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
                this._swapTeamsDirect(last.b, last.a, () => {
                    this._saveToStorage(AUTH_STORAGE_KEY);
                }); // reverse
                setTimeout(() => { this.undoing = false; }, 200);
            }, 200);
        },

        submitChanges() {
            this.isSaving = true;
            const toast = document.getElementById("saving-toast");
            if (toast) toast.classList.remove("hidden");

            let url, body;

            // Derive minimal swap pairs from the net permutation.
            // swapStack may have redundant moves; getSwapCount() reflects the true net count.
            const _working = this.originalTeams.map((t) => ({...t}));
            const _targetPosition = Object.fromEntries(this.teams.map((t) => [t.code, t.position]));
            const _derivedSwaps = [];
            for (const t of _working) {
                const tgt = _targetPosition[t.code];
                if (t.position === tgt) continue;
                const partner = _working.find((w) => w.position === tgt);
                if (!partner) continue;
                _derivedSwaps.push({teamACode: t.code, teamBCode: partner.code});
                const tmp = t.position;
                t.position = partner.position;
                partner.position = tmp;
            }

            if (this.isInitialPrediction || this.isPreSeasonRegistration) {
                url = "/seasonprediction";
                const _next = new URLSearchParams(window.location.search).get('next');
                if (_next) url += '?next=' + encodeURIComponent(_next);
                body = {swaps: _derivedSwaps};
            } else if (this.isOpeningRound) {
                url = "/seasonprediction/opening-swaps";
                body = {swaps: _derivedSwaps};
            } else {
                // Standard swap: send the single pair of team codes
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
        isOpeningRound: false,
        isPreSeasonRegistration: false,

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
                showForm: this.showForm,
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
            this.$watch("showForm", savePrefs);
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
            }, this._prefsKey);
            this.$watch("showStandings", savePrefs);
            this.$watch("showFixtures", savePrefs);
            this.$watch("showPoints", savePrefs);
            this.$watch("showGD", savePrefs);
            this.$watch("showForm", savePrefs);
        },
    });
};

// --- Final Table Predictor ---------------------------------------------------
//
// The select-and-swap behaviour only, extracted so finalTablePage can reuse it without
// inheriting predictionPage's cooldown state, round navigation, what-if, results banners and
// access modes — nine months of main-game concerns it would then have to suppress.
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
            this.teams.forEach((team, idx) => (team.position = idx + 1));

            if (onSwapped) onSwapped();

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

        // Neutral badge: pre-GW1 there are no results to tint by, unlike the main table's
        // form-driven colouring.
        teamBadgeClasses() {
            return 'bg-gray-200 text-gray-700';
        },

        // --- qualification zones -------------------------------------------------
        //
        // Read from data-zones so the bands follow the competition rather than hardcoding the
        // Premier League's shape. Shape: {"CL":[1,5],"UEL":[6,7],"UECL":[8,8],"REL":[18,20]} —
        // inclusive position ranges, any subset, empty object for a league with no zones.
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
         * A faint zone wash on the row itself, as in the reference. Deliberately at the -50 step so
         * it stays below the selected (blue-50) and dirty (amber-50) tints, which must remain the
         * loudest thing on a row — those are the states the player is acting on.
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
         * Back to the page as freshly rendered — table, tints, arrows and hints all cleared.
         *
         * <p>Deliberately restores from {@code originalTeams} rather than re-running init(): init()
         * rebuilds from the {@code data-rows} attribute, which is the order the page was *first*
         * served with. After a save that is stale, so resetting would revert past the player's saved
         * table to whatever they had on page load. {@code originalTeams} is re-baselined on every
         * successful save, so it is the only correct source.
         *
         * <p>Clearing message/messageKind is what stops the "N moved since last save" hint outliving
         * the reset that made it untrue.
         *
         * <p>Restores by reordering the existing team objects rather than assigning a deep copy.
         * `x-for` is keyed by team code, so Alpine reuses each row's DOM node either way — but the
         * `:class` binding's reactive dependency is registered against the *object* that was in
         * `teams` when it last ran. Replacing the array with fresh copies leaves those effects
         * watching orphaned objects that nothing writes to again, so `zoneRowClass(team.position)`
         * never re-evaluates and every row keeps the zone wash of the position it held before the
         * reset. Mutating the tracked objects in place is what actually notifies the bindings.
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
            // Otherwise gate on the table actually differing from the last saved order, not on the
            // pending list being non-empty. Swapping A↔B and back leaves two pending entries but a
            // table identical to the saved one; posting that would advance settledAt — the tiebreak
            // — for a change the player did not make.
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
                        this.hasEntry = true;
                        this._flash(data.message || 'Saved', 'success');
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

// Share card: draws the table to an offscreen canvas and offers it as a PNG.
//
// Separate from finalTablePage so the public read-only view can mount it without the editing
// component. Text and colour blocks only — remote crest images would taint the canvas and make
// toBlob() throw SecurityError.
window.Ligitabl.finalTableShareCard = function (el) {
    const dataset = el?.dataset || {};

    return {
        // Collapsed by default, matching fragments/share-prediction.html: the panel is an offer,
        // not the main event.
        open: false,
        rendering: false,
        copiedText: false,
        copiedLink: false,
        // Feature-detected once: navigator.canShare with files is Safari/Chrome-mobile only, and a
        // button that silently does nothing is worse than an absent one.
        canShareFiles: false,

        init() {
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

        rows() {
            return Ligitabl._parseJSON(dataset.rows, []);
        },

        title() {
            return dataset.title || 'My Final Table';
        },

        shareUrl() {
            return dataset.shareUrl || '';
        },

        shareText() {
            return dataset.shareText || '';
        },

        subtitle() {
            return dataset.subtitle || '';
        },

        _drawCard() {
            // Two-column scorecard: a 20-row single column makes a tall, thin image that reads
            // badly in a timeline. Square is the social format.
            const rows = this.rows();
            const zones = Ligitabl._parseJSON(dataset.zones, {});
            // 1080 at 1x is already above every social service's display size, and dropping the 2x
            // supersample is most of the size win (5.76M pixels -> 1.17M). Text is drawn at final
            // size, not scaled, so it stays sharp.
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

            // The callout box starts at CALLOUT_X, so the title has to fit in what is left of the
            // width — at 62px "Premier League 26/27" ran straight under it. Shrink to fit rather
            // than truncate: the competition name is the whole point of the card.
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

            ctx.fillStyle = 'rgba(255,255,255,0.6)';
            ctx.font = '600 15px ' + FONT;
            ctx.fillText(this.shareUrl().replace(/^https?:\/\//, ''), 60, footerY + 46);

            ctx.fillStyle = ACCENT;
            ctx.textAlign = 'right';
            ctx.fillText('Build yours on LigiPredictor', W - 60, footerY + 46);
            ctx.textAlign = 'left';

            return canvas;
        },

        // JPEG, not PNG. Two things drove the ~2.6MB PNG: a 2x supersampled 1200x1200 canvas
        // (5.76M pixels) and PNG's lossless encoding of a full-bleed gradient, close to its worst
        // case. Now 1080x1080 at 1x (1.17M pixels, ~5x fewer) encoded as JPEG q0.9 — flat colour
        // and text are what JPEG handles well, so the visible result is unchanged.
        _toBlob(callback) {
            this._drawCard().toBlob(callback, 'image/jpeg', 0.9);
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
                    link.download = 'final-table.jpg';
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
                const file = new File([blob], 'final-table.jpg', { type: 'image/jpeg' });
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

// Three plausible scores per outcome, doubling as both the quick-pick chip strip and the
// random pool for the reroll dice. Matches the design in .art/gameweek-v5.html.
const WHAT_IF_CHIPS = {
    H: [[1, 0], [2, 0], [2, 1]],
    D: [[1, 1], [0, 0], [2, 2]],
    A: [[0, 1], [1, 2], [0, 2]]
};

// Matches that have been called off carry no hypothesis — the same exclusion
// ComputeWhatIfUseCase.isScoreable applies, kept in step with it. Everything else is scoreable,
// including matches already kicked off or finished: on a locked round the page replays the guess
// the user saved while it was open, never the real result.
const WHAT_IF_UNSCOREABLE_STATUSES = ["POSTPONED", "CANCELLED", "SUSPENDED"];
const isWhatIfScoreable = (match) => !WHAT_IF_UNSCOREABLE_STATUSES.includes(match.status);

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
        swapLog: [],
        activeTab: "standings",
        hasComputed: false,
        isComputing: false,
        isReverting: false,
        isRefreshing: false,
        // Transient "Already up to date" / "Updated from your last apply" note next to Refresh.
        refreshMessage: null,
        errorMessage: null,
        // Snapshot of `scores` as of the last successful apply() — compared against the live
        // `scores` to tell whether the computed result is stale (scoresDirty()), so the Apply
        // button can flag it ('*Apply') instead of silently showing a result that no longer
        // matches what's entered.
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
        init() {
            this.teams = Ligitabl._mapServerPredictions(parsed.predictions);
            this.originalTeams = Ligitabl._mapServerPredictions(parsed.predictions);
            this._restoreWhatIfSession();
            this._reconcileWithServer();
            this._applyIfComplete();
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
        // The server wins on scores: they're populated from it (not blanked) and the standings go
        // back to the real ones. Swaps are left alone — they're an arrangement of the user's own
        // table, and which scores are loaded doesn't make them wrong. Callers follow with
        // _applyIfComplete() to project the adopted scores against that same arrangement.
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
            this.swapLog = saved.swapLog || [];
            this.hasComputed = !!saved.hasComputed;
            this.currentStandings = saved.currentStandings || this.currentStandings;
            this.currentPoints = saved.currentPoints || this.currentPoints;
            this.currentGoalDifference = saved.currentGoalDifference || this.currentGoalDifference;
            this.appliedScores = saved.appliedScores || null;
            if (this._fixturesChangedSince(saved)) {
                this.swapsClearedByFixtureChange = this.swapLog.length > 0;
                this.reset(); // teams = originalTeams, selectedTeam = null, swapStack = []
                this.swapLog = [];
            }
            this._reconcileMatches();
            this._saveWhatIfSession();
            this.hasEdited = Object.values(this.scores).some((s) => s.home !== null || s.away !== null);
            if (this.hasComputed) this.activeTab = "result";
            return true;
        },
        // Resyncs the round itself — the fixtures and whether it's still open — from the payload
        // Refresh already fetches. Returns whether the change was one that invalidates the sandbox
        // (same rule as a restored session: a tracked match off SCHEDULED, or gone), in which case
        // the swaps and the projection built on the old fixtures go with it.
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
        // Swaps are the user's own exploration of their table and survive both a Reset and a
        // Refresh — pulling different scores doesn't make an arrangement of teams wrong. What does
        // is the fixtures moving underneath it: a match that has left SCHEDULED (the round locked,
        // or it kicked off) or dropped out of the round entirely means the table is now being scored
        // against a different set of games than the one it was arranged for.
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
        // _predictionBase provides canUndo()/swapStack/_swapTeamsDirect/undoing, but
        // undoLastSwap() itself is only defined on predictionPage/guestPredictionPage (like
        // teamClick was) — ported here with our own storage persistence instead of theirs, and
        // popping our own swapLog in step so the log doesn't show a swap that's just been undone.
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
            const hasSwaps = this.getSwapCount() > 0;
            if (this.selectedTeam) {
                return hasSwaps
                    ? `${this.selectedTeam} selected — tap another team to swap`
                    : `Tap another team to swap, or tap ${this.selectedTeam} again to deselect`;
            }
            return hasSwaps
                ? "Tap two teams to swap them"
                : "Tap a team to select it, then tap another to swap them.";
        },
        resetSwaps() {
            this.reset();
            this.swapLog = [];
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

        // Glyph for how a single guess graded, rendered in a coloured circle beside the (square)
        // guessed-outcome badge. A draw guess that landed within one goal is the "half" case —
        // right instinct, wrong result — hence the tilde rather than a tick. Loss is a dash rather
        // than a cross so it never reads as a repeat of a draw pick's "X".
        whatIfRecapMark(grade) {
            return { WIN: "✓", DRAW: "~", LOSS: "–" }[grade] || "";
        },

        whatIfRecapMarkClass(grade) {
            return (
                {
                    WIN: "bg-green-50 text-green-700",
                    DRAW: "bg-amber-50 text-amber-700",
                    LOSS: "bg-red-50 text-red-700"
                }[grade] || "bg-gray-100 text-gray-500"
            );
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
        }
        if (PAGINATION_TARGETS.includes(targetId)) {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    });

    document.body.addEventListener('htmx:responseError', function () {
        finish();
    });

    // Exposed so plain (non-htmx) full-page navigations — e.g. clicking a contest
    // name link — can flash the bar on click. No matching finish() call is needed:
    // the page unload naturally clears it, and the new page starts with a fresh bar.
    window.flashNavLoadingBar = start;
})();

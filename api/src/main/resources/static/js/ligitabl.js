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
    };
};

// Shared base for predictionPage and guestPredictionPage
window.Ligitabl._predictionBase = function (parsed, opts = {}) {
    return {
        teams: [],
        originalTeams: [],
        selectedTeam: null,
        alwaysHoverable: false,
        showStandings: false,
        showFixtures: false,
        showPoints: opts.showPoints ?? false,
        currentStandings: parsed.currentStandings,
        fixtures: parsed.fixtures,
        currentPoints: parsed.currentPoints,

        getCurrentPoints(teamCode) {
            return this.currentPoints[teamCode] || "-";
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

        reset() {
            this.teams = JSON.parse(JSON.stringify(this.originalTeams));
            this.selectedTeam = null;
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
    const isInitialPrediction =
        isInitialRaw === "true" || isInitialRaw === "True";

    const GUEST_STORAGE_KEY = "ligitabl.guestPrediction";

    function loadGuestPrediction() {
        try {
            const saved = localStorage.getItem(GUEST_STORAGE_KEY);
            if (saved) {
                const parsed = JSON.parse(saved);
                const serverCodes = new Set(predictions.map((p) => p.teamCode));
                const savedCodes = new Set(parsed.map((p) => p.code));
                if (
                    serverCodes.size === savedCodes.size &&
                    [...serverCodes].every((c) => savedCodes.has(c))
                ) {
                    return parsed;
                }
            }
        } catch (e) {
            console.warn("Failed to load guest prediction:", e);
        }
        return null;
    }

    function clearGuestStorage() {
        try {
            localStorage.removeItem(GUEST_STORAGE_KEY);
        } catch (e) {
            console.warn("Failed to clear guest prediction:", e);
        }
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

    const base = Ligitabl._predictionBase(parsed);

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
                const guestPrediction = loadGuestPrediction();
                if (guestPrediction) {
                    this.teams = guestPrediction.map((t, idx) => {
                        const serverData = serverDataByCode[t.code];
                        return {
                            position: idx + 1,
                            code: t.code,
                            name: t.name,
                            crestUrl: t.crestUrl,
                            originalPosition: serverData ? serverData.position : idx + 1,
                        };
                    });
                    this.importedFromGuest = true;
                }
            }

            if (this.teams.length === 0) {
                this.teams = Ligitabl._mapServerPredictions(predictions);
            }

            if (this.importedFromGuest) {
                this.originalTeams = Ligitabl._mapServerPredictions(predictions);
            } else {
                this.originalTeams = JSON.parse(JSON.stringify(this.teams));
            }
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
        },

        canUpdate() {
            const swapCount = this.getSwapCount();
            if (swapCount === 0) return false;
            if (this.isInitialPrediction) {
                if (swapCount > 3) return false;
            } else {
                if (swapCount > 1) return false;
            }
            return this.canSwap;
        },

        exceedsLimit() {
            if (this.isInitialPrediction) {
                return this.getSwapCount() > 3;
            }
            return this.getSwapCount() > 1;
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
            if (this.importedFromGuest) {
                clearGuestStorage();
                this.importedFromGuest = false;
                window.dispatchEvent(new CustomEvent('guest-storage-cleared'));
            }
        },

        submitChanges() {
            this.isSaving = true;
            const toast = document.getElementById("saving-toast");
            if (toast) toast.classList.remove("hidden");

            let url, body;

            if (this.isInitialPrediction) {
                // Initial prediction: send all swap pairs (1–3) as a list
                url = "/seasonprediction";
                const pairs = this.inferSwapPairs(this.getChangedTeams());
                body = {
                    swaps: pairs.map((pair) => {
                        const teamA = this.teams.find((t) => t.name === pair.team1);
                        const teamB = this.teams.find((t) => t.name === pair.team2);
                        return {teamACode: teamA.code, teamBCode: teamB.code};
                    }),
                };
            } else {
                // Swap: send the single pair of team codes
                const pairs = this.inferSwapPairs(this.getChangedTeams());
                const pair = pairs[0];
                const teamA = this.teams.find((t) => t.name === pair.team1);
                const teamB = this.teams.find((t) => t.name === pair.team2);
                url = "/seasonprediction/swap";
                body = {teamACode: teamA.code, teamBCode: teamB.code};
            }

            fetch(url, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(body),
            })
                .then((response) => response.json())
                .then((data) => {
                    if (data.success) {
                        if (this.importedFromGuest || this.isInitialPrediction) {
                            clearGuestStorage();
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

    function loadSavedPrediction() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved) {
                const p = JSON.parse(saved);
                const serverCodes = new Set(serverPredictions.map((s) => s.teamCode));
                const savedCodes = new Set(p.map((s) => s.code));
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

    const base = Ligitabl._predictionBase(parsed);

    return Object.assign(base, {
        alwaysHoverable: true,
        hasLocalChanges: false,

        init() {
            const saved = loadSavedPrediction();
            if (saved) {
                this.teams = saved.map((t, idx) => ({...t, position: idx + 1}));
                this.hasLocalChanges = true;
            } else {
                this.teams = Ligitabl._mapServerPredictions(serverPredictions);
            }
            this.originalTeams = Ligitabl._mapServerPredictions(serverPredictions);
        },

        saveToLocalStorage() {
            try {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(this.teams));
                this.hasLocalChanges = true;
            } catch (e) {
                console.warn("Failed to save prediction:", e);
            }
        },

        clearLocalStorage() {
            try {
                localStorage.removeItem(STORAGE_KEY);
                this.hasLocalChanges = false;
            } catch (e) {
                console.warn("Failed to clear prediction:", e);
            }
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
            this.saveToLocalStorage();
        },

        reset() {
            this.teams = JSON.parse(JSON.stringify(this.originalTeams));
            this.selectedTeam = null;
            this.clearLocalStorage();
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

        // Check if the trigger has the dismiss attribute
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

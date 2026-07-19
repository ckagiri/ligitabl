function abbreviateSprintName(name) {
  return name.replace(/^Sprint\s*/i, "S");
}
function sprintLine(sprint, abbreviate) {
  const name = abbreviate ? abbreviateSprintName(sprint.name) : sprint.name;
  const dates = name + "  •  " + sprint.startDate + " – " + sprint.endDate;
  return abbreviate ? dates : dates + "  •  " + sprint.gwLabel;
}
function renderStructureHierarchy(sprints, quarters, from, to, abbreviate) {
  if (!from || !to) return "";
  const inRange = sprints.filter((s) => s.num >= from.num && s.num <= to.num);
  if (from.num === to.num) {
    return sprintLine(from, abbreviate);
  }
  const quarterCodes = [...new Set(inRange.map((s) => s.quarterCode))];
  if (quarterCodes.length === 1) {
    const qLabel = (quarters.find((q) => q.code === quarterCodes[0]) || {}).name || quarterCodes[0];
    const lines2 = [qLabel];
    inRange.forEach((sprint, i) => {
      lines2.push((i < inRange.length - 1 ? "├── " : "└── ") + sprintLine(sprint, abbreviate));
    });
    return lines2.join("\n");
  }
  const lines = ["Overall"];
  quarterCodes.forEach((qCode, qIdx) => {
    const isLastQ = qIdx === quarterCodes.length - 1;
    const qConnector = isLastQ ? "└── " : "├── ";
    const childPad = isLastQ ? "    " : "│   ";
    const qLabel = (quarters.find((q) => q.code === qCode) || {}).name || qCode;
    const qSprints = inRange.filter((s) => s.quarterCode === qCode);
    lines.push(qConnector + qLabel);
    qSprints.forEach((sprint, sIdx) => {
      const isLastS = sIdx === qSprints.length - 1;
      const sConnector = isLastS ? "└── " : "├── ";
      lines.push(childPad + sConnector + sprintLine(sprint, abbreviate));
    });
  });
  return lines.join("\n");
}
window.contestCreator = function() {
  const d = window.__contestCreateData || {};
  return {
    fromOpen: false,
    toOpen: false,
    fromDropUp: false,
    toDropUp: false,
    windowHelpOpen: false,
    structureOpen: true,
    selectedFrom: null,
    selectedTo: null,
    sprints: d.sprints || [],
    quarters: d.quarters || [],
    init() {
      if (d.fromSprintCode) this.selectedFrom = this.sprints.find((s) => s.code === d.fromSprintCode) || null;
      if (d.toSprintCode) this.selectedTo = this.sprints.find((s) => s.code === d.toSprintCode) || null;
    },
    // Desktop dropdown height is capped at max-h-80 (20rem = 320px); flip it above the
    // trigger when there isn't enough room below but there is above, so it doesn't spill
    // past the fold and overlap the submit buttons/footer.
    shouldDropUp(triggerEl) {
      const rect = triggerEl.getBoundingClientRect();
      const dropdownHeight = 320;
      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;
      return spaceBelow < dropdownHeight && spaceAbove > spaceBelow;
    },
    toggleFrom(triggerEl) {
      this.toOpen = false;
      if (this.fromOpen) {
        this.fromOpen = false;
        return;
      }
      this.fromDropUp = this.shouldDropUp(triggerEl);
      this.fromOpen = true;
    },
    toggleTo(triggerEl) {
      if (!this.selectedFrom) return;
      this.fromOpen = false;
      if (this.toOpen) {
        this.toOpen = false;
        return;
      }
      this.toDropUp = this.shouldDropUp(triggerEl);
      this.toOpen = true;
    },
    isUnavailable(s) {
      return s.status === "PAST" || s.status === "LOCKED";
    },
    getAvailableQuarters() {
      const ids = new Set(this.sprints.filter((s) => !this.isUnavailable(s)).map((s) => s.quarterCode));
      return this.quarters.filter((q) => ids.has(q.code));
    },
    getPastQuarters() {
      const ids = new Set(this.sprints.filter((s) => this.isUnavailable(s)).map((s) => s.quarterCode));
      return this.quarters.filter((q) => ids.has(q.code)).slice().reverse();
    },
    getAvailableSprintsForQuarter(quarterCode) {
      return this.sprints.filter((s) => s.quarterCode === quarterCode && !this.isUnavailable(s));
    },
    getPastSprintsForQuarter(quarterCode) {
      return this.sprints.filter((s) => s.quarterCode === quarterCode && this.isUnavailable(s)).slice().reverse();
    },
    getSprintsForQuarter(quarterCode) {
      return this.sprints.filter((s) => s.quarterCode === quarterCode);
    },
    selectFrom(sprint) {
      this.selectedFrom = sprint;
      this.selectedTo = null;
      this.fromOpen = false;
    },
    selectTo(sprint) {
      this.selectedTo = sprint;
      this.toOpen = false;
    },
    isValidTo(sprint) {
      if (!this.selectedFrom) return false;
      if (sprint.code === this.selectedFrom.code) return true;
      if (sprint.num <= this.selectedFrom.num) return false;
      if (!this.selectedFrom.isQuarterStart) return false;
      if (!sprint.isQuarterEnd) return false;
      return true;
    },
    hasValidToInQuarter(quarterCode) {
      return this.getSprintsForQuarter(quarterCode).some((s) => this.isValidTo(s));
    },
    toSubLabel(sprint) {
      if (!this.selectedFrom) return "";
      if (sprint.code === this.selectedFrom.code) return "Single sprint";
      const q = this.quarters.find((q2) => q2.code === sprint.quarterCode);
      return "End of " + (q ? q.name : "");
    },
    renderHierarchy(abbreviate) {
      return renderStructureHierarchy(this.sprints, this.quarters, this.selectedFrom, this.selectedTo, abbreviate);
    },
    sprintAbbrev(name) {
      return abbreviateSprintName(name);
    }
  };
};
window.contestRenewal = function(data) {
  const d = data || window.__contestRenewalData || {};
  return {
    contestId: d.contestId || "",
    contestName: d.contestName || "",
    sprints: d.sprints || window.__renewalSprints || [],
    quarters: d.quarters || window.__renewalQuarters || [],
    fromCode: d.fromCode || "",
    defaultToCode: d.defaultToCode || "",
    toOptionCodes: d.toOptionCodes || [],
    activeMemberCount: d.activeMemberCount || 0,
    open: false,
    structureOpen: true,
    selectedTo: d.defaultToCode || "",
    init() {
      this.$watch("open", (isOpen) => {
        if (isOpen) {
          this.selectedTo = this.defaultToCode;
          this.structureOpen = true;
        }
      });
      window.addEventListener("pageshow", (e) => {
        if (e.persisted) this.open = false;
      });
    },
    get fromSprint() {
      return this.sprints.find((s) => s.code === this.fromCode) || null;
    },
    get toOptions() {
      return this.sprints.filter((s) => this.toOptionCodes.includes(s.code));
    },
    get selectedToSprint() {
      return this.sprints.find((s) => s.code === this.selectedTo) || null;
    },
    get scoresResetLabel() {
      const from = this.fromSprint;
      return from ? "Scores reset at " + from.name + " start (" + from.startDate + ")" : "";
    },
    toOptionLabel(option) {
      const label = option.name + " • " + option.endDate;
      return option.code === this.defaultToCode ? label + " (default)" : label;
    },
    renderHierarchy(abbreviate) {
      return renderStructureHierarchy(this.sprints, this.quarters, this.fromSprint, this.selectedToSprint, abbreviate);
    },
    sprintAbbrev(name) {
      return abbreviateSprintName(name);
    }
  };
};
window.contestDetail = function() {
  const d = window.__contestDetailData || {};
  return {
    contestId: d.contestId || "",
    segmentTree: d.segmentTree || [],
    activeSegment: d.currentSegment || "overall",
    dropdownOpen: false,
    sheetOpen: false,
    loading: false,
    expanded: {},
    init() {
      window.leagueApp = this;
      this.expandRelevantQuarters();
    },
    // Auto-expand any LIVE quarter, plus whichever quarter contains the currently
    // selected segment, so the active selection is always visible without an extra click.
    expandRelevantQuarters() {
      const root = this.segmentTree[0];
      if (!root) return;
      if (root.nodeType === "QUARTER") {
        const containsActive = root.id === this.activeSegment || root.children && root.children.some((s) => s.id === this.activeSegment);
        if (root.status === "LIVE" || containsActive) this.expanded[root.id] = true;
        return;
      }
      if (!root.children) return;
      root.children.forEach((child) => {
        if (child.status === "LIVE") this.expanded[child.id] = true;
        if (child.id === this.activeSegment) this.expanded[child.id] = true;
        if (child.children && child.children.some((s) => s.id === this.activeSegment)) {
          this.expanded[child.id] = true;
        }
      });
    },
    openMenu() {
      this.expandRelevantQuarters();
      if (window.innerWidth >= 768) {
        this.dropdownOpen = true;
      } else {
        this.sheetOpen = true;
      }
    },
    toggleQuarter(id) {
      this.expanded[id] = !this.expanded[id];
    },
    selectSegment(id) {
      this.dropdownOpen = false;
      this.sheetOpen = false;
      if (this.activeSegment === id) return;
      this.activeSegment = id;
      this.loading = true;
      const params = new URLSearchParams(window.location.search);
      params.set("segment", id);
      params.delete("page");
      const url = "/contests/" + this.contestId + "?" + params.toString();
      htmx.ajax("GET", url, {
        target: "#leaderboard-content",
        swap: "outerHTML"
      });
      history.pushState({}, "", url);
    },
    get currentNode() {
      const find = (nodes) => {
        for (const n of nodes) {
          if (n.id === this.activeSegment) return n;
          if (n.children && n.children.length) {
            const found = find(n.children);
            if (found) return found;
          }
        }
        return null;
      };
      return find(this.segmentTree) || this.segmentTree[0] || null;
    },
    get currentLabel() {
      const node = this.currentNode;
      if (!node) return "Select";
      if (node.dateLabel) return node.label + " · " + node.dateLabel;
      return node.label;
    },
    get currentIcon() {
      const node = this.currentNode;
      if (!node) return "";
      if (node.status === "LIVE") return "🟢";
      if (node.status === "FINISHED") return "🏁";
      if (node.status === "NEXT") return "🎯";
      return "⏳";
    },
    shortcuts() {
      const root = this.segmentTree[0];
      if (!root) return [];
      if (root.nodeType === "SPRINT") return [];
      const preferredNode = (nodes) => {
        return nodes.find((n) => n.status === "LIVE") || nodes.find((n) => n.status === "NEXT") || [...nodes].reverse().find((n) => n.status === "FINISHED") || nodes[0] || null;
      };
      if (root.nodeType === "QUARTER") {
        const s2 = preferredNode(root.children || []);
        return [
          { key: "Q", node: root },
          ...s2 ? [{ key: "S", node: s2 }] : []
        ];
      }
      const q = preferredNode(root.children || []);
      const s = q ? preferredNode(q.children || []) : null;
      return [
        { key: "O", node: root },
        ...q ? [{ key: "Q", node: q }] : [],
        ...s ? [{ key: "S", node: s }] : []
      ];
    },
    getRankText(rank) {
      if (!rank) return "";
      const suffix = ["th", "st", "nd", "rd"];
      const v = rank % 100;
      return rank + (suffix[(v - 20) % 10] || suffix[v] || suffix[0]);
    },
    getStatusText(node) {
      if (node.status === "LIVE" && node.rank) {
        return "Live: " + this.getRankText(node.rank) + " 👀";
      }
      if (node.status === "FINISHED") return "Finished";
      if (node.status === "NEXT" || node.status === "FUTURE") {
        const start = node.dateLabel ? node.dateLabel.split(" – ")[0] : "";
        return start ? "Starts " + start : "Upcoming";
      }
      return "";
    },
    renderMenu() {
      const root = this.segmentTree[0];
      if (!root) return "";
      let html = '<div class="divide-y divide-gray-100">';
      const renderSeason = (node) => {
        const isSelected = node.id === this.activeSegment;
        const highlight = node.status === "LIVE" ? "border-l-4 border-green-500 bg-green-50" : "";
        const statusText = this.getStatusText(node);
        html += `
          <div class="px-4 py-3 cursor-pointer hover:bg-gray-50 ${highlight}"
               onclick="window.leagueApp.selectSegment('${node.id}')">
            <div class="flex items-center gap-2 mb-0.5">
              <span class="text-base">🏆</span>
              <span class="text-sm font-medium text-gray-900">${node.label}</span>
              ${isSelected ? '<span class="text-indigo-600 ml-auto text-sm">✓</span>' : ""}
            </div>
            <div class="text-xs text-gray-500">${node.gwLabel}${node.dateLabel ? " · " + node.dateLabel : ""}</div>
            ${statusText ? `<div class="text-xs mt-0.5 text-green-700 font-medium">${statusText}</div>` : ""}
          </div>`;
      };
      const renderQuarter = (node) => {
        const isExpanded = !!this.expanded[node.id];
        const hasChildren = node.children && node.children.length > 0;
        const highlight = node.status === "LIVE" ? "border-l-4 border-green-500 bg-green-50" : "";
        const statusText = this.getStatusText(node);
        const isSelected = node.id === this.activeSegment;
        const icon = node.status === "LIVE" ? "🟢" : node.status === "FINISHED" ? "🏁" : node.status === "NEXT" ? "🎯" : "⏳";
        const sprintCount = hasChildren ? node.children.length : 0;
        const sprintHint = !isExpanded && hasChildren ? `<span class="ml-auto text-xs text-gray-400 font-normal">${sprintCount} sprint${sprintCount !== 1 ? "s" : ""}</span>` : "";
        const chevron = `<svg class="w-4 h-4 shrink-0 transition-transform ${isExpanded ? "rotate-90" : ""}" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>`;
        let sprintsHtml = "";
        if (isExpanded && hasChildren) {
          sprintsHtml = '<div class="mt-1.5 space-y-0.5">';
          const sortedSprints = (() => {
            const sprints = [...node.children];
            if (node.status === "LIVE") {
              return [
                ...sprints.filter((s) => s.status === "LIVE"),
                ...sprints.filter((s) => s.status === "NEXT"),
                ...sprints.filter((s) => s.status === "FUTURE"),
                ...sprints.filter((s) => s.status === "FINISHED").reverse()
              ];
            }
            return [...sprints].reverse();
          })();
          sortedSprints.forEach((sprint) => {
            const sprintSelected = sprint.id === this.activeSegment;
            const sprintHighlight = sprint.status === "LIVE" ? "bg-green-50" : "";
            const clickable = sprint.status === "LIVE" || sprint.status === "FINISHED" || sprint.status === "NEXT";
            const sprintStatus = this.getStatusText(sprint);
            const sprintIcon = sprint.status === "LIVE" ? "🟢" : sprint.status === "NEXT" ? "🎯" : "🏁";
            sprintsHtml += `
              <div class="border-l-2 border-gray-200 ml-4 pl-3 py-1.5 rounded ${sprintHighlight} ${clickable ? "cursor-pointer hover:bg-gray-50" : "opacity-50"}"
                   ${clickable ? `onclick="event.stopPropagation(); window.leagueApp.selectSegment('${sprint.id}')"` : ""}>
                <div class="flex items-center gap-1.5 mb-0.5">
                  <span class="text-sm">${sprintIcon}</span>
                  <span class="text-sm font-medium text-gray-800">${sprint.label}</span>
                  ${sprintSelected ? '<span class="text-indigo-600 ml-auto text-sm">✓</span>' : ""}
                </div>
                <div class="text-xs text-gray-400">${sprint.gwLabel}${sprint.dateLabel ? " · " + sprint.dateLabel : ""}</div>
                ${sprintStatus ? `<div class="text-xs mt-0.5 ${sprint.status === "LIVE" ? "text-green-700 font-medium" : "text-gray-400"}">${sprintStatus}</div>` : ""}
              </div>`;
          });
          sprintsHtml += "</div>";
        }
        html += `
          <div class="${highlight}">
            <div class="px-4 pt-3 ${isExpanded ? "pb-2" : "pb-0"}">
              <div class="flex items-center gap-2 mb-0.5 cursor-pointer select-none"
                   onclick="event.stopPropagation(); window.leagueApp.toggleQuarter('${node.id}')">
                ${chevron}
                <span class="text-base">${icon}</span>
                <span class="text-sm font-medium text-gray-900">${node.label}</span>
                ${isSelected && !sprintHint ? '<span class="text-indigo-600 ml-auto text-sm">✓</span>' : ""}
                ${sprintHint}
              </div>
              <div class="pb-2.5 cursor-pointer rounded hover:bg-gray-50"
                   onclick="window.leagueApp.selectSegment('${node.id}')">
                <div class="text-xs text-gray-500">${node.gwLabel}${node.dateLabel ? " · " + node.dateLabel : ""}</div>
                ${statusText ? `<div class="text-xs mt-0.5 ${node.status === "LIVE" ? "text-green-700 font-medium" : "text-gray-400"}">${statusText}</div>` : ""}
              </div>
              ${sprintsHtml}
            </div>
          </div>`;
      };
      if (root.nodeType === "SPRINT") {
        html += `<div class="px-3 py-2 text-xs text-gray-500">${root.label}</div>`;
      } else if (root.nodeType === "QUARTER") {
        renderQuarter(root);
      } else {
        renderSeason(root);
        if (root.children && root.children.length > 0) {
          const active = root.children.filter((q) => q.status === "LIVE" || q.status === "NEXT");
          const upcoming = root.children.filter((q) => q.status === "FUTURE");
          const past = root.children.filter((q) => q.status === "FINISHED").reverse();
          [...active, ...upcoming].forEach((q) => renderQuarter(q));
          if (past.length > 0) {
            html += `<div class="px-4 py-2 bg-gray-50 border-t border-gray-200">
              <span class="text-xs font-semibold text-gray-400 uppercase tracking-wide">Past</span>
            </div>`;
            past.forEach((q) => renderQuarter(q));
          }
        }
      }
      html += "</div>";
      return html;
    }
  };
};
document.body.addEventListener("htmx:configRequest", (event) => {
  event.detail.headers["X-CSRF-TOKEN"] = document.querySelector('meta[name="_csrf"]')?.content;
});
document.body.addEventListener("htmx:afterSwap", (event) => {
  if (event.detail.target.id === "main-content") {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
});
window.Ligitabl = window.Ligitabl || {};
window.Ligitabl._MAX_INITIAL_SWAPS = 5;
window.Ligitabl._MAX_OPENING_SWAPS = 2;
window.Ligitabl._parseJSON = function(raw, fallback) {
  try {
    return JSON.parse(raw);
  } catch (e) {
    return fallback;
  }
};
window.Ligitabl._parseDataAttributes = function(el) {
  const p = Ligitabl._parseJSON;
  return {
    predictions: p(el?.dataset?.predictions, []),
    currentStandings: p(el?.dataset?.currentStandings, {}),
    fixtures: p(el?.dataset?.fixtures, {}),
    currentPoints: p(el?.dataset?.currentPoints, {}),
    currentGoalDifference: p(el?.dataset?.currentGoalDifference, {}),
    formData: p(el?.dataset?.form, {}) || {}
  };
};
window.Ligitabl._PREFS_KEY = "ligitabl.prefs";
window.Ligitabl._loadPrefs = function(key) {
  try {
    const saved = localStorage.getItem(key || Ligitabl._PREFS_KEY);
    if (saved) return JSON.parse(saved);
  } catch (e) {
    console.warn("Failed to load prefs:", e);
  }
  return null;
};
window.Ligitabl._savePrefs = function(prefs, key) {
  try {
    localStorage.setItem(key || Ligitabl._PREFS_KEY, JSON.stringify(prefs));
  } catch (e) {
    console.warn("Failed to save prefs:", e);
  }
};
window.Ligitabl._predictionBase = function(parsed, userId, roundId) {
  const prefsKey = userId ? "ligitabl.prefs." + userId : "ligitabl.prefs.guest";
  const savedPrefs = Ligitabl._loadPrefs(prefsKey);
  return {
    _prefsKey: prefsKey,
    roundId,
    teams: [],
    originalTeams: [],
    selectedTeam: null,
    swapStack: [],
    undoing: false,
    alwaysHoverable: false,
    isInitialPrediction: false,
    showStandings: savedPrefs ? savedPrefs.showStandings ?? true : true,
    showFixtures: savedPrefs ? savedPrefs.showFixtures ?? false : false,
    showPoints: savedPrefs ? savedPrefs.showPoints ?? false : false,
    showGD: savedPrefs ? savedPrefs.showGD ?? false : false,
    showForm: savedPrefs ? savedPrefs.showForm ?? false : false,
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
      return entry.wasHome ? teamCode + " " + entry.goalsFor + "–" + entry.goalsAgainst + " " + entry.opponentCode : entry.opponentCode + " " + entry.goalsAgainst + "–" + entry.goalsFor + " " + teamCode;
    },
    getCurrentPoints(teamCode) {
      return this.currentPoints[teamCode] || "-";
    },
    getCurrentGD(teamCode) {
      const gd = this.currentGoalDifference[teamCode];
      if (gd === void 0 || gd === null) return "-";
      return gd > 0 ? "+" + gd : gd;
    },
    getGDDirection(teamCode) {
      const gd = this.currentGoalDifference[teamCode];
      if (gd === void 0 || gd === null) return null;
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
      if (fixtures.some((f) => f.status === "LIVE")) return "bg-blue-50 text-blue-700";
      if (fixtures.some((f) => f.result === "WIN")) return "bg-red-50 text-red-700";
      if (fixtures.some((f) => f.result === "LOSS")) return "bg-green-50 text-green-700";
      if (fixtures.some((f) => f.result === "DRAW")) return "bg-yellow-100 text-yellow-700";
      if (fixtures.some((f) => f.status === "POSTPONED")) return "bg-violet-50 text-violet-700";
      return "bg-gray-200 text-gray-700";
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
      const visited = /* @__PURE__ */ new Set();
      let swapCount = 0;
      for (const team of this.teams) {
        if (visited.has(team.code) || !this.isDirty(team.code)) continue;
        let cycleLength = 0;
        let currentCode = team.code;
        while (!visited.has(currentCode)) {
          visited.add(currentCode);
          cycleLength++;
          const originalPos = this.originalTeams.find(
            (t) => t.code === currentCode
          )?.position;
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
      return this.teams.filter((t) => this.isDirty(t.code)).map((t) => {
        const original = this.originalTeams.find((o) => o.code === t.code);
        const change = original.position - t.position;
        return {
          name: t.name,
          code: t.code,
          from: original.position,
          to: t.position,
          direction: change > 0 ? "up" : "down",
          amount: Math.abs(change)
        };
      }).sort((a, b) => a.from - b.from);
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
      return team ? team.shortName || team.name : null;
    },
    pushSwap(codeA, codeB) {
      const top = this.swapStack[this.swapStack.length - 1];
      if (top && top.a === codeB && top.b === codeA) {
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
      this.teams.forEach((team, idx) => team.position = idx + 1);
      if (onSwapped) onSwapped();
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
          swapStack: this.swapStack
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
    }
  };
};
window.Ligitabl._mapServerPredictions = function(predictions) {
  return (Array.isArray(predictions) ? predictions : []).map((p) => ({
    position: p.position,
    code: p.teamCode,
    name: p.teamName,
    shortName: p.teamShortName,
    crestUrl: p.crestUrl,
    originalPosition: p.position
  }));
};
window.Ligitabl.predictionPage = function(el) {
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
  const userId = el?.dataset?.userId || "unknown";
  const roundId = el?.dataset?.roundId || "unknown";
  const GUEST_STORAGE_KEY = "ligitabl.guestPrediction";
  const AUTH_STORAGE_KEY = "ligitabl.prediction." + userId;
  function _validateSaved(saved) {
    if (!saved) return false;
    if (saved.roundId !== roundId) return false;
    const serverCodes = new Set(predictions.map((p) => p.teamCode));
    const teams = _extractTeams(saved);
    const savedCodes = new Set(teams.map((p) => p.code));
    return serverCodes.size === savedCodes.size && [...serverCodes].every((c) => savedCodes.has(c));
  }
  function loadGuestPrediction() {
    try {
      const saved = localStorage.getItem(GUEST_STORAGE_KEY);
      if (saved) {
        const parsed2 = JSON.parse(saved);
        if (_validateSaved(parsed2)) return parsed2;
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
        const parsed2 = JSON.parse(saved);
        if (_validateSaved(parsed2)) return parsed2;
      }
    } catch (e) {
      console.warn("Failed to load auth prediction:", e);
    }
    return null;
  }
  function _extractTeams(saved) {
    return Array.isArray(saved) ? saved : saved?.teams ?? [];
  }
  function _extractSwapStack(saved) {
    return Array.isArray(saved) ? [] : saved?.swapStack ?? [];
  }
  const serverDataByCode = {};
  (Array.isArray(predictions) ? predictions : []).forEach((p) => {
    serverDataByCode[p.teamCode] = {
      position: p.position,
      code: p.teamCode,
      name: p.teamName,
      crestUrl: p.crestUrl
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
        const authPrediction = loadAuthPrediction();
        if (authPrediction) {
          this.teams = _extractTeams(authPrediction).map((t, idx) => ({ ...t, position: idx + 1 }));
          this.swapStack = _extractSwapStack(authPrediction);
          this._clearStorage(GUEST_STORAGE_KEY);
        }
        if (this.teams.length === 0 && !isOpeningRound) {
          const guestPrediction = loadGuestPrediction();
          if (guestPrediction) {
            this.teams = _extractTeams(guestPrediction).map((t, idx) => {
              const serverData = serverDataByCode[t.code];
              return {
                position: idx + 1,
                code: t.code,
                name: t.name,
                crestUrl: t.crestUrl,
                originalPosition: serverData ? serverData.position : idx + 1
              };
            });
            this.swapStack = _extractSwapStack(guestPrediction);
            this.importedFromGuest = true;
          }
        }
      } else {
        const authPrediction = loadAuthPrediction();
        if (authPrediction) {
          this.teams = _extractTeams(authPrediction).map((t, idx) => ({ ...t, position: idx + 1 }));
          this.swapStack = _extractSwapStack(authPrediction);
        }
      }
      if (this.teams.length === 0) {
        this.teams = Ligitabl._mapServerPredictions(predictions);
      }
      this.originalTeams = Ligitabl._mapServerPredictions(predictions);
      const savePrefs = () => Ligitabl._savePrefs({
        showStandings: this.showStandings,
        showFixtures: this.showFixtures,
        showPoints: this.showPoints,
        showGD: this.showGD,
        showForm: this.showForm
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
        pairs: this.inferSwapPairs(changed)
      };
    },
    inferSwapPairs(changedTeams) {
      const pairs = [];
      const processed = /* @__PURE__ */ new Set();
      for (const team of changedTeams) {
        if (processed.has(team.code)) continue;
        const partner = changedTeams.find(
          (t) => !processed.has(t.code) && t.to === team.from && t.from === team.to
        );
        if (partner) {
          pairs.push({
            team1: team.name,
            team2: partner.name,
            pos1: team.from,
            pos2: partner.from
          });
          processed.add(team.code);
          processed.add(partner.code);
        } else {
          pairs.push({
            team1: team.name,
            team2: null,
            pos1: team.from,
            pos2: team.to,
            isComplex: true
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
        window.dispatchEvent(new CustomEvent("guest-storage-cleared"));
      }
    },
    undoLastSwap() {
      if (!this.canUndo() || this.undoing) return;
      this.undoing = true;
      const last = this.swapStack.pop();
      setTimeout(() => {
        this._swapTeamsDirect(last.b, last.a, () => {
          this._saveToStorage(AUTH_STORAGE_KEY);
        });
        setTimeout(() => {
          this.undoing = false;
        }, 200);
      }, 200);
    },
    submitChanges() {
      this.isSaving = true;
      const toast = document.getElementById("saving-toast");
      if (toast) toast.classList.remove("hidden");
      let url, body;
      const _working = this.originalTeams.map((t) => ({ ...t }));
      const _targetPosition = Object.fromEntries(this.teams.map((t) => [t.code, t.position]));
      const _derivedSwaps = [];
      for (const t of _working) {
        const tgt = _targetPosition[t.code];
        if (t.position === tgt) continue;
        const partner = _working.find((w) => w.position === tgt);
        if (!partner) continue;
        _derivedSwaps.push({ teamACode: t.code, teamBCode: partner.code });
        const tmp = t.position;
        t.position = partner.position;
        partner.position = tmp;
      }
      if (this.isInitialPrediction || this.isPreSeasonRegistration) {
        url = "/seasonprediction";
        const _next = new URLSearchParams(window.location.search).get("next");
        if (_next) url += "?next=" + encodeURIComponent(_next);
        body = { swaps: _derivedSwaps };
      } else if (this.isOpeningRound) {
        url = "/seasonprediction/opening-swaps";
        body = { swaps: _derivedSwaps };
      } else {
        const entry = this.swapStack[0];
        url = "/seasonprediction/swap";
        body = { teamACode: entry.a, teamBCode: entry.b };
      }
      fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      }).then((response) => response.json()).then((data) => {
        if (data.success) {
          this._clearStorage(AUTH_STORAGE_KEY);
          if (this.importedFromGuest || (this.isInitialPrediction || this.isPreSeasonRegistration) && !this.isOpeningRound) {
            this._clearStorage(GUEST_STORAGE_KEY);
          }
          if (data.nextUrl) {
            window.location.href = data.nextUrl;
            return;
          }
          setTimeout(() => {
            window.scrollTo({ top: 0, behavior: "smooth" });
          }, 300);
          setTimeout(() => {
            window.location.reload();
          }, 800);
        } else {
          this.isSaving = false;
          if (toast) toast.classList.add("hidden");
          this.errorMessage = data.message || "Something went wrong";
        }
      }).catch((error) => {
        console.error("Error:", error);
        this.isSaving = false;
        if (toast) toast.classList.add("hidden");
        this.errorMessage = "Failed to submit. Please check your connection and try again.";
      });
    }
  });
};
document.body.addEventListener("htmx:afterSwap", (event) => {
  if (!window.Alpine || !event.detail?.target) return;
  const target = event.detail.target;
  if (!target.querySelector || !target.querySelector("[x-data]")) return;
  window.Alpine.initTree(target);
});
document.body.addEventListener("htmx:beforeRequest", function(e) {
  if (e.detail?.target?.id === "user-detail-modal") {
    const spinner = document.getElementById("modal-loading");
    if (spinner) spinner.classList.remove("hidden");
  }
});
document.body.addEventListener("htmx:afterSwap", function(e) {
  if (e.detail?.target?.id === "user-detail-modal") {
    const spinner = document.getElementById("modal-loading");
    if (spinner) spinner.classList.add("hidden");
  }
});
window.Ligitabl.formatTimestamps = function(root) {
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
      el.textContent = d.toLocaleString(void 0, {
        dateStyle: "medium",
        timeStyle: "short"
      });
    } catch (e) {
    }
  });
};
document.addEventListener("DOMContentLoaded", () => Ligitabl.formatTimestamps());
document.body.addEventListener("htmx:afterSettle", () => Ligitabl.formatTimestamps());
window.Ligitabl.guestPredictionPage = function(el) {
  const STORAGE_KEY = "ligitabl.guestPrediction";
  const parsed = Ligitabl._parseDataAttributes(el);
  const serverPredictions = parsed.predictions;
  const roundId = el?.dataset?.roundId || "unknown";
  function loadSavedPrediction() {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const p = JSON.parse(saved);
        if (p.roundId !== roundId) return null;
        const teams = Array.isArray(p) ? p : p?.teams ?? [];
        const serverCodes = new Set(serverPredictions.map((s) => s.teamCode));
        const savedCodes = new Set(teams.map((s) => s.code));
        if (serverCodes.size === savedCodes.size && [...serverCodes].every((c) => savedCodes.has(c))) {
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
        const teams = Array.isArray(saved) ? saved : saved?.teams ?? [];
        this.teams = teams.map((t, idx) => ({ ...t, position: idx + 1 }));
        this.swapStack = Array.isArray(saved) ? [] : saved?.swapStack ?? [];
      } else {
        this.teams = Ligitabl._mapServerPredictions(serverPredictions);
      }
      this.originalTeams = Ligitabl._mapServerPredictions(serverPredictions);
      const savePrefs = () => Ligitabl._savePrefs({
        showStandings: this.showStandings,
        showFixtures: this.showFixtures,
        showPoints: this.showPoints,
        showGD: this.showGD,
        showForm: this.showForm
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
        });
        setTimeout(() => {
          this.undoing = false;
        }, 200);
      }, 200);
    }
  });
};
window.Ligitabl.publicPredictionPage = function(el) {
  const parsed = Ligitabl._parseDataAttributes(el);
  const roundId = el?.dataset?.roundId || "unknown";
  const base = Ligitabl._predictionBase(parsed, null, roundId);
  return Object.assign(base, {
    init() {
      this.teams = Ligitabl._mapServerPredictions(parsed.predictions);
      this.originalTeams = Ligitabl._mapServerPredictions(parsed.predictions);
      const savePrefs = () => Ligitabl._savePrefs({
        showStandings: this.showStandings,
        showFixtures: this.showFixtures,
        showPoints: this.showPoints,
        showGD: this.showGD,
        showForm: this.showForm
      }, this._prefsKey);
      this.$watch("showStandings", savePrefs);
      this.$watch("showFixtures", savePrefs);
      this.$watch("showPoints", savePrefs);
      this.$watch("showGD", savePrefs);
      this.$watch("showForm", savePrefs);
    }
  });
};
(function() {
  function dismissResultsBanner(roundNumber) {
    var csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    var headers = { "Content-Type": "application/json" };
    if (csrfToken) {
      headers["X-CSRF-TOKEN"] = csrfToken;
    }
    fetch(
      "/my-table/latest-result-banner/dismiss?round=" + roundNumber,
      { method: "POST", headers }
    ).catch(function(err) {
      console.warn("Failed to dismiss results banner:", err);
    });
    var host = document.getElementById("results-banner");
    if (host) host.remove();
  }
  document.addEventListener("click", function(event) {
    var trigger = event.target.closest ? event.target.closest('[data-dismiss-results-banner="true"]') : null;
    if (!trigger) return;
    var banner = document.querySelector("[data-result-round]");
    var roundNumber = banner ? banner.getAttribute("data-result-round") : null;
    if (roundNumber) {
      dismissResultsBanner(roundNumber);
    }
  }, true);
  document.body.addEventListener("htmx:beforeRequest", function(event) {
    var trigger = event.detail.elt;
    if (!trigger) return;
    var hasDismissAttr = trigger.getAttribute("data-dismiss-results-banner") === "true";
    if (!hasDismissAttr) return;
    var banner = document.querySelector("[data-result-round]");
    var roundNumber = banner ? banner.getAttribute("data-result-round") : null;
    if (roundNumber) {
      dismissResultsBanner(roundNumber);
    }
  });
})();
(function() {
  const BAR_ID = "nav-loading-bar";
  let timer = null;
  function getBar() {
    let bar = document.getElementById(BAR_ID);
    if (!bar) {
      bar = document.createElement("div");
      bar.id = BAR_ID;
      const isMobile = window.matchMedia("(max-width: 767px)").matches;
      bar.style.cssText = [
        "position:fixed",
        "top:0",
        "left:0",
        "width:0%",
        "height:" + (isMobile ? "5px" : "3px"),
        "background:#6366f1",
        "z-index:9999",
        "transition:width 0.3s ease,opacity 0.4s ease",
        "opacity:0",
        "pointer-events:none"
      ].join(";");
      document.body.appendChild(bar);
    }
    return bar;
  }
  function start() {
    const bar = getBar();
    bar.style.transition = "none";
    bar.style.width = "0%";
    bar.style.opacity = "1";
    bar.offsetWidth;
    bar.style.transition = "width 0.3s ease, opacity 0.4s ease";
    bar.style.width = "70%";
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      bar.style.width = "90%";
    }, 1e3);
  }
  function finish() {
    if (timer) clearTimeout(timer);
    const bar = getBar();
    bar.style.width = "100%";
    setTimeout(() => {
      bar.style.opacity = "0";
    }, 300);
    setTimeout(() => {
      bar.style.transition = "none";
      bar.style.width = "0%";
    }, 700);
  }
  const NAV_TARGETS = ["prediction-page", "matches-page", "standings-page"];
  const PAGINATION_TARGETS = ["leaderboard-content"];
  document.body.addEventListener("htmx:beforeRequest", function(e) {
    const targetId = e.detail?.target?.id;
    if (NAV_TARGETS.includes(targetId) || PAGINATION_TARGETS.includes(targetId)) {
      start();
    }
  });
  document.body.addEventListener("htmx:afterSwap", function(e) {
    const targetId = e.detail?.target?.id;
    if (NAV_TARGETS.includes(targetId) || PAGINATION_TARGETS.includes(targetId)) {
      finish();
    }
    if (PAGINATION_TARGETS.includes(targetId)) {
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  });
  document.body.addEventListener("htmx:responseError", function() {
    finish();
  });
  window.flashNavLoadingBar = start;
})();

window.contestCreator = function () {
  const d = window.__contestCreateData || {};
  return {
    fromOpen: false,
    toOpen: false,
    fromMaxH: '18rem',
    windowHelpOpen: false,
    selectedFrom: null,
    selectedTo: null,
    sprints: d.sprints || [],
    quarters: d.quarters || [],

    init() {
      if (d.fromSprintCode) this.selectedFrom = this.sprints.find(s => s.code === d.fromSprintCode) || null;
      if (d.toSprintCode)   this.selectedTo   = this.sprints.find(s => s.code === d.toSprintCode)   || null;
    },

    isUnavailable(s) { return s.status === 'PAST' || s.status === 'LOCKED'; },

    getAvailableQuarters() {
      const ids = new Set(this.sprints.filter(s => !this.isUnavailable(s)).map(s => s.quarterCode));
      return this.quarters.filter(q => ids.has(q.code));
    },

    getPastQuarters() {
      const ids = new Set(this.sprints.filter(s => this.isUnavailable(s)).map(s => s.quarterCode));
      return this.quarters.filter(q => ids.has(q.code)).slice().reverse();
    },

    getAvailableSprintsForQuarter(quarterCode) {
      return this.sprints.filter(s => s.quarterCode === quarterCode && !this.isUnavailable(s));
    },

    getPastSprintsForQuarter(quarterCode) {
      return this.sprints.filter(s => s.quarterCode === quarterCode && this.isUnavailable(s)).slice().reverse();
    },

    getSprintsForQuarter(quarterCode) {
      return this.sprints.filter(s => s.quarterCode === quarterCode);
    },

    selectFrom(sprint) {
      this.selectedFrom = sprint;
      this.selectedTo   = null;
      this.fromOpen     = false;
    },

    selectTo(sprint) {
      this.selectedTo = sprint;
      this.toOpen     = false;
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
      return this.getSprintsForQuarter(quarterCode).some(s => this.isValidTo(s));
    },

    toSubLabel(sprint) {
      if (!this.selectedFrom) return '';
      if (sprint.code === this.selectedFrom.code) return 'Single sprint';
      const q = this.quarters.find(q => q.code === sprint.quarterCode);
      return 'End of ' + (q ? q.name : '');
    },

    sprintLine(sprint) {
      return sprint.name + '  •  ' + sprint.startDate + ' – ' + sprint.endDate + '  •  ' + sprint.gwLabel;
    },

    renderHierarchy() {
      if (!this.selectedFrom || !this.selectedTo) return '';
      const fromNum = this.selectedFrom.num;
      const toNum   = this.selectedTo.num;
      const inRange = this.sprints.filter(s => s.num >= fromNum && s.num <= toNum);

      if (fromNum === toNum) {
        return this.sprintLine(this.selectedFrom);
      }

      const quarterCodes = [...new Set(inRange.map(s => s.quarterCode))];

      if (quarterCodes.length === 1) {
        const qLabel = (this.quarters.find(q => q.code === quarterCodes[0]) || {}).name || quarterCodes[0];
        const lines  = [qLabel];
        inRange.forEach((sprint, i) => {
          lines.push((i < inRange.length - 1 ? '├── ' : '└── ') + this.sprintLine(sprint));
        });
        return lines.join('\n');
      }

      const lines = ['Overall'];
      quarterCodes.forEach((qCode, qIdx) => {
        const isLastQ    = qIdx === quarterCodes.length - 1;
        const qConnector = isLastQ ? '└── ' : '├── ';
        const childPad   = isLastQ ? '    ' : '│   ';
        const qLabel     = (this.quarters.find(q => q.code === qCode) || {}).name || qCode;
        const qSprints   = inRange.filter(s => s.quarterCode === qCode);
        lines.push(qConnector + qLabel);
        qSprints.forEach((sprint, sIdx) => {
          const isLastS    = sIdx === qSprints.length - 1;
          const sConnector = isLastS ? '└── ' : '├── ';
          lines.push(childPad + sConnector + this.sprintLine(sprint));
        });
      });
      return lines.join('\n');
    }
  };
};

window.contestDetail = function () {
  const d = window.__contestDetailData || {};
  return {
    contestId:    d.contestId || '',
    segmentTree:  d.segmentTree || [],
    activeSegment: d.currentSegment || 'overall',
    dropdownOpen: false,
    sheetOpen:    false,
    expanded:     {},

    init() {
      window.leagueApp = this;
      // auto-expand any LIVE quarter nodes
      const root = this.segmentTree[0];
      if (root && root.children) {
        root.children.forEach(child => {
          if (child.status === 'LIVE') this.expanded[child.id] = true;
        });
      }
    },

    openMenu() {
      const root = this.segmentTree[0];
      if (root && root.children) {
        root.children.forEach(child => {
          if (child.status === 'LIVE') this.expanded[child.id] = true;
        });
      }
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
      this.sheetOpen    = false;
      if (this.activeSegment === id) return;
      this.activeSegment = id;
      htmx.ajax('GET', '/contests/' + this.contestId + '?segment=' + id, {
        target: '#leaderboard-content',
        swap:   'outerHTML'
      });
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
      if (!node) return 'Select';
      if (node.dateLabel) return node.label + ' · ' + node.dateLabel;
      return node.label;
    },

    get currentIcon() {
      const node = this.currentNode;
      if (!node) return '';
      if (node.status === 'LIVE')     return '🟢';
      if (node.status === 'FINISHED') return '🏁';
      if (node.status === 'NEXT')     return '🎯';
      return '⏳';
    },

    shortcuts() {
      const root = this.segmentTree[0];
      if (!root) return [];
      if (root.nodeType === 'SPRINT') return [];

      const preferredNode = (nodes) => {
        return nodes.find(n => n.status === 'LIVE')
            || nodes.find(n => n.status === 'NEXT')
            || [...nodes].reverse().find(n => n.status === 'FINISHED')
            || nodes[0]
            || null;
      };

      if (root.nodeType === 'QUARTER') {
        const s = preferredNode(root.children || []);
        return [
          { key: 'Q', node: root },
          ...(s ? [{ key: 'S', node: s }] : [])
        ];
      }

      // OVERALL
      const q = preferredNode(root.children || []);
      const s = q ? preferredNode(q.children || []) : null;
      return [
        { key: 'O', node: root },
        ...(q ? [{ key: 'Q', node: q }] : []),
        ...(s ? [{ key: 'S', node: s }] : [])
      ];
    },

    getRankText(rank) {
      if (!rank) return '';
      const suffix = ['th', 'st', 'nd', 'rd'];
      const v = rank % 100;
      return rank + (suffix[(v - 20) % 10] || suffix[v] || suffix[0]);
    },

    getStatusText(node) {
      if (node.status === 'LIVE' && node.rank) {
        return 'Live: ' + this.getRankText(node.rank) +  ' 👀';
      }
      if (node.status === 'FINISHED') return 'Finished';
      if (node.status === 'NEXT' || node.status === 'FUTURE') {
        const start = node.dateLabel ? node.dateLabel.split(' – ')[0] : '';
        return start ? 'Starts ' + start : 'Upcoming';
      }
      return '';
    },

    renderMenu() {
      const root = this.segmentTree[0];
      if (!root) return '';
      let html = '<div class="divide-y divide-gray-100">';

      const renderSeason = (node) => {
        const isSelected = node.id === this.activeSegment;
        const highlight  = node.status === 'LIVE' ? 'border-l-4 border-green-500 bg-green-50' : '';
        const statusText = this.getStatusText(node);
        html += `
          <div class="px-4 py-3 cursor-pointer hover:bg-gray-50 ${highlight}"
               onclick="window.leagueApp.selectSegment('${node.id}')">
            <div class="flex items-center gap-2 mb-0.5">
              <span class="text-base">🏆</span>
              <span class="text-sm font-medium text-gray-900">${node.label}</span>
              ${isSelected ? '<span class="text-indigo-600 ml-auto text-sm">✓</span>' : ''}
            </div>
            <div class="text-xs text-gray-500">${node.gwLabel}${node.dateLabel ? ' · ' + node.dateLabel : ''}</div>
            ${statusText ? `<div class="text-xs mt-0.5 text-green-700 font-medium">${statusText}</div>` : ''}
          </div>`;
      };

      const renderQuarter = (node) => {
        const isExpanded  = !!this.expanded[node.id];
        const hasChildren = node.children && node.children.length > 0;
        const highlight   = node.status === 'LIVE' ? 'border-l-4 border-green-500 bg-green-50' : '';
        const statusText  = this.getStatusText(node);
        const isSelected  = node.id === this.activeSegment;
        const icon        = node.status === 'LIVE' ? '🟢' : node.status === 'FINISHED' ? '🏁' : node.status === 'NEXT' ? '🎯' : '⏳';
        const sprintCount = hasChildren ? node.children.length : 0;
        const sprintHint  = (!isExpanded && hasChildren)
          ? `<span class="ml-auto text-xs text-gray-400 font-normal">${sprintCount} sprint${sprintCount !== 1 ? 's' : ''}</span>` : '';
        const chevron = `<svg class="w-4 h-4 shrink-0 transition-transform ${isExpanded ? 'rotate-90' : ''}" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>`;

        let sprintsHtml = '';
        if (isExpanded && hasChildren) {
          sprintsHtml = '<div class="mt-1.5 space-y-0.5">';
          const sortedSprints = (() => {
            const sprints = [...node.children];
            if (node.status === 'LIVE') {
              return [
                ...sprints.filter(s => s.status === 'LIVE'),
                ...sprints.filter(s => s.status === 'NEXT'),
                ...sprints.filter(s => s.status === 'FUTURE'),
                ...sprints.filter(s => s.status === 'FINISHED').reverse(),
              ];
            }
            return [...sprints].reverse();
          })();
          sortedSprints.forEach(sprint => {
            const sprintSelected  = sprint.id === this.activeSegment;
            const sprintHighlight = sprint.status === 'LIVE' ? 'bg-green-50' : '';
            const clickable       = sprint.status === 'LIVE' || sprint.status === 'FINISHED' || sprint.status === 'NEXT';
            const sprintStatus    = this.getStatusText(sprint);
            const sprintIcon      = sprint.status === 'LIVE' ? '🟢' : sprint.status === 'NEXT' ? '🎯' : '🏁';
            sprintsHtml += `
              <div class="border-l-2 border-gray-200 ml-4 pl-3 py-1.5 rounded ${sprintHighlight} ${clickable ? 'cursor-pointer hover:bg-gray-50' : 'opacity-50'}"
                   ${clickable ? `onclick="event.stopPropagation(); window.leagueApp.selectSegment('${sprint.id}')"` : ''}>
                <div class="flex items-center gap-1.5 mb-0.5">
                  <span class="text-sm">${sprintIcon}</span>
                  <span class="text-sm font-medium text-gray-800">${sprint.label}</span>
                  ${sprintSelected ? '<span class="text-indigo-600 ml-auto text-sm">✓</span>' : ''}
                </div>
                <div class="text-xs text-gray-400">${sprint.gwLabel}${sprint.dateLabel ? ' · ' + sprint.dateLabel : ''}</div>
                ${sprintStatus ? `<div class="text-xs mt-0.5 ${sprint.status === 'LIVE' ? 'text-green-700 font-medium' : 'text-gray-400'}">${sprintStatus}</div>` : ''}
              </div>`;
          });
          sprintsHtml += '</div>';
        }

        html += `
          <div class="${highlight}">
            <div class="px-4 pt-3 ${isExpanded ? 'pb-2' : 'pb-0'}">
              <div class="flex items-center gap-2 mb-0.5 cursor-pointer select-none"
                   onclick="event.stopPropagation(); window.leagueApp.toggleQuarter('${node.id}')">
                ${chevron}
                <span class="text-base">${icon}</span>
                <span class="text-sm font-medium text-gray-900">${node.label}</span>
                ${isSelected && !sprintHint ? '<span class="text-indigo-600 ml-auto text-sm">✓</span>' : ''}
                ${sprintHint}
              </div>
              <div class="pb-2.5 cursor-pointer rounded hover:bg-gray-50"
                   onclick="window.leagueApp.selectSegment('${node.id}')">
                <div class="text-xs text-gray-500">${node.gwLabel}${node.dateLabel ? ' · ' + node.dateLabel : ''}</div>
                ${statusText ? `<div class="text-xs mt-0.5 ${node.status === 'LIVE' ? 'text-green-700 font-medium' : 'text-gray-400'}">${statusText}</div>` : ''}
              </div>
              ${sprintsHtml}
            </div>
          </div>`;
      };

      if (root.nodeType === 'SPRINT') {
        // Single sprint — nothing to navigate, dropdown is empty
        html += `<div class="px-3 py-2 text-xs text-gray-500">${root.label}</div>`;
      } else if (root.nodeType === 'QUARTER') {
        renderQuarter(root);
      } else {
        // OVERALL
        renderSeason(root);
        if (root.children && root.children.length > 0) {
          const active   = root.children.filter(q => q.status === 'LIVE' || q.status === 'NEXT');
          const upcoming = root.children.filter(q => q.status === 'FUTURE');
          const past     = root.children.filter(q => q.status === 'FINISHED').reverse();
          [...active, ...upcoming].forEach(q => renderQuarter(q));
          if (past.length > 0) {
            html += `<div class="px-4 py-2 bg-gray-50 border-t border-gray-200">
              <span class="text-xs font-semibold text-gray-400 uppercase tracking-wide">Past</span>
            </div>`;
            past.forEach(q => renderQuarter(q));
          }
        }
      }

      html += '</div>';
      return html;
    }
  };
};

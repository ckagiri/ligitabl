window.contestCreator = function () {
  const d = window.__contestCreateData || {};
  return {
    fromOpen: false,
    toOpen: false,
    selectedFrom: null,
    selectedTo: null,
    sprints: d.sprints || [],
    quarters: d.quarters || [],

    init() {
      if (d.fromSprintCode) this.selectedFrom = this.sprints.find(s => s.code === d.fromSprintCode) || null;
      if (d.toSprintCode)   this.selectedTo   = this.sprints.find(s => s.code === d.toSprintCode)   || null;
    },

    getAvailableQuarters() {
      const ids = new Set(this.sprints.filter(s => s.status === 'OPEN' || s.status === 'FUTURE').map(s => s.quarterCode));
      return this.quarters.filter(q => ids.has(q.code));
    },

    getPastQuarters() {
      const ids = new Set(this.sprints.filter(s => s.status === 'PAST').map(s => s.quarterCode));
      return this.quarters.filter(q => ids.has(q.code));
    },

    getAvailableSprintsForQuarter(quarterCode) {
      return this.sprints.filter(s => s.quarterCode === quarterCode && (s.status === 'OPEN' || s.status === 'FUTURE'));
    },

    getPastSprintsForQuarter(quarterCode) {
      return this.sprints.filter(s => s.quarterCode === quarterCode && s.status === 'PAST');
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
    contestId: d.contestId || '',
    segmentTree: d.segmentTree || [],
    activeSegment: d.currentSegment || 'overall',

    init() {
      // no-op — segment tree is already rendered server-side
    },

    selectSegment(segmentCode) {
      if (this.activeSegment === segmentCode) return;
      this.activeSegment = segmentCode;
      htmx.ajax('GET', '/contests/' + this.contestId + '?segment=' + segmentCode, {
        target: '#leaderboard-content',
        swap: 'outerHTML'
      });
    }
  };
};

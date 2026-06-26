window.contestCreator = function () {
  const d = window.__contestCreateData || {};
  return {
    sprints: d.sprints || [],
    fromCode: d.fromSprintCode || '',
    toCode: d.toSprintCode || '',
    toOptions: [],
    structurePreview: '',

    get selectedFrom() {
      return this.sprints.find(s => s.code === this.fromCode) || null;
    },

    init() {
      if (this.fromCode) this.onFromChange();
      if (this.fromCode && this.toCode) this.updatePreview();
    },

    onFromChange() {
      this.toCode = '';
      this.toOptions = [];
      this.structurePreview = '';
      const from = this.selectedFrom;
      if (!from) return;

      const options = [{ code: from.code, label: from.name + ' (single sprint)' }];

      const quarterCodes = [...new Set(this.sprints.map(s => s.quarterCode))];
      const fromQIdx = quarterCodes.indexOf(from.quarterCode);
      const quarterSprints = this.sprints.filter(s => s.quarterCode === from.quarterCode);
      const isQuarterStart = quarterSprints.length > 0 && quarterSprints[0].code === from.code;

      if (isQuarterStart) {
        const lastInSameQ = quarterSprints[quarterSprints.length - 1];
        if (lastInSameQ.code !== from.code) {
          options.push({ code: lastInSameQ.code, label: from.quarterName + ' (end of quarter)' });
        }
        for (let qi = fromQIdx + 1; qi < quarterCodes.length; qi++) {
          const qSprints = this.sprints.filter(s => s.quarterCode === quarterCodes[qi]);
          if (qSprints.length > 0) {
            const last = qSprints[qSprints.length - 1];
            options.push({ code: last.code, label: 'Through end of ' + qSprints[0].quarterName });
          }
        }
      }

      this.toOptions = options;
    },

    updatePreview() {
      const from = this.selectedFrom;
      const to = this.sprints.find(s => s.code === this.toCode);
      if (!from || !to) { this.structurePreview = ''; return; }

      const fromIdx = this.sprints.indexOf(from);
      const toIdx = this.sprints.indexOf(to);
      const window = this.sprints.slice(fromIdx, toIdx + 1);
      const quarters = [...new Set(window.map(s => s.quarterCode))];

      const fromGw = from.gwLabel.split('–')[0];
      const toGw = to.gwLabel.split('–')[1];

      if (window.length === 1) {
        this.structurePreview = from.name + ' · ' + from.gwLabel;
      } else if (quarters.length === 1) {
        const lines = [from.quarterName + ' · ' + fromGw + '–' + toGw];
        window.forEach((s, i) => {
          lines.push('  ' + (i < window.length - 1 ? '├─' : '└─') + ' ' + s.name + ' · ' + s.gwLabel);
        });
        this.structurePreview = lines.join('\n');
      } else {
        const lines = ['Overall · ' + fromGw + '–' + toGw];
        quarters.forEach(qCode => {
          const qSprints = window.filter(s => s.quarterCode === qCode);
          const qName = qSprints[0].quarterName;
          const qFrom = qSprints[0].gwLabel.split('–')[0];
          const qTo = qSprints[qSprints.length - 1].gwLabel.split('–')[1];
          lines.push('  ├─ ' + qName + ' · ' + qFrom + '–' + qTo);
          qSprints.forEach((s, i) => {
            lines.push('  │  ' + (i < qSprints.length - 1 ? '├─' : '└─') + ' ' + s.name + ' · ' + s.gwLabel);
          });
        });
        this.structurePreview = lines.join('\n');
      }
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

package com.ligitabl.model.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Score-to-grade mapping shown after each round (emoji + letter, e.g. {@link
 * com.ligitabl.model.domain.ScoreTier#LEGEND}) and on the landing page's full tier table, grouped
 * into Outstanding/Strong/Fair bands. Single source of truth for both — do not re-derive
 * thresholds in templates.
 */
public enum ScoreTier {
    PINNACLE(200, "🤯", "S", "Pinnacle", Group.OUTSTANDING),
    LEGEND(195, "🤩", "A+", "Legend", Group.OUTSTANDING),
    ELITE(190, "😎", "A", "Elite", Group.OUTSTANDING),
    EXPERT(185, "😁", "A-", "Expert", Group.OUTSTANDING),
    PROFICIENT(180, "😃", "B+", "Proficient", Group.STRONG),
    ADVANCED(170, "🙂", "B", "Advanced", Group.STRONG),
    COMPETENT(160, "😌", "C+", "Competent", Group.STRONG),
    NOVICE(145, "😬", "C", "Novice", Group.FAIR),
    ASPIRANT(0, "😩", "D", "Aspirant", Group.FAIR);

    /**
     * Colors are intentionally not modeled here — Tailwind's build-time content scanner only picks
     * up complete literal class strings (e.g. "text-purple-500"), so callers must hardcode the
     * Tailwind classes per group in the template rather than compose them from this enum.
     */
    public enum Group {
        OUTSTANDING,
        STRONG,
        FAIR
    }

    private final int minScore;
    private final String emoji;
    private final String letter;
    private final String tierName;
    private final Group group;

    ScoreTier(int minScore, String emoji, String letter, String tierName, Group group) {
        this.minScore = minScore;
        this.emoji = emoji;
        this.letter = letter;
        this.tierName = tierName;
        this.group = group;
    }

    public int getMinScore() {
        return minScore;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getLetter() {
        return letter;
    }

    public String getTierName() {
        return tierName;
    }

    public Group getGroup() {
        return group;
    }

    /** Resolves the tier for a score. Scores below the lowest tier's threshold still resolve to it. */
    public static ScoreTier forScore(int score) {
        return Arrays.stream(values())
                .filter(t -> score >= t.minScore)
                .findFirst()
                .orElse(ASPIRANT);
    }

    /** Tiers belonging to the given group, highest-scoring first. */
    public static List<ScoreTier> byGroup(Group group) {
        return Arrays.stream(values()).filter(t -> t.group == group).toList();
    }

    /** Human-readable score range for this tier, e.g. "195–199", "200" (top tier), or "< 145" (floor tier). */
    public String getRangeLabel() {
        ScoreTier[] all = values();
        int idx = ordinal();
        if (idx == 0) {
            return String.valueOf(minScore);
        }
        if (idx == all.length - 1) {
            return "< " + all[idx - 1].minScore;
        }
        return minScore + "–" + (all[idx - 1].minScore - 1);
    }
}

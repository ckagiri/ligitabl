package com.ligitabl.model.domain.standings.ranking;

import java.util.Comparator;
import java.util.function.Function;

import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Functional interface for comparing two teams' statistics.
 * Rules can be chained using the then() method.
 */
@FunctionalInterface
public interface RankingRule {

    /**
     * Compare two team statistics.
     *
     * @return negative if a ranks higher, positive if b ranks higher, 0 if equal
     */
    int compare(TeamStats a, TeamStats b);

    /**
     * Chain this rule with another.
     * The next rule is only consulted if this rule returns 0 (tie).
     */
    default RankingRule then(RankingRule next) {
        return (a, b) -> {
            int result = this.compare(a, b);
            return result != 0 ? result : next.compare(a, b);
        };
    }

    /**
     * Create a descending (higher is better) ranking rule.
     */
    static RankingRule descending(Function<TeamStats, Integer> extractor) {
        return Comparator.comparing(extractor).reversed()::compare;
    }

    /**
     * Create an ascending (lower is better) ranking rule.
     */
    static RankingRule ascending(Function<TeamStats, String> extractor) {
        return Comparator.comparing(extractor)::compare;
    }
}

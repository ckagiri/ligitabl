package com.ligitabl.model.domain.standings.ranking;

import com.ligitabl.model.domain.standings.stats.TeamStats;

@FunctionalInterface
public interface RankingRule {
    int compare(TeamStats a, TeamStats b);

    default RankingRule then(RankingRule next) {
        return (a, b) -> {
            int result = compare(a, b);
            return result != 0 ? result : next.compare(a, b);
        };
    }
}

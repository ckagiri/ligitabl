package com.ligitabl.model.domain.standings.ranking;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.ligitabl.model.domain.standings.stats.TeamStats;

public class RankingBuilder {
    private RankingRule rule = (a, b) -> 0;
    private Map<UUID, String> teamShortNames = Map.of();

    public RankingBuilder withTeamShortNames(Map<UUID, String> teamShortNames) {
        this.teamShortNames = Objects.requireNonNull(teamShortNames);
        return this;
    }

    public RankingBuilder then(RankingRule next) {
        Objects.requireNonNull(next);
        rule = rule.then(next);
        return this;
    }

    public RankingBuilder thenDescending(Function<TeamStats, Integer> extractor) {
        Objects.requireNonNull(extractor);
        return then((a, b) -> Integer.compare(extractor.apply(b), extractor.apply(a)));
    }

    public RankingBuilder thenAscending(Function<TeamStats, String> extractor) {
        Objects.requireNonNull(extractor);
        return then((a, b) -> extractor.apply(a).compareTo(extractor.apply(b)));
    }

    public RankingBuilder thenAscendingByShortName() {
        return then((a, b) -> {
            String sa = teamShortNames.getOrDefault(a.teamId(), a.teamId().toString());
            String sb = teamShortNames.getOrDefault(b.teamId(), b.teamId().toString());
            return sa.compareTo(sb);
        });
    }

    public RankingRule build() {
        return rule;
    }
}

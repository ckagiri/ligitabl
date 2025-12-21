package com.ligitabl.model.domain.standings.ranking;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.standings.stats.TeamStats;

public class CachedHeadToHeadRule implements RankingRule {
    private final List<Match> allMatches;
    private final RankingRule tieBreaker;
    private final Map<Set<UUID>, Integer> cache;

    public CachedHeadToHeadRule(List<Match> matches, RankingRule tieBreaker) {
        this.allMatches = Objects.requireNonNull(matches);
        this.tieBreaker = Objects.requireNonNull(tieBreaker);
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public int compare(TeamStats a, TeamStats b) {
        Set<UUID> teams = Set.of(a.teamId(), b.teamId());

        // Check cache
        Integer cached = cache.get(teams);
        if (cached != null) {
            return cached;
        }

        // Calculate head-to-head stats
        List<Match> h2hMatches = allMatches.stream()
                .filter(m -> teams.contains(m.getHomeTeamId()) && teams.contains(m.getAwayTeamId()))
                .filter(Match::isPlayed)
                .toList();

        if (h2hMatches.isEmpty()) {
            return 0; // No head-to-head matches
        }

        // Build mini-table from head-to-head matches only
        Map<UUID, TeamStats> h2hStats = new HashMap<>();
        for (UUID teamId : teams) {
            h2hStats.put(teamId, TeamStats.empty(teamId));
        }

        for (Match match : h2hMatches) {
            match.viewFor(match.getHomeTeamId())
                    .ifPresent(view -> h2hStats.computeIfPresent(match.getHomeTeamId(), (k, v) -> v.withMatch(view)));
            match.viewFor(match.getAwayTeamId())
                    .ifPresent(view -> h2hStats.computeIfPresent(match.getAwayTeamId(), (k, v) -> v.withMatch(view)));
        }

        // Compare using the tie-breaker rule
        int result = tieBreaker.compare(h2hStats.get(a.teamId()), h2hStats.get(b.teamId()));

        // Cache result
        cache.put(teams, result);

        return result;
    }
}

package com.ligitabl.model.domain.standings.ranking;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.ligitabl.model.calculator.StatsCalculator;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.standings.stats.TeamStats;

public class CachedHeadToHeadRule implements RankingRule {
    private final List<Match> matches;
    private final RankingRule h2hTieBreaker;
    private final Map<Set<UUID>, Map<UUID, TeamStats>> cache;

    public CachedHeadToHeadRule(List<Match> matches, RankingRule h2hTieBreaker) {
        this.matches = Objects.requireNonNull(matches);
        this.h2hTieBreaker = Objects.requireNonNull(h2hTieBreaker);
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public int compare(TeamStats a, TeamStats b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        Set<UUID> key = Set.of(a.teamId(), b.teamId());
        Map<UUID, TeamStats> h2hStats = cache.computeIfAbsent(key, k -> computeHeadToHeadStats(k));

        TeamStats h2hA = h2hStats.get(a.teamId());
        TeamStats h2hB = h2hStats.get(b.teamId());

        if (h2hA == null || h2hB == null || !h2hA.hasPlayed()) return 0;

        int cmp = h2hTieBreaker.compare(h2hA, h2hB);
        if (cmp != 0) {
            return cmp;
        }

        // If still tied, fall back to alphabetical by team ID
        return a.teamId().compareTo(b.teamId());
    }

    private Map<UUID, TeamStats> computeHeadToHeadStats(Set<UUID> teamIds) {
        List<Match> h2hMatches = matches.stream()
                .filter(m -> m.getScore() != null)
                .filter(m -> teamIds.contains(m.getHomeTeamId()) && teamIds.contains(m.getAwayTeamId()))
                .toList();
        return StatsCalculator.computeStats(new ArrayList<>(teamIds), h2hMatches);
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }
}

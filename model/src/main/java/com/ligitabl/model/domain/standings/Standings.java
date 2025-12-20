package com.ligitabl.model.domain.standings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Standings(UUID id, UUID seasonId, int roundPosition, List<StandingsTeamRank> rankings) {
    public Standings {
        Objects.requireNonNull(id, "ID cannot be null");
        Objects.requireNonNull(seasonId, "Season ID cannot be null");
        Objects.requireNonNull(rankings, "Rankings cannot be null");
        if (roundPosition < 0) throw new IllegalArgumentException("Round position must be non-negative");
        if (rankings.isEmpty()) throw new IllegalArgumentException("Rankings cannot be empty");
        rankings = List.copyOf(rankings);
    }

    public int teamCount() {
        return rankings.size();
    }

    public Optional<StandingsTeamRank> findByTeamCode(String teamCode) {
        Objects.requireNonNull(teamCode, "Team code cannot be null");
        return rankings.stream().filter(r -> r.teamCode().equals(teamCode)).findFirst();
    }

    public Optional<StandingsTeamRank> findByPosition(int position) {
        return rankings.stream().filter(r -> r.position() == position).findFirst();
    }

    public Optional<StandingsTeamRank> getLeader() {
        return findByPosition(1);
    }

    public List<StandingsTeamRank> getTopN(int n) {
        if (n < 1) throw new IllegalArgumentException("N must be at least 1");
        return rankings.stream().filter(r -> r.position() <= n).toList();
    }

    public List<StandingsTeamRank> getBottomN(int n) {
        if (n < 1) throw new IllegalArgumentException("N must be at least 1");
        if (rankings.isEmpty()) return List.of();
        int last = rankings.stream().mapToInt(StandingsTeamRank::position).max().orElse(0);
        int from = Math.max(1, last - n + 1);
        return rankings.stream().filter(r -> r.position() >= from).toList();
    }

    @Override
    public String toString() {
        return String.format(
                "Standings[id=%s, season=%s, round=%d, teams=%d]", id, seasonId, roundPosition, teamCount());
    }
}

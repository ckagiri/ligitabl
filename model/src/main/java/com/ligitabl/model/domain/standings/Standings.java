package com.ligitabl.model.domain.standings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.AbstractModel;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Standings extends AbstractModel<UUID> {
    @NotNull
    private UUID seasonId;

    private int roundPosition;

    @NotNull
    private List<StandingsTeamRank> rankings;

    public int teamCount() {
        return rankings == null ? 0 : rankings.size();
    }

    public Optional<StandingsTeamRank> findByTeamCode(String teamCode) {
        Objects.requireNonNull(teamCode, "Team code cannot be null");
        if (rankings == null) return Optional.empty();
        return rankings.stream().filter(r -> r.teamCode().equals(teamCode)).findFirst();
    }

    public Optional<StandingsTeamRank> findByPosition(int position) {
        if (rankings == null) return Optional.empty();
        return rankings.stream().filter(r -> r.position() == position).findFirst();
    }

    public Optional<StandingsTeamRank> getLeader() {
        return findByPosition(1);
    }

    public List<StandingsTeamRank> getTopN(int n) {
        if (n < 1) throw new IllegalArgumentException("N must be at least 1");
        if (rankings == null) return List.of();
        return rankings.stream().filter(r -> r.position() <= n).toList();
    }

    public List<StandingsTeamRank> getBottomN(int n) {
        if (n < 1) throw new IllegalArgumentException("N must be at least 1");
        if (rankings == null || rankings.isEmpty()) return List.of();
        int last = rankings.stream().mapToInt(StandingsTeamRank::position).max().orElse(0);
        int from = Math.max(1, last - n + 1);
        return rankings.stream().filter(r -> r.position() >= from).toList();
    }

    @Override
    public String toString() {
        return String.format(
                "Standings[id=%s, season=%s, round=%d, teams=%d]", getId(), seasonId, roundPosition, teamCount());
    }
}

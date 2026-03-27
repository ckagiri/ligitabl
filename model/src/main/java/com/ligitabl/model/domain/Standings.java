package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private boolean finalised;

    private OffsetDateTime finalisedAt;

    // Populated by the database (defaults/triggers)
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;

    public static Standings create(UUID seasonId, int roundPosition, List<StandingsTeamRank> rankings) {
        return Standings.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundPosition(roundPosition)
                .rankings(rankings)
                .finalised(false)
                .build();
    }

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

    @Override
    public String toString() {
        return String.format(
                "Standings[id=%s, season=%s, round=%d, teams=%d]", getId(), seasonId, roundPosition, teamCount());
    }
}

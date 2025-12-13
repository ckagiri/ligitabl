package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Match extends AbstractModel<UUID> {
    @NotNull
    private Integer clientId;

    @NotNull
    private UUID homeTeamId;

    @NotNull
    private UUID awayTeamId;

    @NotNull
    private UUID roundId;

    private Score score;

    @NotNull
    private String slug;

    @NotNull
    private MatchStatus status;

    private OffsetDateTime kickOff;

    private String venue;

    private int matchday;
}

package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class SeasonPrediction extends AbstractModel<UUID> {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID seasonId;

    @NotNull
    private List<TeamRank> initialRankings;

    @NotNull
    private List<TeamRank> currentRankings;

    private JsonNode swaps;

    private OffsetDateTime lastSwapAt;

    private int atRoundNumber;
}

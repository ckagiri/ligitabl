package com.ligitabl.model.domain.prediction;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.AbstractModel;
import com.ligitabl.model.domain.league.TeamRank;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
public class RoundSubmission extends AbstractModel<UUID> {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID seasonId;

    private int roundPosition;

    @NotNull
    private List<TeamRank> rankings;

    @NotNull
    private UUID seasonPredictionId;
}

package com.ligitabl.model.domain;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
public class WhatIfPrediction extends AbstractModel<UUID> {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID roundId;

    @NotNull
    private List<WhatIfScore> scores;
}

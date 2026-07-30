package com.ligitabl.model.domain;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class WhatIfPrediction extends AbstractModel<UUID> {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID roundId;

    @NotNull
    private List<WhatIfScore> scores;
}

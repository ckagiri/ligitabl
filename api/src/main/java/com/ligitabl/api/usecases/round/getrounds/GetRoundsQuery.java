package com.ligitabl.api.usecases.round.getrounds;

import com.ligitabl.model.validator.ValidSlug;

import jakarta.validation.constraints.NotNull;

public record GetRoundsQuery(
        @NotNull(message = "Competition slug cannot be null") @ValidSlug String competitionSlug,
        @NotNull(message = "Season slug cannot be null") @ValidSlug String seasonSlug) {}

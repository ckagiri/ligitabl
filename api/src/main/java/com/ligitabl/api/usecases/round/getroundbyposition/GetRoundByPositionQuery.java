package com.ligitabl.api.usecases.round.getroundbyposition;

import com.ligitabl.model.validator.ValidSlug;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GetRoundByPositionQuery(
        @NotNull(message = "Competition slug cannot be null") @ValidSlug String competitionSlug,
        @NotNull(message = "Season slug cannot be null") @ValidSlug String seasonSlug,
        @Min(value = 1, message = "Position must be at least 1") int position) {}

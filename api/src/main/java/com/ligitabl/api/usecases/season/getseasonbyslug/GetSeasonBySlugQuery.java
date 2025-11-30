package com.ligitabl.api.usecases.season.getseasonbyslug;

import com.ligitabl.model.validator.ValidSlug;
import jakarta.validation.constraints.NotNull;

public record GetSeasonBySlugQuery(
        @NotNull(message = "Competition slug cannot be null")
        @ValidSlug
        String competitionSlug,

        @NotNull(message = "Season slug cannot be null")
        @ValidSlug
        String seasonSlug
) {}

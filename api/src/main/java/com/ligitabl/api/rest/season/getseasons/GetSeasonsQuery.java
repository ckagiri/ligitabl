package com.ligitabl.api.rest.season.getseasons;

import com.ligitabl.model.validator.ValidSlug;

import jakarta.validation.constraints.NotNull;

public record GetSeasonsQuery(
        @NotNull(message = "Competition slug cannot be null") @ValidSlug String competitionSlug) {}

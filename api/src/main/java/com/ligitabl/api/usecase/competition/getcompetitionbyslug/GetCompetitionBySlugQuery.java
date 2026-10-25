package com.ligitabl.api.usecase.competition.getcompetitionbyslug;

import com.ligitabl.model.validator.ValidSlug;

import jakarta.validation.constraints.NotNull;

public record GetCompetitionBySlugQuery(@NotNull(message = "Slug cannot be null") @ValidSlug String slug) {}

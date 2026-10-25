package com.ligitabl.api.usecase.team.getteambyslug;

import com.ligitabl.model.validator.ValidSlug;

import jakarta.validation.constraints.NotNull;

public record GetTeamBySlugQuery(@NotNull(message = "Slug cannot be null") @ValidSlug String slug) {}

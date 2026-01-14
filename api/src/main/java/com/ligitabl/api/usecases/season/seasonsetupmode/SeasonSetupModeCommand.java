package com.ligitabl.api.usecases.season.seasonsetupmode;

import java.util.Optional;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SeasonSetupModeCommand {

    @Nullable
    String competitionIdentifier;

    String seasonSlug;
    SetupModeAction action;

    public Optional<String> getCompetitionIdentifier() {
        return Optional.ofNullable(competitionIdentifier);
    }
}

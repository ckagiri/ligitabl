package com.ligitabl.api.usecase.round.advanceround;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.ligitabl.model.domain.RoundStatus;

public record GetRoundAdvancementStatusResult(
        UUID roundId,
        int position,
        RoundStatus status,
        boolean scheduled,
        OffsetDateTime advanceAt,
        Integer minutesRemaining,
        boolean advanced,
        boolean canOverride) {}

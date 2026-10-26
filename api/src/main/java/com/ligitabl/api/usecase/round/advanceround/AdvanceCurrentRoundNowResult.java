package com.ligitabl.api.usecase.round.advanceround;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdvanceCurrentRoundNowResult(UUID roundId, int fromPosition, int toPosition, OffsetDateTime advancedAt) {}

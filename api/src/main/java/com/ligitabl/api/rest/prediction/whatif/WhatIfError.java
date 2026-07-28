package com.ligitabl.api.rest.prediction.whatif;

import java.util.List;
import java.util.UUID;

public sealed interface WhatIfError {
    record SeasonNotFound(UUID seasonId) implements WhatIfError {}

    record SeasonNotInPlay(UUID seasonId) implements WhatIfError {}

    record SeasonCompleted() implements WhatIfError {}

    record SeasonInSetupMode() implements WhatIfError {}

    record RoundNotFound(UUID seasonId) implements WhatIfError {}

    record RoundNotOpen(String roundStatus) implements WhatIfError {}

    record UnknownMatch(List<UUID> matchIds) implements WhatIfError {}

    record MissingScores(List<UUID> matchIds) implements WhatIfError {}

    record InvalidScore(UUID matchId, String reason) implements WhatIfError {}

    record CalculationFailed(String message) implements WhatIfError {}
}

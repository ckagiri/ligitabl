package com.ligitabl.api.usecase.shared;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Admin-facing notifications raised by use cases. */
public interface AdminAlerts {
    void notifyNewUserSignup(String publicId, String displayName, String email, String signupMethod);

    void notifyRoundFinalized(UUID roundId, int roundPosition, int submissions, int results, boolean lastRound);

    void notifyAdvancementScheduled(UUID roundId, int roundPosition, OffsetDateTime advanceAt, int delayMinutes);

    void notifyRoundAdvanced(int fromPosition, UUID seasonId, boolean lastRound);

    void notifyAdvancementFailed(UUID roundId, String error);
}

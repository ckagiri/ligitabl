package com.ligitabl.api.rest.contest.renewcontest;

import java.util.UUID;

public sealed interface RenewContestError {
    record ContestNotFound(UUID contestId) implements RenewContestError {}

    record NotOwner(UUID contestId) implements RenewContestError {}

    record AlreadyRenewed(UUID contestId) implements RenewContestError {}

    record NotPrivate(UUID contestId) implements RenewContestError {}

    /** Structurally renewable but the timing gate hasn't been reached yet. */
    record TooEarly(UUID contestId) implements RenewContestError {}

    /** The resolved renewal window (the sprint after the original) has already started or passed. */
    record RenewalWindowElapsed(UUID contestId) implements RenewContestError {}

    record SeasonNotFound() implements RenewContestError {}

    record CompetitionNotFound() implements RenewContestError {}

    /** Covers all button-visibility conditions collapsing to "not renewable": not LIVE, full season, or no sprint remains. */
    record NotRenewable(UUID contestId) implements RenewContestError {}

    record InvalidToCombination(String toCode) implements RenewContestError {}

    /** The auto-generated renewed name collides with another contest this owner already has this season. */
    record NameConflict(String name) implements RenewContestError {}
}

package com.ligitabl.api.rest.contest.renewcontest;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.contest.shared.ContestRenewalCalculator;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

/**
 * Read-only counterpart to {@link RenewContestUseCase}: resolves the renewal window and valid TO
 * options for the confirmation modal, without mutating anything. Mirrors that use case's
 * current-season / past-season branching and its private-only / timing-gate rules.
 */
@Service
@RequiredArgsConstructor
public class GetContestRenewalOptionsUseCase {

    private final ContestRepo contestRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final CompetitionRepo competitionRepo;
    private final EntryRepo entryRepo;
    private final ContestSupport contestSupport;

    public Either<RenewContestError, GetContestRenewalOptionsResult> execute(UUID contestId, UUID userId) {
        Contest contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) return Either.left(new RenewContestError.ContestNotFound(contestId));

        if (!contest.isOwnedBy(userId)) return Either.left(new RenewContestError.NotOwner(contestId));

        if (!contest.isPrivate()) return Either.right(GetContestRenewalOptionsResult.hidden());

        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        if (season == null) return Either.left(new RenewContestError.SeasonNotFound());

        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null) return Either.left(new RenewContestError.CompetitionNotFound());

        List<RoundSpan> phases = competition.getPhases() != null ? competition.getPhases() : List.of();

        RoundSpan originalFrom =
                PhaseRules.sprintStartingAt(phases, contest.getFromRoundPosition()).orElse(null);
        RoundSpan originalTo =
                PhaseRules.sprintEndingAt(phases, contest.getToRoundPosition()).orElse(null);

        if (originalFrom == null || originalTo == null || contest.getRenewedIntoContestId() != null) {
            return Either.right(GetContestRenewalOptionsResult.hidden());
        }

        Season activeSeason =
                seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        boolean isCurrentSeason = activeSeason != null && activeSeason.getId().equals(season.getId());

        RoundSpan from;
        RoundSpan defaultTo;
        List<String> toOptionCodes;
        boolean enabled;

        if (isCurrentSeason) {
            if (!ContestRenewalCalculator.isRenewable(originalFrom, originalTo, phases, true, false)) {
                return Either.right(GetContestRenewalOptionsResult.hidden());
            }
            from = ContestRenewalCalculator.resolveRenewalFrom(originalTo, phases).orElseThrow();
            defaultTo = ContestRenewalCalculator.resolveDefaultTo(originalFrom, originalTo, from, phases);
            toOptionCodes = ContestRenewalCalculator.resolveValidToOptions(from, phases).stream()
                    .map(RoundSpan::getCode)
                    .toList();

            int currentRoundPosition = roundRepo.findPosition(season.getCurrentRoundId());
            enabled = ContestRenewalCalculator.hasReachedRenewalTiming(
                            originalFrom, originalTo, currentRoundPosition, phases)
                    && contestSupport.isOpenForJoining(contest, season, competition);
        } else {
            if (activeSeason == null) return Either.right(GetContestRenewalOptionsResult.hidden());

            var window = ContestRenewalCalculator.resolvePastSeasonWindow(originalFrom, originalTo, phases);
            from = window.from();
            defaultTo = window.defaultTo();
            toOptionCodes =
                    window.validToOptions().stream().map(RoundSpan::getCode).toList();
            enabled = true;
        }

        int activeMembers = entryRepo.countActiveByContestId(contestId);

        return Either.right(new GetContestRenewalOptionsResult(
                true, enabled, contest.getName(), from.getCode(), defaultTo.getCode(), toOptionCodes, activeMembers));
    }
}

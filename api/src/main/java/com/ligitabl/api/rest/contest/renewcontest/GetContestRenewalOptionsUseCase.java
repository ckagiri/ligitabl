package com.ligitabl.api.rest.contest.renewcontest;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.contest.shared.ContestRenewalCalculator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

/**
 * Read-only counterpart to {@link RenewContestUseCase}: resolves the renewal window and valid TO
 * options for the confirmation modal, without mutating anything. Only supports the current-season
 * renewal path — past-season renewal has no UI entry point yet.
 */
@Service
@RequiredArgsConstructor
public class GetContestRenewalOptionsUseCase {

    private final ContestRepo contestRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final EntryRepo entryRepo;

    public Either<RenewContestError, GetContestRenewalOptionsResult> execute(UUID contestId, UUID userId) {
        Contest contest = contestRepo.findById(contestId).orElse(null);
        if (contest == null) return Either.left(new RenewContestError.ContestNotFound(contestId));

        if (!contest.isOwnedBy(userId)) return Either.left(new RenewContestError.NotOwner(contestId));

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

        Season activeSeason =
                seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        boolean isCurrentSeason = activeSeason != null && activeSeason.getId().equals(season.getId());
        boolean alreadyRenewed = contest.getRenewedIntoContestId() != null;

        if (originalFrom == null
                || originalTo == null
                || !isCurrentSeason
                || !ContestRenewalCalculator.isRenewable(originalFrom, originalTo, phases, true, alreadyRenewed)) {
            return Either.right(GetContestRenewalOptionsResult.notRenewable());
        }

        RoundSpan from =
                ContestRenewalCalculator.resolveRenewalFrom(originalTo, phases).orElseThrow();
        RoundSpan defaultTo = ContestRenewalCalculator.resolveDefaultTo(originalFrom, originalTo, from, phases);
        List<String> toOptionCodes = ContestRenewalCalculator.resolveValidToOptions(from, phases).stream()
                .map(RoundSpan::getCode)
                .toList();

        int activeMembers = entryRepo.countActiveByContestId(contestId);

        return Either.right(new GetContestRenewalOptionsResult(
                true, contest.getName(), from.getCode(), defaultTo.getCode(), toOptionCodes, activeMembers));
    }
}

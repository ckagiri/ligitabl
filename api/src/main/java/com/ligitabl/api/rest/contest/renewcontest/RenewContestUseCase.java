package com.ligitabl.api.rest.contest.renewcontest;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.contest.ContestCodeGenerator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.contest.shared.ContestRenewalCalculator;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Renews a private contest. If the contest belongs to the competition's current active season, it
 * renews into the sprint immediately following its own window in that same season. If the contest
 * belongs to a past season, it renews into the start of the new active season instead (fixed at
 * S1, defaulting to end of Q1, or fixed at the last sprint if the original was a full season). The
 * renewed contest's name is the original's name with the new window's period label appended (e.g.
 * "Homeboyz" → "Homeboyz H2"), keeping repeated renewals distinct — names are unique per owner per
 * season. Rename the contest afterwards from its settings page if a different name is wanted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RenewContestUseCase {

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final CompetitionRepo competitionRepo;
    private final ContestCodeGenerator codeGenerator;
    private final ContestSupport contestSupport;

    @Transactional
    public Either<RenewContestError, RenewContestResult> execute(RenewContestCommand cmd) {
        Contest original = contestRepo.findById(cmd.contestId()).orElse(null);
        if (original == null) return Either.left(new RenewContestError.ContestNotFound(cmd.contestId()));

        if (!original.isOwnedBy(cmd.userId())) return Either.left(new RenewContestError.NotOwner(cmd.contestId()));

        if (!original.isPrivate()) return Either.left(new RenewContestError.NotPrivate(cmd.contestId()));

        if (original.getRenewedIntoContestId() != null)
            return Either.left(new RenewContestError.AlreadyRenewed(cmd.contestId()));

        Season season = seasonRepo.findById(original.getSeasonId()).orElse(null);
        if (season == null) return Either.left(new RenewContestError.SeasonNotFound());

        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null) return Either.left(new RenewContestError.CompetitionNotFound());

        if (!contestSupport.isOpenForJoining(original, season, competition))
            return Either.left(new RenewContestError.ContestClosed(cmd.contestId()));

        List<RoundSpan> phases = competition.getPhases() != null ? competition.getPhases() : List.of();

        RoundSpan originalFrom =
                PhaseRules.sprintStartingAt(phases, original.getFromRoundPosition()).orElse(null);
        RoundSpan originalTo =
                PhaseRules.sprintEndingAt(phases, original.getToRoundPosition()).orElse(null);
        if (originalFrom == null || originalTo == null)
            return Either.left(new RenewContestError.NotRenewable(cmd.contestId()));

        Season activeSeason =
                seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        boolean isCurrentSeason = activeSeason != null && activeSeason.getId().equals(season.getId());

        RoundSpan from;
        RoundSpan to;
        UUID targetSeasonId;

        if (isCurrentSeason) {
            if (!ContestRenewalCalculator.isRenewable(originalFrom, originalTo, phases, true, false))
                return Either.left(new RenewContestError.NotRenewable(cmd.contestId()));

            int currentRoundPosition = roundRepo.findPosition(season.getCurrentRoundId());
            if (!ContestRenewalCalculator.hasReachedRenewalTiming(originalFrom, originalTo, currentRoundPosition, phases))
                return Either.left(new RenewContestError.TooEarly(cmd.contestId()));

            from = ContestRenewalCalculator.resolveRenewalFrom(originalTo, phases).orElseThrow();
            to = findByCode(ContestRenewalCalculator.resolveValidToOptions(from, phases), cmd.toSprintCode());
            targetSeasonId = season.getId();
        } else {
            if (activeSeason == null) return Either.left(new RenewContestError.NotRenewable(cmd.contestId()));

            var window = ContestRenewalCalculator.resolvePastSeasonWindow(originalFrom, originalTo, phases);
            from = window.from();
            to = findByCode(window.validToOptions(), cmd.toSprintCode());
            targetSeasonId = activeSeason.getId();
        }

        if (to == null) return Either.left(new RenewContestError.InvalidToCombination(cmd.toSprintCode()));

        String renewedName = original.getName() + " " + PhaseRules.periodLabel(from, to, phases);
        if (contestRepo.existsByOwnerSeasonAndName(targetSeasonId, original.getOwnerId(), renewedName, null))
            return Either.left(new RenewContestError.NameConflict(renewedName));

        String joinCode = generateUniqueCode();

        Contest renewed = Contest.builder()
                .seasonId(targetSeasonId)
                .name(renewedName)
                .isPrivate(true)
                .isOpen(original.isOpen())
                .joinCode(joinCode)
                .fromRoundPosition(from.getFrom())
                .toRoundPosition(to.getTo())
                .maxEntries(original.getMaxEntries())
                .ownerId(original.getOwnerId())
                .build();

        Contest savedRenewed = contestRepo.save(renewed);

        entryRepo.findByContestId(original.getId()).stream()
                .filter(e -> e.getRemovedAtRound() == null)
                .forEach(entry -> entryRepo.save(Entry.builder()
                        .userId(entry.getUserId())
                        .contestId(savedRenewed.getId())
                        .joinedAtRound(savedRenewed.getFromRoundPosition())
                        .build()));

        original.setRenewedIntoContestId(savedRenewed.getId());
        contestRepo.save(original);

        log.info("User {} renewed contest {} into {}", cmd.userId(), original.getId(), savedRenewed.getId());
        return Either.right(new RenewContestResult(savedRenewed.getId(), joinCode));
    }

    private RoundSpan findByCode(List<RoundSpan> options, String code) {
        return options.stream()
                .filter(s -> s.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = codeGenerator.generate();
        } while (contestRepo.findByJoinCode(code).isPresent());
        return code;
    }
}

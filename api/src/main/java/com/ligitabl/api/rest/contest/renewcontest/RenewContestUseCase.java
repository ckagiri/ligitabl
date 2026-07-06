package com.ligitabl.api.rest.contest.renewcontest;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.contest.ContestCodeGenerator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.contest.shared.ContestRenewalCalculator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Renews a private contest into the sprint immediately following its own window, within the same
 * active season. Past-season renewal (renewing into a newly started season) is not yet supported
 * by this use case.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RenewContestUseCase {

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final ContestCodeGenerator codeGenerator;

    @Transactional
    public Either<RenewContestError, RenewContestResult> execute(RenewContestCommand cmd) {
        Contest original = contestRepo.findById(cmd.contestId()).orElse(null);
        if (original == null) return Either.left(new RenewContestError.ContestNotFound(cmd.contestId()));

        if (!original.isOwnedBy(cmd.userId())) return Either.left(new RenewContestError.NotOwner(cmd.contestId()));

        if (original.getRenewedIntoContestId() != null)
            return Either.left(new RenewContestError.AlreadyRenewed(cmd.contestId()));

        Season season = seasonRepo.findById(original.getSeasonId()).orElse(null);
        if (season == null) return Either.left(new RenewContestError.SeasonNotFound());

        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null) return Either.left(new RenewContestError.CompetitionNotFound());

        List<RoundSpan> phases = competition.getPhases() != null ? competition.getPhases() : List.of();

        RoundSpan originalFrom = sprintStartingAt(phases, original.getFromRoundPosition());
        RoundSpan originalTo = sprintEndingAt(phases, original.getToRoundPosition());
        if (originalFrom == null || originalTo == null)
            return Either.left(new RenewContestError.NotRenewable(cmd.contestId()));

        Season activeSeason =
                seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        boolean isLive = activeSeason != null && activeSeason.getId().equals(season.getId());

        if (!ContestRenewalCalculator.isRenewable(originalFrom, originalTo, phases, isLive, false))
            return Either.left(new RenewContestError.NotRenewable(cmd.contestId()));

        RoundSpan from =
                ContestRenewalCalculator.resolveRenewalFrom(originalTo, phases).orElseThrow();

        List<RoundSpan> validToOptions = ContestRenewalCalculator.resolveValidToOptions(from, phases);
        RoundSpan to = validToOptions.stream()
                .filter(s -> s.getCode().equalsIgnoreCase(cmd.toSprintCode()))
                .findFirst()
                .orElse(null);
        if (to == null) return Either.left(new RenewContestError.InvalidToCombination(cmd.toSprintCode()));

        String joinCode = generateUniqueCode();

        Contest renewed = Contest.builder()
                .seasonId(season.getId())
                .name(original.getName())
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

    private RoundSpan sprintStartingAt(List<RoundSpan> phases, int fromRoundPosition) {
        return PhaseRules.sprintsOf(phases).stream()
                .filter(s -> s.getFrom() == fromRoundPosition)
                .findFirst()
                .orElse(null);
    }

    private RoundSpan sprintEndingAt(List<RoundSpan> phases, int toRoundPosition) {
        return PhaseRules.sprintsOf(phases).stream()
                .filter(s -> s.getTo() == toRoundPosition)
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

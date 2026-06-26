package com.ligitabl.api.rest.contest.previewcontestbycode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PreviewContestByCodeUseCase {

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final CompetitionRepo competitionRepo;

    public Either<PreviewContestByCodeError, ContestPreviewDto> execute(String joinCode) {
        var contest = contestRepo.findByJoinCode(joinCode).orElse(null);
        if (contest == null) return Either.left(new PreviewContestByCodeError.ContestNotFound(joinCode));

        if (!contest.isOpen()) return Either.left(new PreviewContestByCodeError.ContestClosed());

        var competitionResult = resolveCompetition(contest.getSeasonId());
        if (competitionResult.isLeft()) return competitionResult.map(c -> null);
        Competition competition = competitionResult.get();

        List<RoundSpan> phases = competition.getPhases();
        int memberCount = entryRepo.countActiveByContestId(contest.getId());
        String gwRange = "GW " + contest.getFromRoundPosition() + "–" + contest.getToRoundPosition();

        return Either.right(new ContestPreviewDto(
                contest.getId(),
                contest.getName(),
                resolveScopeCode(contest, phases),
                resolveScopeLabel(contest, phases, gwRange),
                gwRange,
                memberCount,
                contest.isOpen()));
    }

    private Either<PreviewContestByCodeError, Competition> resolveCompetition(UUID seasonId) {
        return competitionRepo.findAll().stream()
                .filter(c -> c.getActiveSeasonId() != null && c.getActiveSeasonId().equals(seasonId))
                .findFirst()
                .<Either<PreviewContestByCodeError, Competition>>map(Either::right)
                .orElseGet(() -> Either.left(
                        new PreviewContestByCodeError.CompetitionNotFound(seasonId.toString())));
    }

    private String resolveScopeCode(Contest contest, List<RoundSpan> phases) {
        return findMatchingPhase(contest, phases)
                .map(RoundSpan::getCode)
                .orElse("custom");
    }

    private String resolveScopeLabel(Contest contest, List<RoundSpan> phases, String gwRange) {
        return findMatchingPhase(contest, phases)
                .map(p -> buildPhaseName(p, phases))
                .orElse(gwRange);
    }

    private Optional<RoundSpan> findMatchingPhase(Contest contest, List<RoundSpan> phases) {
        return phases.stream()
                .filter(p -> p.getFrom() == contest.getFromRoundPosition()
                        && p.getTo() == contest.getToRoundPosition())
                .findFirst();
    }

    private String buildPhaseName(RoundSpan phase, List<RoundSpan> allPhases) {
        if (phase.getType() == PhaseType.FULL_SEASON) return "Overall Season";
        if (phase.getType() == PhaseType.QUARTER) return phase.getName();

        String quarterName = allPhases.stream()
                .filter(p -> p.getType() == PhaseType.QUARTER)
                .filter(q -> q.getFrom() <= phase.getFrom() && phase.getTo() <= q.getTo())
                .map(RoundSpan::getName)
                .findFirst()
                .orElse(null);
        return quarterName != null ? quarterName + " · " + phase.getName() : phase.getName();
    }
}

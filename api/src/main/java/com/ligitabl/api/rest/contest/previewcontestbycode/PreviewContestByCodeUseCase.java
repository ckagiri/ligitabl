package com.ligitabl.api.rest.contest.previewcontestbycode;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
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

        Competition competition = competitionRepo.findAll().stream()
                .filter(c -> c.getActiveSeasonId() != null
                        && c.getActiveSeasonId().equals(contest.getSeasonId()))
                .findFirst()
                .orElse(null);

        if (competition == null)
            return Either.left(
                    new PreviewContestByCodeError.CompetitionNotFound(contest.getSeasonId().toString()));

        List<RoundSpan> phases = competition.getPhases();
        int memberCount = entryRepo.countActiveByContestId(contest.getId());
        String gwRange = "GW " + contest.getFromRoundPosition() + "–" + contest.getToRoundPosition();

        RoundSpan matchingPhase = phases.stream()
                .filter(p -> p.getFrom() == contest.getFromRoundPosition()
                        && p.getTo() == contest.getToRoundPosition())
                .findFirst()
                .orElse(null);

        String scopeCode = matchingPhase != null ? matchingPhase.getCode() : "custom";
        String scopeLabel = matchingPhase != null
                ? buildPhaseName(matchingPhase, phases)
                : gwRange;

        return Either.right(new ContestPreviewDto(
                contest.getId(),
                contest.getName(),
                scopeCode,
                scopeLabel,
                gwRange,
                memberCount,
                contest.isOpen()));
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

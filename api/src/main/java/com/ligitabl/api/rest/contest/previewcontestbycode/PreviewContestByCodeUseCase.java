package com.ligitabl.api.rest.contest.previewcontestbycode;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.MatchRepo.RoundDateRange;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PreviewContestByCodeUseCase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d");

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final MatchRepo matchRepo;

    public Either<PreviewContestByCodeError, ContestPreviewDto> execute(String joinCode) {
        var contest = contestRepo.findByJoinCode(joinCode).orElse(null);
        if (contest == null) return Either.left(new PreviewContestByCodeError.ContestNotFound(joinCode));

        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        if (season == null)
            return Either.left(new PreviewContestByCodeError.CompetitionNotFound(
                    contest.getSeasonId().toString()));

        // A closed contest, or one whose season is neither in play nor in pre-season (off-season,
        // inactive, or any past season), is treated the same as closed — no live preview to join.
        if (!contest.isOpen() || (!season.isInPlay() && !season.isPreSeason()))
            return Either.left(new PreviewContestByCodeError.ContestClosed());

        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null)
            return Either.left(new PreviewContestByCodeError.CompetitionNotFound(
                    contest.getSeasonId().toString()));

        List<RoundSpan> phases = competition.getPhases();
        int memberCount = entryRepo.countActiveByContestId(contest.getId());
        String gwRange = "GW " + contest.getFromRoundPosition() + "–" + contest.getToRoundPosition();

        Map<Integer, RoundDateRange> roundDates = matchRepo.groupRoundDateRangesBySeason(contest.getSeasonId());
        String dateRange = resolveDateRange(contest, roundDates);

        return Either.right(new ContestPreviewDto(
                contest.getId(),
                contest.getName(),
                resolveScopeCode(contest, phases),
                resolveScopeLabel(contest, phases, gwRange),
                gwRange,
                dateRange,
                memberCount,
                contest.isOpen()));
    }

    private String resolveScopeCode(Contest contest, List<RoundSpan> phases) {
        return findMatchingPhase(contest, phases).map(RoundSpan::getCode).orElse("custom");
    }

    private String resolveScopeLabel(Contest contest, List<RoundSpan> phases, String gwRange) {
        return findMatchingPhase(contest, phases)
                .map(p -> buildPhaseName(p, phases))
                .orElse(gwRange);
    }

    private Optional<RoundSpan> findMatchingPhase(Contest contest, List<RoundSpan> phases) {
        return phases.stream()
                .filter(p -> p.getFrom() == contest.getFromRoundPosition() && p.getTo() == contest.getToRoundPosition())
                .findFirst();
    }

    private String resolveDateRange(Contest contest, Map<Integer, RoundDateRange> roundDates) {
        RoundDateRange from = roundDates.get(contest.getFromRoundPosition());
        RoundDateRange to = roundDates.get(contest.getToRoundPosition());
        if (from == null || to == null) return null;
        String start =
                from.firstKickoff().atZoneSameInstant(java.time.ZoneOffset.UTC).format(DATE_FMT);
        String end =
                to.lastKickoff().atZoneSameInstant(java.time.ZoneOffset.UTC).format(DATE_FMT);
        return start + " – " + end;
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

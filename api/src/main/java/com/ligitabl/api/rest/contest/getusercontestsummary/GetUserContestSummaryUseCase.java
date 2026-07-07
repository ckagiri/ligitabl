package com.ligitabl.api.rest.contest.getusercontestsummary;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.ligitabl.api.rest.contest.shared.ContestRankResolver;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetUserContestSummaryUseCase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final ContestRankResolver contestRankResolver;

    public GetUserContestSummaryResult execute(GetUserContestSummaryQuery query) {
        var competition = competitionRepo.findBySlug(query.competitionSlug()).orElse(null);
        if (competition == null) return new GetUserContestSummaryResult(List.of(), List.of());

        var season = seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        if (season == null) return new GetUserContestSummaryResult(List.of(), List.of());

        var mainContest = contestRepo.findMainBySeasonId(season.getId()).orElse(null);
        if (mainContest == null) return new GetUserContestSummaryResult(List.of(), List.of());

        Round currentRound = season.getCurrentRoundId() != null
                ? roundRepo.findById(season.getCurrentRoundId()).orElse(null)
                : null;

        Map<Integer, MatchRepo.RoundDateRange> dateRanges = matchRepo.groupRoundDateRangesBySeason(season.getId());

        List<GeneralContestRowDto> generalRows =
                buildGeneralRows(competition, season, mainContest.getId(), query.userId(), currentRound, dateRanges);

        List<PrivateContestRowDto> privateRows = buildPrivateRows(query.userId(), season.getId());

        return new GetUserContestSummaryResult(generalRows, privateRows);
    }

    private List<GeneralContestRowDto> buildGeneralRows(
            Competition competition,
            Season season,
            UUID mainContestId,
            UUID userId,
            Round currentRound,
            Map<Integer, MatchRepo.RoundDateRange> dateRanges) {

        List<RoundSpan> phases = competition.getPhases();

        RoundSpan fullSeason = phases.stream()
                .filter(p -> p.getType() == PhaseType.FULL_SEASON)
                .findFirst()
                .orElse(null);

        RoundSpan currentQuarter = currentRound != null
                ? findContainingPhase(phases, currentRound.getPosition(), PhaseType.QUARTER)
                : null;

        RoundSpan currentSprint =
                currentRound != null ? findContainingPhase(phases, currentRound.getPosition(), PhaseType.SPRINT) : null;

        List<GeneralContestRowDto> rows = new ArrayList<>();
        Stream.of(fullSeason, currentQuarter, currentSprint)
                .filter(p -> p != null)
                .forEach(phase -> {
                    var rankInfo = contestRankResolver.resolve(
                            mainContestId, season.getId(), phase.getFrom(), phase.getTo(), userId);
                    rows.add(new GeneralContestRowDto(
                            phase.getCode(),
                            phase.getName(),
                            buildDateLabel(phase.getFrom(), phase.getTo(), dateRanges),
                            buildDateFrom(phase.getFrom(), dateRanges),
                            buildDateTo(phase.getTo(), dateRanges),
                            buildGwLabel(phase.getFrom(), phase.getTo()),
                            phase.getFrom(),
                            phase.getTo(),
                            rankInfo.position(),
                            rankInfo.movement()));
                });
        return rows;
    }

    private String buildDateLabel(int from, int to, Map<Integer, MatchRepo.RoundDateRange> dateRanges) {
        if (dateRanges == null) return null;
        MatchRepo.RoundDateRange startRange = dateRanges.get(from);
        MatchRepo.RoundDateRange endRange = dateRanges.get(to);
        if (startRange == null || endRange == null) return null;
        return startRange.firstKickoff().format(DATE_FMT) + " – "
                + endRange.lastKickoff().format(DATE_FMT);
    }

    private String buildDateFrom(int from, Map<Integer, MatchRepo.RoundDateRange> dateRanges) {
        if (dateRanges == null) return null;
        var r = dateRanges.get(from);
        return r != null ? r.firstKickoff().format(DATE_FMT) : null;
    }

    private String buildDateTo(int to, Map<Integer, MatchRepo.RoundDateRange> dateRanges) {
        if (dateRanges == null) return null;
        var r = dateRanges.get(to);
        return r != null ? r.lastKickoff().format(DATE_FMT) : null;
    }

    private String buildGwLabel(int from, int to) {
        return from + "–" + to;
    }

    private List<PrivateContestRowDto> buildPrivateRows(UUID userId, UUID seasonId) {
        List<Contest> contests = contestRepo.findPrivateByUserId(userId, seasonId);
        Map<Integer, MatchRepo.RoundDateRange> dateRanges =
                !contests.isEmpty() ? matchRepo.groupRoundDateRangesBySeason(seasonId) : null;
        Map<UUID, Integer> memberCounts =
                entryRepo.countActiveByContestIds(contests.stream().map(Contest::getId).toList());
        List<PrivateContestRowDto> rows = new ArrayList<>();
        for (Contest contest : contests) {
            int memberCount = memberCounts.getOrDefault(contest.getId(), 0);
            rows.add(new PrivateContestRowDto(
                    contest.getId(),
                    contest.getName(),
                    buildDateLabel(contest.getFromRoundPosition(), contest.getToRoundPosition(), dateRanges),
                    buildDateFrom(contest.getFromRoundPosition(), dateRanges),
                    buildDateTo(contest.getToRoundPosition(), dateRanges),
                    buildGwLabel(contest.getFromRoundPosition(), contest.getToRoundPosition()),
                    contest.getFromRoundPosition(),
                    contest.getToRoundPosition(),
                    memberCount,
                    contest.isOwnedBy(userId),
                    contest.isOpen()));
        }
        return rows;
    }

    private RoundSpan findContainingPhase(List<RoundSpan> phases, int roundPosition, PhaseType type) {
        return phases.stream()
                .filter(p -> p.getType() == type)
                .filter(p -> roundPosition >= p.getFrom() && roundPosition <= p.getTo())
                .findFirst()
                .orElse(null);
    }
}

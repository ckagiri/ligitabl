package com.ligitabl.api.rest.contest.getusercontestsummary;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
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
    private final LeaderboardRepo leaderboardRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;

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

        Map<Integer, MatchRepo.RoundDateRange> dateRanges =
                matchRepo.groupRoundDateRangesBySeason(season.getId());

        List<GeneralContestRowDto> generalRows =
                buildGeneralRows(competition, season, mainContest.getId(), query.userId(), currentRound, dateRanges);

        List<PrivateContestRowDto> privateRows =
                buildPrivateRows(query.userId());

        return new GetUserContestSummaryResult(generalRows, privateRows);
    }

    private List<GeneralContestRowDto> buildGeneralRows(
            Competition competition, Season season, UUID mainContestId, UUID userId, Round currentRound,
            Map<Integer, MatchRepo.RoundDateRange> dateRanges) {

        List<RoundSpan> phases = competition.getPhases();

        RoundSpan fullSeason = phases.stream()
                .filter(p -> p.getType() == PhaseType.FULL_SEASON)
                .findFirst()
                .orElse(null);

        RoundSpan currentQuarter = currentRound != null
                ? findContainingPhase(phases, currentRound.getPosition(), PhaseType.QUARTER)
                : null;

        RoundSpan currentSprint = currentRound != null
                ? findContainingPhase(phases, currentRound.getPosition(), PhaseType.SPRINT)
                : null;

        List<GeneralContestRowDto> rows = new ArrayList<>();
        Stream.of(fullSeason, currentQuarter, currentSprint)
                .filter(p -> p != null)
                .forEach(phase -> {
                    var response = leaderboardRepo.computeLeaderboard(
                            mainContestId, season.getId(), phase.getFrom(), phase.getTo(), userId, 0, 1, true);
                    LeaderboardEntry userEntry = response.userEntry();
                    Integer rank = userEntry != null ? userEntry.position() : null;
                    int movement = userEntry != null ? userEntry.movement() : 0;
                    rows.add(new GeneralContestRowDto(
                            phase.getCode(),
                            phase.getName(),
                            buildGwLabel(phase.getFrom(), phase.getTo(), dateRanges),
                            rank,
                            movement));
                });
        return rows;
    }

    private String buildGwLabel(int from, int to, Map<Integer, MatchRepo.RoundDateRange> dateRanges) {
        String gwPart = "GW " + from + "–" + to;
        MatchRepo.RoundDateRange startRange = dateRanges.get(from);
        MatchRepo.RoundDateRange endRange = dateRanges.get(to);
        if (startRange == null || endRange == null) return gwPart;
        String startDate = startRange.firstKickoff().format(DATE_FMT);
        String endDate = endRange.lastKickoff().format(DATE_FMT);
        return startDate + " – " + endDate + " · " + gwPart;
    }

    private List<PrivateContestRowDto> buildPrivateRows(UUID userId) {
        List<PrivateContestRowDto> rows = new ArrayList<>();
        for (Contest contest : contestRepo.findPrivateByUserId(userId)) {
            int memberCount = entryRepo.countActiveByContestId(contest.getId());
            String gwLabel = "GW " + contest.getFromRoundPosition() + "–" + contest.getToRoundPosition();
            rows.add(new PrivateContestRowDto(
                    contest.getId(), contest.getName(), gwLabel, memberCount, contest.isOwnedBy(userId)));
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

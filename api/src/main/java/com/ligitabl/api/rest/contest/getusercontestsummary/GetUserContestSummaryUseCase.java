package com.ligitabl.api.rest.contest.getusercontestsummary;

import java.util.ArrayList;
import java.util.List;
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
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetUserContestSummaryUseCase {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final LeaderboardRepo leaderboardRepo;
    private final RoundRepo roundRepo;

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

        List<GeneralContestRowDto> generalRows =
                buildGeneralRows(competition, season, mainContest.getId(), query.userId(), currentRound);

        List<PrivateContestRowDto> privateRows =
                buildPrivateRows(query.userId(), competition, currentRound);

        return new GetUserContestSummaryResult(generalRows, privateRows);
    }

    private List<GeneralContestRowDto> buildGeneralRows(
            Competition competition, Season season, UUID mainContestId, UUID userId, Round currentRound) {

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
                            "GW " + phase.getFrom() + "–" + phase.getTo(),
                            rank,
                            movement));
                });
        return rows;
    }

    private List<PrivateContestRowDto> buildPrivateRows(
            UUID userId, Competition competition, Round currentRound) {

        List<PrivateContestRowDto> rows = new ArrayList<>();
        for (Contest contest : contestRepo.findPrivateByUserId(userId)) {
            int memberCount = entryRepo.countActiveByContestId(contest.getId());
            String phaseLabel = currentRound != null
                    ? resolveCurrentPhaseLabel(contest, competition.getPhases(), currentRound.getPosition())
                    : "";
            rows.add(new PrivateContestRowDto(
                    contest.getId(), contest.getName(), phaseLabel, memberCount, contest.isOwnedBy(userId)));
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

    private String resolveCurrentPhaseLabel(Contest contest, List<RoundSpan> phases, int currentPosition) {
        RoundSpan match = phases.stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .filter(p -> currentPosition >= p.getFrom() && currentPosition <= p.getTo())
                .filter(p -> p.getFrom() >= contest.getFromRoundPosition()
                        && p.getTo() <= contest.getToRoundPosition())
                .findFirst()
                .orElse(null);

        if (match == null) {
            match = phases.stream()
                    .filter(p -> p.getType() == PhaseType.QUARTER)
                    .filter(p -> currentPosition >= p.getFrom() && currentPosition <= p.getTo())
                    .filter(p -> p.getFrom() >= contest.getFromRoundPosition()
                            && p.getTo() <= contest.getToRoundPosition())
                    .findFirst()
                    .orElse(null);
        }

        return match != null ? match.getName() : "";
    }
}

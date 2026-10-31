package com.ligitabl.api.usecase.contest.getusercontestsummary;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.ligitabl.api.usecase.contest.shared.ContestRankResolver;
import com.ligitabl.api.usecase.contest.shared.ContestSupport;
import com.ligitabl.api.usecase.contest.shared.RenewalOptions;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.PhaseRules;
import com.ligitabl.model.domain.league.PhaseType;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.RoundSpan;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.query.RoundDateRange;
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
    private final ContestSupport contestSupport;

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

        Map<Integer, RoundDateRange> dateRanges = matchRepo.groupRoundDateRangesBySeason(season.getId());

        List<GeneralContestRowDto> generalRows =
                buildGeneralRows(competition, season, mainContest.getId(), query.userId(), currentRound, dateRanges);

        List<PrivateContestRowDto> privateRows = buildPrivateRows(query.userId(), season, currentRound, competition);

        return new GetUserContestSummaryResult(generalRows, privateRows);
    }

    private List<GeneralContestRowDto> buildGeneralRows(
            Competition competition,
            Season season,
            UUID mainContestId,
            UUID userId,
            Round currentRound,
            Map<Integer, RoundDateRange> dateRanges) {

        List<RoundSpan> phases = competition.getPhases();

        RoundSpan fullSeason = phases.stream()
                .filter(p -> p.getType() == PhaseType.FULL_SEASON)
                .findFirst()
                .orElse(null);

        Integer effectivePosition = currentRound != null ? PhaseRules.effectivePosition(currentRound) : null;

        RoundSpan currentQuarter = effectivePosition != null
                ? PhaseRules.phaseOfTypeContaining(phases, PhaseType.QUARTER, effectivePosition)
                        .orElse(null)
                : null;

        RoundSpan currentSprint = effectivePosition != null
                ? PhaseRules.phaseOfTypeContaining(phases, PhaseType.SPRINT, effectivePosition)
                        .orElse(null)
                : null;

        List<GeneralContestRowDto> rows = new ArrayList<>();
        Stream.of(fullSeason, currentQuarter, currentSprint)
                .filter(p -> p != null)
                .forEach(phase -> {
                    var rankInfo = contestRankResolver.resolve(
                            mainContestId, season.getId(), phase.getFrom(), phase.getTo(), userId);
                    rows.add(GeneralContestRowDto.builder()
                            .phaseCode(phase.getCode())
                            .label(phase.getName())
                            .dateLabel(buildDateLabel(phase.getFrom(), phase.getTo(), dateRanges))
                            .dateFrom(buildDateFrom(phase.getFrom(), dateRanges))
                            .dateTo(buildDateTo(phase.getTo(), dateRanges))
                            .gwLabel(buildGwLabel(phase.getFrom(), phase.getTo()))
                            .gwFrom(phase.getFrom())
                            .gwTo(phase.getTo())
                            .rank(rankInfo.position())
                            .movement(rankInfo.movement())
                            .build());
                });
        return rows;
    }

    private String buildDateLabel(int from, int to, Map<Integer, RoundDateRange> dateRanges) {
        if (dateRanges == null) return null;
        RoundDateRange startRange = dateRanges.get(from);
        RoundDateRange endRange = dateRanges.get(to);
        if (startRange == null || endRange == null) return null;
        return startRange.firstKickoff().format(DATE_FMT) + " – "
                + endRange.lastKickoff().format(DATE_FMT);
    }

    private String buildDateFrom(int from, Map<Integer, RoundDateRange> dateRanges) {
        if (dateRanges == null) return null;
        var r = dateRanges.get(from);
        return r != null ? r.firstKickoff().format(DATE_FMT) : null;
    }

    private String buildDateTo(int to, Map<Integer, RoundDateRange> dateRanges) {
        if (dateRanges == null) return null;
        var r = dateRanges.get(to);
        return r != null ? r.lastKickoff().format(DATE_FMT) : null;
    }

    private String buildGwLabel(int from, int to) {
        return from + "–" + to;
    }

    private List<PrivateContestRowDto> buildPrivateRows(
            UUID userId, Season season, Round currentRound, Competition competition) {
        List<RoundSpan> phases = competition.getPhases();
        List<Contest> contests = contestRepo.findPrivateByUserId(userId, season.getId());
        Map<Integer, RoundDateRange> dateRanges =
                !contests.isEmpty() ? matchRepo.groupRoundDateRangesBySeason(season.getId()) : null;
        Map<UUID, Integer> memberCounts = entryRepo.countActiveByContestIds(
                contests.stream().map(Contest::getId).toList());
        List<PrivateContestRowDto> rows = new ArrayList<>();
        for (Contest contest : contests) {
            int memberCount = memberCounts.getOrDefault(contest.getId(), 0);
            String status = contestSupport.deriveContestStatus(
                    contest.getFromRoundPosition(), contest.getToRoundPosition(), currentRound, phases);
            rows.add(PrivateContestRowDto.builder()
                    .id(contest.getId())
                    .name(contest.getName())
                    .dateLabel(buildDateLabel(contest.getFromRoundPosition(), contest.getToRoundPosition(), dateRanges))
                    .dateFrom(buildDateFrom(contest.getFromRoundPosition(), dateRanges))
                    .dateTo(buildDateTo(contest.getToRoundPosition(), dateRanges))
                    .gwLabel(buildGwLabel(contest.getFromRoundPosition(), contest.getToRoundPosition()))
                    .gwFrom(contest.getFromRoundPosition())
                    .gwTo(contest.getToRoundPosition())
                    .memberCount(memberCount)
                    .isOwner(contest.isOwnedBy(userId))
                    .status(status)
                    .isOpenForJoining(contestSupport.isOpenForJoining(contest, season, competition))
                    .renewal(resolveRenewalOptions(contest, userId, currentRound, phases))
                    .build());
        }
        return rows;
    }

    // Every row here belongs to the active season (findPrivateByUserId is scoped to it), so the
    // past-season branch of GetContestRenewalOptionsUseCase never applies.
    private RenewalOptions resolveRenewalOptions(
            Contest contest, UUID userId, Round currentRound, List<RoundSpan> phases) {
        if (!contest.isOwnedBy(userId)) return RenewalOptions.hidden();
        return RenewalOptions.forCurrentSeason(
                contest, phases, currentRound != null ? currentRound.getPosition() : null);
    }
}

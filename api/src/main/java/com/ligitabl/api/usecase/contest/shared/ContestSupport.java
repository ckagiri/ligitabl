package com.ligitabl.api.usecase.contest.shared;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.usecase.round.shared.RoundSupport;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.contest.ContestJoinWindow;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.PhaseRules;
import com.ligitabl.model.domain.league.PhaseType;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.RoundSpan;
import com.ligitabl.model.domain.league.RoundStatus;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.query.RoundDateRange;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContestSupport {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final CompetitionDefaults competitionDefaults;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final MatchRepo matchRepo;
    private final RoundSupport roundSupport;

    @Builder
    public record SprintOption(
            String code,
            String name,
            int num,
            String status,
            String quarterCode,
            String quarterName,
            String startDate,
            String endDate,
            String gwLabel,
            boolean isQuarterStart,
            boolean isQuarterEnd) {}

    public record QuarterOption(String code, String name) {}

    /**
     * Open for new members only if the contest's {@code isOpen} toggle is on and its join window
     * hasn't closed. The same gate {@code JoinPrivateContestUseCase} enforces on a join.
     */
    public boolean isOpenForJoining(Contest contest, Season season, Competition competition) {
        if (!contest.isOpen()) return false;
        if (season.getCurrentRoundId() == null) return true;

        Round currentRound = roundSupport.resolveCurrentRound(season);
        if (currentRound == null) return true;

        return !ContestJoinWindow.isJoinWindowClosed(
                contest.getToRoundPosition(),
                currentRound,
                competition,
                () -> roundSupport.resolveStatus(currentRound));
    }

    /**
     * LIVE/FINISHED/NEXT/FUTURE for the contest's own window, sprint-aware for NEXT (see
     * {@link PhaseRules#deriveWindowStatus(int, int, int, List)}). FUTURE when there is no current
     * round yet. List-view badge only; the detail page uses
     * {@link #isOpenForJoining(Contest, Season, Competition)}, which also applies the last-sprint
     * opening-round lock.
     */
    public String deriveContestStatus(
            int fromRoundPosition, int toRoundPosition, Round currentRound, List<RoundSpan> phases) {
        if (currentRound == null) return "FUTURE";
        return PhaseRules.deriveWindowStatus(fromRoundPosition, toRoundPosition, currentRound.getPosition(), phases);
    }

    /**
     * The contest's current sprint: the sprint containing {@code currentPos}, or the nearest
     * upcoming/most recent one if the contest hasn't started or has finished.
     */
    public RoundSpan findCurrentSprint(Contest contest, Competition competition, int currentPos) {
        return findCurrentPhase(contest, competition, PhaseType.SPRINT, currentPos)
                .orElseThrow();
    }

    /**
     * The contest's current phase of {@code type}, chosen as {@link #findCurrentSprint} chooses a
     * sprint. Empty when no phase of that type lies wholly inside the contest window.
     */
    public Optional<RoundSpan> findCurrentPhase(
            Contest contest, Competition competition, PhaseType type, int currentPos) {
        List<RoundSpan> contestPhases = (competition != null && competition.getPhases() != null
                        ? competition.getPhases()
                        : List.<RoundSpan>of())
                .stream()
                        .filter(p -> p.getType() == type)
                        .filter(p -> p.getFrom() >= contest.getFromRoundPosition()
                                && p.getTo() <= contest.getToRoundPosition())
                        .sorted(Comparator.comparingInt(RoundSpan::getFrom))
                        .toList();
        if (contestPhases.isEmpty()) return Optional.empty();

        return contestPhases.stream()
                .filter(p -> currentPos >= p.getFrom() && currentPos <= p.getTo())
                .findFirst()
                .or(() -> currentPos < contest.getFromRoundPosition()
                        ? contestPhases.stream().findFirst()
                        : Optional.empty())
                .or(() -> Optional.of(contestPhases.getLast()));
    }

    public List<QuarterOption> resolveQuarterOptions() {
        String slug = competitionDefaults.defaultCompetitionSlug();
        var competition = competitionRepo.findBySlug(slug).orElse(null);
        if (competition == null || competition.getPhases() == null) return List.of();

        return competition.getPhases().stream()
                .filter(p -> p.getType() == PhaseType.QUARTER)
                .map(q -> new QuarterOption(q.getCode(), q.getName()))
                .toList();
    }

    public List<SprintOption> resolveSprintOptions() {
        String slug = competitionDefaults.defaultCompetitionSlug();
        var competition = competitionRepo.findBySlug(slug).orElse(null);
        if (competition == null || competition.getPhases() == null) return List.of();

        var season = seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        Map<Integer, RoundDateRange> dateRanges =
                season != null ? matchRepo.groupRoundDateRangesBySeason(season.getId()) : Map.of();

        int currentPos = roundSupport.resolveCurrentRoundPosition();
        Round currentRound = roundSupport.resolveCurrentRound();
        List<RoundSpan> phases = competition.getPhases();
        List<RoundSpan> quarters =
                phases.stream().filter(p -> p.getType() == PhaseType.QUARTER).toList();
        List<RoundSpan> sprints =
                phases.stream().filter(p -> p.getType() == PhaseType.SPRINT).toList();

        return IntStream.range(0, sprints.size())
                .mapToObj(i -> {
                    RoundSpan sprint = sprints.get(i);

                    String status;
                    if (sprint.getTo() < currentPos) {
                        status = "PAST";
                    } else if (sprint.getFrom() > currentPos) {
                        status = "FUTURE";
                    } else if (currentRound != null) {
                        RoundStatus rs = roundSupport.resolveStatus(currentRound);
                        // OPEN only at the sprint's first round; mid-sprint, a single-sprint
                        // contest starting here is already closed to joins.
                        status = (rs == RoundStatus.OPEN && currentPos == sprint.getFrom()) ? "OPEN" : "LOCKED";
                    } else {
                        status = "OPEN";
                    }

                    RoundSpan quarter = quarters.stream()
                            .filter(q -> q.getFrom() <= sprint.getFrom() && q.getTo() >= sprint.getTo())
                            .findFirst()
                            .orElse(null);

                    List<RoundSpan> sprintsInQuarter = quarter == null
                            ? List.of()
                            : sprints.stream()
                                    .filter(s -> quarter.getFrom() <= s.getFrom() && quarter.getTo() >= s.getTo())
                                    .toList();
                    boolean isQuarterStart = !sprintsInQuarter.isEmpty()
                            && sprintsInQuarter.get(0).equals(sprint);
                    boolean isQuarterEnd = !sprintsInQuarter.isEmpty()
                            && sprintsInQuarter.get(sprintsInQuarter.size() - 1).equals(sprint);

                    RoundDateRange startRange = dateRanges.get(sprint.getFrom());
                    RoundDateRange endRange = dateRanges.get(sprint.getTo());
                    String startDate =
                            startRange != null ? startRange.firstKickoff().format(DATE_FMT) : "";
                    String endDate = endRange != null ? endRange.lastKickoff().format(DATE_FMT) : "";

                    String gwLabel = sprint.getFrom() == sprint.getTo()
                            ? "GW " + sprint.getFrom()
                            : "GW " + sprint.getFrom() + "–" + sprint.getTo();

                    return SprintOption.builder()
                            .code(sprint.getCode())
                            .name(sprint.getName())
                            .num(i + 1)
                            .status(status)
                            .quarterCode(quarter != null ? quarter.getCode() : "")
                            .quarterName(quarter != null ? quarter.getName() : "")
                            .startDate(startDate)
                            .endDate(endDate)
                            .gwLabel(gwLabel)
                            .isQuarterStart(isQuarterStart)
                            .isQuarterEnd(isQuarterEnd)
                            .build();
                })
                .toList();
    }
}

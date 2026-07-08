package com.ligitabl.api.web.contest.shared;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;

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
     * A contest is actually open for new members only if its own {@code isOpen} toggle is on
     * <em>and</em> its join window hasn't closed (past its end, or past the opening of its own
     * last sprint). Mirrors the gate {@code JoinPrivateContestUseCase} enforces on an actual join.
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
     * Coarser "closed" rule for list views: just whether the current round has passed the
     * contest's last round. Unlike the full {@link #isOpenForJoining(Contest, Season, Competition)}
     * gate used on the detail page and actual joins, this intentionally ignores the last-sprint
     * opening-round lock check — good enough for a table badge, and needs no round-status lookup
     * at all (no match fetch), so callers don't need to resolve one for every row.
     */
    public boolean isOpenForJoining(boolean contestIsOpen, int toRoundPosition, Round currentRound) {
        if (!contestIsOpen) return false;
        if (currentRound == null) return true;

        return currentRound.getPosition() <= toRoundPosition;
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
        Map<Integer, MatchRepo.RoundDateRange> dateRanges =
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
                        // Only mark OPEN when we're exactly at the sprint's first round.
                        // Mid-sprint (pos > sprint.from) means the join window is already closed
                        // for any single-sprint contest starting here.
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

                    MatchRepo.RoundDateRange startRange = dateRanges.get(sprint.getFrom());
                    MatchRepo.RoundDateRange endRange = dateRanges.get(sprint.getTo());
                    String startDate =
                            startRange != null ? startRange.firstKickoff().format(DATE_FMT) : "";
                    String endDate = endRange != null ? endRange.lastKickoff().format(DATE_FMT) : "";

                    String gwLabel = sprint.getFrom() == sprint.getTo()
                            ? "GW " + sprint.getFrom()
                            : "GW " + sprint.getFrom() + "–" + sprint.getTo();

                    return new SprintOption(
                            sprint.getCode(),
                            sprint.getName(),
                            i + 1,
                            status,
                            quarter != null ? quarter.getCode() : "",
                            quarter != null ? quarter.getName() : "",
                            startDate,
                            endDate,
                            gwLabel,
                            isQuarterStart,
                            isQuarterEnd);
                })
                .toList();
    }
}

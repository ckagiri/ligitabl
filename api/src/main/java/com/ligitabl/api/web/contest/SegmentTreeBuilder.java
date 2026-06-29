package com.ligitabl.api.web.contest;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SegmentTreeBuilder {

    private final MatchRepo matchRepo;
    private final LeaderboardRepo leaderboardRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    /**
     * Builds a segment tree for the contest's round window.
     *
     * Single-sprint  → root IS the sprint (no children).
     * Single-quarter → root IS the quarter; sprints are direct children.
     * Multi-quarter  → root "Overall" → quarters → sprints.
     *
     * Rank is computed for at most 3 nodes: root, current quarter, current sprint.
     */
    public List<SegmentNodeDto> build(Contest contest, Competition competition, UUID userId, int currentPosition) {

        if (competition == null || competition.getPhases() == null) return List.of();

        Map<Integer, MatchRepo.RoundDateRange> dateRanges =
                matchRepo.groupRoundDateRangesBySeason(contest.getSeasonId());

        List<RoundSpan> phases = competition.getPhases();
        int from = contest.getFromRoundPosition();
        int to = contest.getToRoundPosition();

        List<RoundSpan> sprints = phases.stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .filter(p -> p.getFrom() >= from && p.getTo() <= to)
                .sorted(Comparator.comparingInt(RoundSpan::getFrom))
                .toList();

        if (sprints.isEmpty()) return List.of();

        if (sprints.size() == 1) {
            RoundSpan sprint = sprints.get(0);
            Integer rank = isLive(sprint, currentPosition)
                    ? computeRank(contest, sprint.getFrom(), sprint.getTo(), userId)
                    : null;
            return List.of(buildNode(
                    sprint.getCode(), sprint.getName(), sprint, currentPosition, dateRanges, rank, List.of()));
        }

        // QUARTER phases whose full range lies within the contest window
        List<RoundSpan> quarters = phases.stream()
                .filter(p -> p.getType() == PhaseType.QUARTER)
                .filter(p -> p.getFrom() >= from && p.getTo() <= to)
                .sorted(Comparator.comparingInt(RoundSpan::getFrom))
                .toList();

        // Compute current-sprint rank (used by both single-quarter and multi-quarter paths)
        RoundSpan currentSprintPhase = findContaining(phases, currentPosition, PhaseType.SPRINT);
        Map<String, Integer> sprintRanks = new HashMap<>();
        if (currentSprintPhase != null && currentSprintPhase.getFrom() >= from && currentSprintPhase.getTo() <= to) {
            Integer rank = computeRank(contest, currentSprintPhase.getFrom(), currentSprintPhase.getTo(), userId);
            sprintRanks.put(currentSprintPhase.getCode(), rank);
        }

        if (quarters.size() == 1) {
            // Single-quarter: root IS the quarter; sprints are direct children (at most 2 rank calls)
            RoundSpan quarter = quarters.get(0);
            Integer quarterRank = isLive(quarter, currentPosition)
                    ? computeRank(contest, quarter.getFrom(), quarter.getTo(), userId)
                    : null;
            List<SegmentNodeDto> sprintNodes = sprints.stream()
                    .map(s -> buildNode(
                            s.getCode(),
                            s.getName(),
                            s,
                            currentPosition,
                            dateRanges,
                            sprintRanks.get(s.getCode()),
                            List.of()))
                    .toList();
            return List.of(buildNode(
                    quarter.getCode(),
                    quarter.getName(),
                    quarter,
                    currentPosition,
                    dateRanges,
                    quarterRank,
                    sprintNodes));
        }

        // Multi-quarter: root "Overall" + at most 2 more rank calls
        RoundSpan contestWindow = RoundSpan.builder()
                .code("overall")
                .name("Overall")
                .from(from)
                .to(to)
                .build();
        Integer rootRank = isLive(contestWindow, currentPosition) ? computeRank(contest, from, to, userId) : null;

        RoundSpan currentQuarterPhase = findContaining(phases, currentPosition, PhaseType.QUARTER);
        Map<String, Integer> quarterRanks = new HashMap<>();
        if (currentQuarterPhase != null && currentQuarterPhase.getFrom() >= from && currentQuarterPhase.getTo() <= to) {
            Integer rank = computeRank(contest, currentQuarterPhase.getFrom(), currentQuarterPhase.getTo(), userId);
            quarterRanks.put(currentQuarterPhase.getCode(), rank);
        }

        List<SegmentNodeDto> quarterNodes = quarters.stream()
                .map(q -> {
                    List<SegmentNodeDto> sprintNodes = sprints.stream()
                            .filter(s -> s.getFrom() >= q.getFrom() && s.getTo() <= q.getTo())
                            .map(s -> buildNode(
                                    s.getCode(),
                                    s.getName(),
                                    s,
                                    currentPosition,
                                    dateRanges,
                                    sprintRanks.get(s.getCode()),
                                    List.of()))
                            .toList();
                    return buildNode(
                            q.getCode(),
                            q.getName(),
                            q,
                            currentPosition,
                            dateRanges,
                            quarterRanks.get(q.getCode()),
                            sprintNodes);
                })
                .toList();

        SegmentNodeDto root =
                buildNode("overall", "Overall", contestWindow, currentPosition, dateRanges, rootRank, quarterNodes);
        return List.of(root);
    }

    public String contestDateLabel(UUID seasonId, int from, int to) {
        Map<Integer, MatchRepo.RoundDateRange> dateRanges = matchRepo.groupRoundDateRangesBySeason(seasonId);
        MatchRepo.RoundDateRange startRange = dateRanges.get(from);
        MatchRepo.RoundDateRange endRange = dateRanges.get(to);
        if (startRange == null || endRange == null) return "";
        return format(startRange.firstKickoff()) + " – " + format(endRange.lastKickoff());
    }

    private SegmentNodeDto buildNode(
            String id,
            String label,
            RoundSpan span,
            int currentPosition,
            Map<Integer, MatchRepo.RoundDateRange> dateRanges,
            Integer rank,
            List<SegmentNodeDto> children) {
        String nodeType = children.isEmpty() ? "SPRINT" : (id.equals("overall") ? "OVERALL" : "QUARTER");
        return new SegmentNodeDto(
                id,
                label,
                gwLabel(span),
                dateLabel(span, dateRanges),
                deriveStatus(span, currentPosition),
                nodeType,
                rank,
                rank != null && rank <= 10,
                children);
    }

    private Integer computeRank(Contest contest, int from, int to, UUID userId) {
        var response = leaderboardRepo.computeLeaderboard(
                contest.getId(), contest.getSeasonId(), from, to, userId, 0, 1, true);
        if (response == null) return null;
        var entry = response.userEntry();
        return entry != null ? entry.position() : null;
    }

    private String deriveStatus(RoundSpan span, int currentPosition) {
        if (currentPosition >= span.getFrom() && currentPosition <= span.getTo()) return "LIVE";
        if (span.getTo() < currentPosition) return "FINISHED";
        if (span.getFrom() == currentPosition + 1) return "NEXT";
        return "FUTURE";
    }

    private boolean isLive(RoundSpan span, int currentPosition) {
        return currentPosition >= span.getFrom() && currentPosition <= span.getTo();
    }

    private String gwLabel(RoundSpan span) {
        if (span.getFrom() == span.getTo()) return "GW " + span.getFrom();
        return "GW " + span.getFrom() + "–" + span.getTo();
    }

    private String dateLabel(RoundSpan span, Map<Integer, MatchRepo.RoundDateRange> dateRanges) {
        MatchRepo.RoundDateRange startRange = dateRanges.get(span.getFrom());
        MatchRepo.RoundDateRange endRange = dateRanges.get(span.getTo());
        if (startRange == null || endRange == null) return "";
        return format(startRange.firstKickoff()) + " – " + format(endRange.lastKickoff());
    }

    private String format(OffsetDateTime dt) {
        return dt.format(DATE_FMT);
    }

    private RoundSpan findContaining(List<RoundSpan> phases, int position, PhaseType type) {
        return phases.stream()
                .filter(p -> p.getType() == type)
                .filter(p -> position >= p.getFrom() && position <= p.getTo())
                .findFirst()
                .orElse(null);
    }
}

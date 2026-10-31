package com.ligitabl.api.usecase.notification.segmentresults;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.usecase.notification.roundresults.EnqueueRoundResultsEmailsUseCase;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundAdvancedPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonCompletedPayload;
import com.ligitabl.api.usecase.shared.outbox.SegmentResultsPayload;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.identity.PublicId;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.PhaseRules;
import com.ligitabl.model.domain.league.PhaseType;
import com.ligitabl.model.domain.league.RoundSpan;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.query.LeaderboardEntry;
import com.ligitabl.model.query.LeaderboardResponse;
import com.ligitabl.model.repo.AppSettingRepo;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Expands a segment boundary into one SEGMENT_RESULTS event per podium finisher.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnqueueSegmentResultsEmailsUseCase {
    static final String IGNORE_LIST_SETTING_KEY = EnqueueRoundResultsEmailsUseCase.IGNORE_LIST_SETTING_KEY;

    /** computeLeaderboard rejects limits above 100, so boards are read in pages. */
    private static final int PAGE_SIZE = 100;

    static final String SEASON_SCOPE = "season";

    private final OutboxRepo outboxRepo;
    private final AppSettingRepo appSettingRepo;
    private final UserRepo userRepo;
    private final LeaderboardRepo leaderboardRepo;
    private final ContestRepo contestRepo;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SegmentResultsEmailProperties properties;

    /** A podium finisher: their board entry plus the account the email goes to. */
    private record Finisher(LeaderboardEntry entry, User user) {}

    /** One email's worth of work: the account, and every segment they placed in at this boundary. */
    private record Recipient(User user, List<SegmentResultsPayload.SegmentPlacement> placements) {}

    /** A closed segment plus the mailable podium of the board covering exactly its window. */
    private record ClosedSegment(RoundSpan span, List<Finisher> podium, int totalParticipants) {}

    /**
     * Fired for every advanced round; returns immediately unless the round is the last of a sprint
     * or a quarter. At rounds 9/19/29/38 both close at once and are reported in one email.
     */
    public void execute(RoundAdvancedPayload event) {
        int roundPosition = event.roundPosition();
        Context ctx = resolveContext(event.seasonId(), "round=" + roundPosition);
        if (ctx == null) {
            return;
        }

        List<RoundSpan> ended = new ArrayList<>();
        PhaseRules.sprintEndingAt(ctx.phases(), roundPosition).ifPresent(ended::add);
        ctx.phases().stream()
                .filter(p -> p.getType() == PhaseType.QUARTER && p.getTo() == roundPosition)
                .findFirst()
                .ifPresent(ended::add);

        if (ended.isEmpty()) {
            // The common case by a wide margin — most rounds sit mid-sprint.
            log.debug("[SEGMENT_RESULTS_SKIPPED] round={}: closes no segment", roundPosition);
            return;
        }

        enqueue(ctx, ended, "r" + roundPosition, roundPosition, properties.getDelay());
    }

    /**
     * The season finale, and only the season: the full-season span is deliberately <em>not</em>
     * considered by {@link #execute(RoundAdvancedPayload)}, so advancing the last round closes its sprint and
     * quarter and nothing more. Completing the season is a separate admin action taken afterwards,
     * and it produces an email carrying the season podium alone.
     */
    public void execute(SeasonCompletedPayload event) {
        Context ctx = resolveContext(event.seasonId(), "season-completed");
        if (ctx == null) {
            return;
        }

        RoundSpan fullSeason = ctx.phases().stream()
                .filter(p -> p.getType() == PhaseType.FULL_SEASON)
                .findFirst()
                .orElse(null);
        if (fullSeason == null) {
            log.warn("[SEGMENT_RESULTS_SKIPPED] season-completed: no FULL_SEASON phase configured");
            return;
        }

        enqueue(ctx, List.of(fullSeason), SEASON_SCOPE, fullSeason.getTo(), properties.getSeasonDelay());
    }

    private void enqueue(Context ctx, List<RoundSpan> ended, String scopeKey, int boundaryRound, Duration delay) {
        Set<String> ignoreList = loadIgnoreList();

        List<ClosedSegment> segments = ended.stream()
                .map(span -> closedSegment(ctx, span, ignoreList))
                .filter(s -> !s.podium().isEmpty())
                .toList();

        if (segments.isEmpty()) {
            log.info("[SEGMENT_RESULTS_ENQUEUED] scope={}, recipients=0, mode={}", scopeKey, properties.getMode());
            return;
        }

        // LinkedHashMap so events follow board order rather than hash order.
        Map<UUID, Recipient> byUserId = new LinkedHashMap<>();
        for (ClosedSegment segment : segments) {
            for (Finisher finisher : segment.podium()) {
                byUserId.computeIfAbsent(
                                finisher.user().getId(), k -> new Recipient(finisher.user(), new ArrayList<>()))
                        .placements()
                        .add(placement(segment, finisher.entry()));
            }
        }

        int inserted = 0;
        for (Recipient recipient : byUserId.values()) {
            try {
                if (writeEvent(ctx, recipient.user(), recipient.placements(), scopeKey, boundaryRound, delay)) {
                    inserted++;
                }
            } catch (Exception e) {
                // One bad payload must not cost the other finishers their email.
                log.error(
                        "[SEGMENT_RESULTS_ENQUEUE_USER_FAILED] scope={}, userId={}: {}",
                        scopeKey,
                        recipient.user().getId(),
                        e.getMessage(),
                        e);
            }
        }

        log.info(
                "[SEGMENT_RESULTS_ENQUEUED] scope={}, segments={}, recipients={}, inserted={}, mode={}",
                scopeKey,
                segments.stream().map(s -> s.span().getCode()).toList(),
                byUserId.size(),
                inserted,
                properties.getMode());
    }

    /**
     * The first {@code topN} entries by position, then filtered for mailability — <b>not</b>
     * filtered-then-truncated.
     */
    private ClosedSegment closedSegment(Context ctx, RoundSpan span, Set<String> ignoreList) {
        Board board = fetchBoard(ctx, span, properties.getTopN());

        List<Finisher> podium = new ArrayList<>();
        for (LeaderboardEntry entry : board.entries()) {
            User user =
                    userRepo.findByPublicId(PublicId.create(entry.publicId())).orElse(null);
            if (user == null || user.getEmail() == null) {
                continue;
            }
            boolean ignored = ignoreList.contains(user.getEmail().value().toLowerCase(Locale.ROOT));
            if (properties.isTestMode()) {
                if (!ignored) {
                    continue;
                }
            } else if (ignored || !user.isEmailVerified() || user.isResultsEmailOptOut()) {
                continue;
            }
            podium.add(new Finisher(entry, user));
        }
        return new ClosedSegment(span, podium, board.totalParticipants());
    }

    private SegmentResultsPayload.SegmentPlacement placement(ClosedSegment segment, LeaderboardEntry entry) {
        RoundSpan span = segment.span();
        return SegmentResultsPayload.SegmentPlacement.builder()
                .type(span.getType().name())
                .code(span.getCode())
                .name(span.getName())
                .fromRound(span.getFrom())
                .toRound(span.getTo())
                .rank(entry.position())
                .totalParticipants(segment.totalParticipants())
                .totalScore(entry.totalScore())
                .build();
    }

    private boolean writeEvent(
            Context ctx,
            User user,
            List<SegmentResultsPayload.SegmentPlacement> placements,
            String scopeKey,
            int boundaryRound,
            Duration delay)
            throws Exception {
        // Sprint before quarter before season, so the template reads smallest-window first and the
        // headline (largest) is simply the last one.
        List<SegmentResultsPayload.SegmentPlacement> ordered = placements.stream()
                .sorted(Comparator.comparingInt(p -> p.toRound() - p.fromRound()))
                .toList();

        SegmentResultsPayload payload = SegmentResultsPayload.builder()
                .userId(user.getId())
                .userEmail(user.getEmail().value())
                .userDisplayName(user.getDisplayName())
                .userPublicId(user.getPublicId().value())
                .seasonSlug(ctx.season().getSlug().toShorthand())
                .scopeKey(scopeKey)
                .boundaryRound(boundaryRound)
                .placements(ordered)
                .build();

        OutboxEvent event = OutboxEvent.createAvailableAt(
                "segment-results:%s:%s:%s".formatted(ctx.season().getId(), scopeKey, user.getId()),
                OutboxEventTypes.SEGMENT_RESULTS,
                "user",
                user.getId().toString(),
                objectMapper.writeValueAsString(payload),
                clock.instant().plus(delay));
        return outboxRepo.save(event);
    }

    private record Context(Season season, Contest contest, List<RoundSpan> phases) {}

    private Context resolveContext(UUID seasonId, String scopeLabel) {
        Season season = seasonRepo.findById(seasonId).orElse(null);
        if (season == null) {
            log.warn("[SEGMENT_RESULTS_SKIPPED] {}: season {} not found", scopeLabel, seasonId);
            return null;
        }
        if (season.getMainContestId() == null) {
            log.warn("[SEGMENT_RESULTS_SKIPPED] {}: season has no main contest", scopeLabel);
            return null;
        }
        Contest contest = contestRepo.findById(season.getMainContestId()).orElse(null);
        if (contest == null) {
            log.warn("[SEGMENT_RESULTS_SKIPPED] {}: main contest not found", scopeLabel);
            return null;
        }
        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null
                || competition.getPhases() == null
                || competition.getPhases().isEmpty()) {
            log.warn("[SEGMENT_RESULTS_SKIPPED] {}: no competition phases configured", scopeLabel);
            return null;
        }
        return new Context(season, contest, competition.getPhases());
    }

    private record Board(List<LeaderboardEntry> entries, int totalParticipants) {}

    /**
     * The top {@code maxEntries} of the board for exactly this segment's window, plus the field
     * size the email quotes ("2 of 41"). A single page covers the podium at any sane {@code topN};
     * the loop exists only so a misconfigured one still works.
     */
    private Board fetchBoard(Context ctx, RoundSpan span, int maxEntries) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        int totalParticipants = 0;
        int offset = 0;
        while (entries.size() < maxEntries) {
            LeaderboardResponse page = leaderboardRepo.computeLeaderboard(
                    ctx.contest().getId(),
                    ctx.season().getId(),
                    span.getFrom(),
                    span.getTo(),
                    null,
                    offset,
                    PAGE_SIZE,
                    true);
            entries.addAll(page.entries());
            totalParticipants = page.totalParticipants();
            if (!page.hasNext() || page.entries().isEmpty()) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return new Board(
                entries.size() > maxEntries ? List.copyOf(entries.subList(0, maxEntries)) : entries, totalParticipants);
    }

    private Set<String> loadIgnoreList() {
        return appSettingRepo
                .findValue(IGNORE_LIST_SETTING_KEY)
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }
}

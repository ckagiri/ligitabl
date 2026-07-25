package com.ligitabl.api.notification.outbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.AppSettingRepo;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Recipients are the top {@code topN} of the main contest's sprint
 * leaderboard for the sprint containing the finalized round — recipient
 * selection stays sprint-scoped. Ignore-list accounts (test users),
 * unverified emails and opted-out users are skipped — the next-ranked user
 * takes the freed slot. In {@code test} mode the rule inverts: only
 * ignore-list accounts receive, so the pipeline can run locally against test
 * users. Both modes scan the same {@code topN + ignoreList.size()} window —
 * a test account outside it simply gets no email.
 *
 * <p>The email shows a "best"/movement callout for both sprint and quarter
 * (rank, movement, best-of-phase) — unlike the in-app results banner, which
 * shows sprint only. Full-season appears as a plain secondary standing (rank
 * only, no "season best" concept).
 *
 * <p>Duplicate fan-outs (relay retry, refinalization) are no-ops via the
 * per-user idempotency keys. Missing contest or phase configuration logs and
 * no-ops rather than failing the event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoundResultsEmailEnqueuer {

    static final String IGNORE_LIST_SETTING_KEY = "round_results_email_ignore_list";

    /** computeLeaderboard rejects limits above 100, so boards are read in pages. */
    private static final int PAGE_SIZE = 100;

    /** Upper bound on rows scanned per quarter/season board for placement lookups. */
    private static final int SCAN_CAP = 2_000;

    private final OutboxRepo outboxRepo;
    private final AppSettingRepo appSettingRepo;
    private final UserRepo userRepo;
    private final LeaderboardRepo leaderboardRepo;
    private final ContestRepo contestRepo;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundResultRepo roundResultRepo;
    private final ObjectMapper objectMapper;
    private final RoundResultsEmailProperties properties;

    private record Recipient(User user, LeaderboardEntry sprintEntry, RoundResult result) {}

    public void enqueue(RoundAdvancedPayload event) {
        int roundPosition = event.roundPosition();

        Season season = seasonRepo.findById(event.seasonId()).orElse(null);
        if (season == null) {
            log.warn("[ROUND_RESULTS_ENQUEUE_SKIPPED] round={}: season {} not found", roundPosition, event.seasonId());
            return;
        }
        if (season.getMainContestId() == null) {
            log.warn("[ROUND_RESULTS_ENQUEUE_SKIPPED] round={}: season has no main contest", roundPosition);
            return;
        }
        Contest contest = contestRepo.findById(season.getMainContestId()).orElse(null);
        if (contest == null) {
            log.warn("[ROUND_RESULTS_ENQUEUE_SKIPPED] round={}: main contest not found", roundPosition);
            return;
        }
        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null
                || competition.getPhases() == null
                || competition.getPhases().isEmpty()) {
            log.warn("[ROUND_RESULTS_ENQUEUE_SKIPPED] round={}: no competition phases configured", roundPosition);
            return;
        }
        RoundSpan sprint = PhaseRules.sprintContaining(competition.getPhases(), roundPosition)
                .orElse(null);
        if (sprint == null) {
            log.warn("[ROUND_RESULTS_ENQUEUE_SKIPPED] round={}: round not assigned to any sprint", roundPosition);
            return;
        }

        Set<String> ignoreList = loadIgnoreList();
        boolean testMode = properties.isTestMode();
        int topN = properties.getTopN();

        // Same scan window in both modes: test accounts outside the top-N window
        // simply get no email — they're expected to rank inside it.
        int maxEntries = topN + ignoreList.size();
        Board sprintBoard = fetchBoard(contest, season, sprint.getFrom(), roundPosition, maxEntries);

        List<Recipient> recipients = selectRecipients(sprintBoard.entries(), roundPosition, ignoreList, testMode, topN);
        if (recipients.isEmpty()) {
            log.info("[ROUND_RESULTS_ENQUEUED] round={}, recipients=0, mode={}", roundPosition, properties.getMode());
            return;
        }

        RoundSpan quarter = PhaseRules.phaseOfTypeContaining(competition.getPhases(), PhaseType.QUARTER, roundPosition)
                .orElse(null);
        RoundSpan fullSeason = PhaseRules.phaseOfTypeContaining(
                        competition.getPhases(), PhaseType.FULL_SEASON, roundPosition)
                .orElse(null);

        // Each level is suppressed independently, against the level directly above it, when
        // that wider span started at the same round as the narrower one — meaning its
        // standings-so-far would just repeat the narrower one's:
        //   - quarter is dropped while we're in its first sprint (quarter == this sprint's start)
        //   - season is dropped while we're in its first quarter (season == this quarter's start),
        //     regardless of which sprint within that quarter we're currently in.
        // No FULL_SEASON phase configured ⇒ season implicitly starts at round 1.
        boolean quarterRedundantWithSprint = quarter != null && PhaseRules.sameStart(sprint, quarter);
        RoundSpan effectiveQuarter = quarterRedundantWithSprint ? null : quarter;

        boolean seasonRedundantWithQuarter = quarter != null
                && (fullSeason != null ? PhaseRules.sameStart(quarter, fullSeason) : quarter.getFrom() == 1);

        Board quarterBoard = effectiveQuarter == null
                ? null
                : fetchBoard(contest, season, effectiveQuarter.getFrom(), roundPosition, SCAN_CAP);
        Map<String, LeaderboardEntry> quarterByPublicId =
                quarterBoard == null ? Map.of() : entryByPublicId(quarterBoard);
        int quarterTotalParticipants = quarterBoard == null ? 0 : quarterBoard.totalParticipants();

        PlacementBoard seasonBoard = seasonRedundantWithQuarter
                ? null
                : fullSeason == null
                        ? placementBoard(contest, season, "Season", 1, roundPosition)
                        : placementBoard(contest, season, fullSeason.getCode(), fullSeason.getFrom(), roundPosition);

        int inserted = 0;
        for (Recipient recipient : recipients) {
            try {
                RoundResultsPayload payload = buildPayload(
                        recipient,
                        roundPosition,
                        event.currentRoundPosition(),
                        season.getMaxRounds(),
                        sprint,
                        sprintBoard.totalParticipants(),
                        effectiveQuarter,
                        quarterByPublicId,
                        quarterTotalParticipants,
                        seasonBoard);
                String json = objectMapper.writeValueAsString(payload);
                String idempotencyKey = "round-results:%s:%d:%s"
                        .formatted(
                                season.getId(), roundPosition, recipient.user().getId());
                OutboxEvent outboxEvent = OutboxEvent.create(
                        idempotencyKey, OutboxEventTypes.ROUND_RESULTS, "round", String.valueOf(roundPosition), json);
                if (outboxRepo.save(outboxEvent)) {
                    inserted++;
                }
            } catch (Exception e) {
                // One bad payload must not cost the other recipients their email.
                log.error(
                        "[ROUND_RESULTS_ENQUEUE_USER_FAILED] round={}, userId={}: {}",
                        roundPosition,
                        recipient.user().getId(),
                        e.getMessage(),
                        e);
            }
        }

        log.info(
                "[ROUND_RESULTS_ENQUEUED] round={}, recipients={}, inserted={}, mode={}",
                roundPosition,
                recipients.size(),
                inserted,
                properties.getMode());
    }

    private List<Recipient> selectRecipients(
            List<LeaderboardEntry> sprintEntries,
            int roundPosition,
            Set<String> ignoreList,
            boolean testMode,
            int topN) {
        List<Recipient> recipients = new ArrayList<>();
        for (LeaderboardEntry entry : sprintEntries) {
            if (recipients.size() >= topN) {
                break;
            }
            User user =
                    userRepo.findByPublicId(PublicId.create(entry.publicId())).orElse(null);
            if (user == null || user.getEmail() == null) {
                continue;
            }
            String email = user.getEmail().value().toLowerCase(Locale.ROOT);
            boolean ignored = ignoreList.contains(email);
            if (testMode) {
                if (!ignored) {
                    continue;
                }
            } else if (ignored || !user.isEmailVerified() || user.isResultsEmailOptOut()) {
                continue;
            }
            RoundResult result = roundResultRepo
                    .findByUserAndRound(user.getId(), roundPosition)
                    .orElse(null);
            if (result == null) {
                continue;
            }
            recipients.add(new Recipient(user, entry, result));
        }
        return recipients;
    }

    private RoundResultsPayload buildPayload(
            Recipient recipient,
            int roundPosition,
            int currentRoundPosition,
            int lastRound,
            RoundSpan sprint,
            int sprintTotalParticipants,
            RoundSpan quarter,
            Map<String, LeaderboardEntry> quarterByPublicId,
            int quarterTotalParticipants,
            PlacementBoard seasonBoard) {
        User user = recipient.user();
        LeaderboardEntry sprintEntry = recipient.sprintEntry();
        int score = recipient.result().getTotalScore();

        // Already fetched for recipient selection — no extra query needed.
        boolean isNewSprintBest = score == sprintEntry.maxScore() && roundPosition > sprint.getFrom();
        RoundResultsPayload.SprintPlacement sprintPlacement = new RoundResultsPayload.SprintPlacement(
                sprint.getCode(),
                sprint.getFrom(),
                sprint.getTo(),
                sprintEntry.position(),
                sprintTotalParticipants,
                sprintEntry.movement(),
                sprintEntry.maxScore(),
                isNewSprintBest);

        RoundResultsPayload.QuarterPlacement quarterPlacement = null;
        if (quarter != null) {
            LeaderboardEntry quarterEntry = quarterByPublicId.get(sprintEntry.publicId());
            if (quarterEntry != null) {
                boolean isNewQuarterBest = score == quarterEntry.maxScore() && roundPosition > quarter.getFrom();
                quarterPlacement = new RoundResultsPayload.QuarterPlacement(
                        quarter.getCode(),
                        quarter.getFrom(),
                        quarter.getTo(),
                        quarterEntry.position(),
                        quarterTotalParticipants,
                        quarterEntry.movement(),
                        quarterEntry.maxScore(),
                        isNewQuarterBest);
            }
        }

        return new RoundResultsPayload(
                user.getId(),
                user.getEmail().value(),
                user.getDisplayName(),
                roundPosition,
                score,
                currentRoundPosition,
                lastRound,
                recipient.result().hitDistribution(),
                sprintPlacement,
                quarterPlacement,
                seasonBoard == null ? null : seasonBoard.placementOf(sprintEntry.publicId()));
    }

    private record PlacementBoard(String label, Map<String, Integer> rankByPublicId, int totalParticipants) {
        RoundResultsPayload.Placement placementOf(String publicId) {
            Integer rank = rankByPublicId.get(publicId);
            return rank == null ? null : new RoundResultsPayload.Placement(label, rank, totalParticipants);
        }
    }

    private PlacementBoard placementBoard(Contest contest, Season season, String label, int fromRound, int toRound) {
        Board board = fetchBoard(contest, season, fromRound, toRound, SCAN_CAP);
        Map<String, Integer> rankByPublicId = board.entries().stream()
                .collect(Collectors.toMap(
                        LeaderboardEntry::publicId, LeaderboardEntry::position, (a, b) -> a, HashMap::new));
        return new PlacementBoard(label, rankByPublicId, board.totalParticipants());
    }

    private record Board(List<LeaderboardEntry> entries, int totalParticipants) {}

    private Map<String, LeaderboardEntry> entryByPublicId(Board board) {
        return board.entries().stream()
                .collect(Collectors.toMap(LeaderboardEntry::publicId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private Board fetchBoard(Contest contest, Season season, int fromRound, int toRound, int maxEntries) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        int totalParticipants = 0;
        int offset = 0;
        while (entries.size() < maxEntries) {
            LeaderboardResponse page = leaderboardRepo.computeLeaderboard(
                    contest.getId(), season.getId(), fromRound, toRound, null, offset, PAGE_SIZE, true);
            entries.addAll(page.entries());
            totalParticipants = page.totalParticipants();
            if (!page.hasNext() || page.entries().isEmpty()) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return new Board(entries, totalParticipants);
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

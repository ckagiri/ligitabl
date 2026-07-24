package com.ligitabl.api.rest.round.finalizeround;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundResultsEmailProperties;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.AppSettingRepo;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes one ROUND_RESULTS outbox event per email recipient when a round is
 * finalized, inside the finalization transaction.
 *
 * <p>Recipients are the top {@code topN} of the main contest's sprint
 * leaderboard for the sprint containing the finalized round. Ignore-list
 * accounts (test users), unverified emails and opted-out users are skipped —
 * the next-ranked user takes the freed slot. In {@code test} mode the rule
 * inverts: only ignore-list accounts receive, so the pipeline can run locally
 * against test users.
 *
 * <p>Duplicate enqueues (e.g. refinalization) are no-ops via the outbox
 * idempotency key. Every early return here is deliberate: missing contest or
 * phase configuration must never fail round finalization.
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
    private final ObjectMapper objectMapper;
    private final RoundResultsEmailProperties properties;

    private record Recipient(User user, LeaderboardEntry sprintEntry, RoundResult result) {}

    public void enqueue(
            Season season,
            Round round,
            int currentRoundPosition,
            List<RoundSubmission> submissions,
            List<RoundResult> results) {
        int roundPosition = round.getPosition();

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

        Map<UUID, RoundResult> resultsByUserId = resultsByUserId(submissions, results);
        Set<String> ignoreList = loadIgnoreList();
        boolean testMode = properties.isTestMode();
        int topN = properties.getTopN();

        // Same scan window in both modes: test accounts outside the top-N window
        // simply get no email — they're expected to rank inside it.
        int maxEntries = topN + ignoreList.size();
        Board sprintBoard = fetchBoard(contest, season, sprint.getFrom(), roundPosition, maxEntries);

        List<Recipient> recipients =
                selectRecipients(sprintBoard.entries(), resultsByUserId, ignoreList, testMode, topN);
        if (recipients.isEmpty()) {
            log.info("[ROUND_RESULTS_ENQUEUED] round={}, recipients=0, mode={}", roundPosition, properties.getMode());
            return;
        }

        RoundSpan quarter = PhaseRules.phaseOfTypeContaining(
                        competition.getPhases(), PhaseType.QUARTER, roundPosition)
                .orElse(null);
        RoundSpan fullSeason = PhaseRules.phaseOfTypeContaining(
                        competition.getPhases(), PhaseType.FULL_SEASON, roundPosition)
                .orElse(null);

        PlacementBoard quarterBoard = quarter == null
                ? null
                : placementBoard(contest, season, quarter.getCode(), quarter.getFrom(), roundPosition);
        PlacementBoard seasonBoard = fullSeason == null
                ? placementBoard(contest, season, "Season", 1, roundPosition)
                : placementBoard(contest, season, fullSeason.getCode(), fullSeason.getFrom(), roundPosition);

        int inserted = 0;
        for (Recipient recipient : recipients) {
            try {
                RoundResultsPayload payload = buildPayload(
                        recipient,
                        roundPosition,
                        currentRoundPosition,
                        season.getMaxRounds(),
                        sprint,
                        sprintBoard.totalParticipants(),
                        quarterBoard,
                        seasonBoard);
                String json = objectMapper.writeValueAsString(payload);
                String idempotencyKey = "round-results:%s:%d:%s"
                        .formatted(season.getId(), roundPosition, recipient.user().getId());
                OutboxEvent event = OutboxEvent.create(
                        idempotencyKey,
                        OutboxEventTypes.ROUND_RESULTS,
                        "round",
                        String.valueOf(roundPosition),
                        json);
                if (outboxRepo.save(event)) {
                    inserted++;
                }
            } catch (Exception e) {
                // One bad payload must not cost the other recipients their email,
                // and never the finalization itself.
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
            Map<UUID, RoundResult> resultsByUserId,
            Set<String> ignoreList,
            boolean testMode,
            int topN) {
        List<Recipient> recipients = new ArrayList<>();
        for (LeaderboardEntry entry : sprintEntries) {
            if (recipients.size() >= topN) {
                break;
            }
            User user = userRepo.findByPublicId(PublicId.create(entry.publicId())).orElse(null);
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
            RoundResult result = resultsByUserId.get(user.getId());
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
            PlacementBoard quarterBoard,
            PlacementBoard seasonBoard) {
        User user = recipient.user();
        LeaderboardEntry entry = recipient.sprintEntry();
        int score = recipient.result().getTotalScore();

        boolean isNewSprintBest = score == entry.maxScore() && roundPosition > sprint.getFrom();
        RoundResultsPayload.SprintPlacement sprintPlacement = new RoundResultsPayload.SprintPlacement(
                sprint.getCode(),
                sprint.getFrom(),
                sprint.getTo(),
                entry.position(),
                sprintTotalParticipants,
                entry.movement(),
                entry.maxScore(),
                isNewSprintBest);

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
                quarterBoard == null ? null : quarterBoard.placementOf(entry.publicId()),
                seasonBoard == null ? null : seasonBoard.placementOf(entry.publicId()));
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

    private Map<UUID, RoundResult> resultsByUserId(List<RoundSubmission> submissions, List<RoundResult> results) {
        Map<UUID, UUID> userBySubmissionId =
                submissions.stream().collect(Collectors.toMap(RoundSubmission::getId, RoundSubmission::getUserId));
        return results.stream()
                .filter(r -> userBySubmissionId.containsKey(r.getRoundSubmissionId()))
                .collect(Collectors.toMap(
                        r -> userBySubmissionId.get(r.getRoundSubmissionId()), Function.identity(), (a, b) -> a));
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

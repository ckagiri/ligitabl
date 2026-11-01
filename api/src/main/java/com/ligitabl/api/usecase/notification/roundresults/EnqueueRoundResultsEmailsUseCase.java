package com.ligitabl.api.usecase.notification.roundresults;

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
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundAdvancedPayload;
import com.ligitabl.api.usecase.shared.outbox.RoundResultsPayload;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.identity.PublicId;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.PhaseRules;
import com.ligitabl.model.domain.league.PhaseType;
import com.ligitabl.model.domain.league.RoundSpan;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.prediction.RoundResult;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.query.LeaderboardEntry;
import com.ligitabl.model.query.LeaderboardResponse;
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
 * <p>Recipients are the top {@code topN} of the main contest's leaderboard for the sprint holding
 * the finalized round. Ignore-list (test) accounts, unverified emails and opted-out users are
 * skipped, and the next-ranked user takes the slot. {@code test} mode inverts this: only ignore-list
 * accounts receive. Both scan the same {@code topN + ignoreList.size()} window.
 *
 * <p>Duplicate fan-outs (relay retry, refinalization) are no-ops via the per-user idempotency keys.
 * Missing contest or phase configuration logs and no-ops rather than failing the event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnqueueRoundResultsEmailsUseCase {

    public static final String IGNORE_LIST_SETTING_KEY = "round_results_email_ignore_list";

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

    public void execute(RoundAdvancedPayload event) {
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

        // "Best" callouts compare against the max achieved strictly before this round, so a
        // round that only ties an earlier best doesn't re-trigger a "new best" callout.
        Board sprintBoardPrevious = roundPosition > sprint.getFrom()
                ? fetchBoard(contest, season, sprint.getFrom(), roundPosition - 1, maxEntries)
                : null;
        Map<String, LeaderboardEntry> sprintPreviousByPublicId =
                sprintBoardPrevious == null ? Map.of() : entryByPublicId(sprintBoardPrevious);

        RoundSpan quarter = PhaseRules.phaseOfTypeContaining(competition.getPhases(), PhaseType.QUARTER, roundPosition)
                .orElse(null);
        int seasonFrom = 1;
        boolean seasonRedundantWithSprint = sprint.getFrom() == seasonFrom;

        Board seasonBoard =
                seasonRedundantWithSprint ? null : fetchBoard(contest, season, seasonFrom, roundPosition, SCAN_CAP);
        Map<String, LeaderboardEntry> seasonByPublicId = seasonBoard == null ? Map.of() : entryByPublicId(seasonBoard);
        int seasonTotalParticipants = seasonBoard == null ? 0 : seasonBoard.totalParticipants();

        Board seasonBoardPrevious = (seasonRedundantWithSprint || roundPosition <= seasonFrom)
                ? null
                : fetchBoard(contest, season, seasonFrom, roundPosition - 1, SCAN_CAP);
        Map<String, LeaderboardEntry> seasonPreviousByPublicId =
                seasonBoardPrevious == null ? Map.of() : entryByPublicId(seasonBoardPrevious);

        // Quarter is always a plain secondary standing (rank only)
        PlacementBoard quarterPlacementBoard = quarter == null
                ? null
                : placementBoard(contest, season, quarter.getCode(), quarter.getFrom(), roundPosition);

        int inserted = 0;
        for (Recipient recipient : recipients) {
            try {
                RoundResultsPayload payload = buildPayload(
                        recipient,
                        season.getSlug().toShorthand(),
                        roundPosition,
                        event.currentRoundPosition(),
                        season.getMaxRounds(),
                        sprint,
                        sprintBoard.totalParticipants(),
                        sprintPreviousByPublicId,
                        seasonFrom,
                        seasonByPublicId,
                        seasonTotalParticipants,
                        seasonPreviousByPublicId,
                        quarterPlacementBoard);
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
            String seasonSlug,
            int roundPosition,
            int currentRoundPosition,
            int lastRound,
            RoundSpan sprint,
            int sprintTotalParticipants,
            Map<String, LeaderboardEntry> sprintPreviousByPublicId,
            int seasonFrom,
            Map<String, LeaderboardEntry> seasonByPublicId,
            int seasonTotalParticipants,
            Map<String, LeaderboardEntry> seasonPreviousByPublicId,
            PlacementBoard quarterPlacementBoard) {
        User user = recipient.user();
        LeaderboardEntry sprintEntry = recipient.sprintEntry();
        int score = recipient.result().getTotalScore();

        LeaderboardEntry sprintPrevious = sprintPreviousByPublicId.get(sprintEntry.publicId());
        int previousSprintBest = sprintPrevious != null ? sprintPrevious.maxScore() : 0;
        boolean isNewSprintBest = roundPosition > sprint.getFrom() && score > previousSprintBest;
        RoundResultsPayload.SprintPlacement sprintPlacement = RoundResultsPayload.SprintPlacement.builder()
                .label(sprint.getCode())
                .fromRound(sprint.getFrom())
                .toRound(sprint.getTo())
                .rank(sprintEntry.position())
                .totalParticipants(sprintTotalParticipants)
                .movement(sprintEntry.movement())
                .sprintBest(sprintEntry.maxScore())
                .isNewSprintBest(isNewSprintBest)
                .build();

        RoundResultsPayload.SeasonPlacement seasonPlacement = null;
        LeaderboardEntry seasonEntry = seasonByPublicId.get(sprintEntry.publicId());
        if (seasonEntry != null) {
            LeaderboardEntry seasonPrevious = seasonPreviousByPublicId.get(sprintEntry.publicId());
            int previousSeasonBest = seasonPrevious != null ? seasonPrevious.maxScore() : 0;
            boolean isNewSeasonBest = roundPosition > seasonFrom && score > previousSeasonBest;
            seasonPlacement = RoundResultsPayload.SeasonPlacement.builder()
                    .label("Season")
                    .fromRound(seasonFrom)
                    .toRound(lastRound)
                    .rank(seasonEntry.position())
                    .totalParticipants(seasonTotalParticipants)
                    .movement(seasonEntry.movement())
                    .seasonBest(seasonEntry.maxScore())
                    .isNewSeasonBest(isNewSeasonBest)
                    .build();
        }

        RoundResultsPayload.Placement quarterPlacement =
                quarterPlacementBoard == null ? null : quarterPlacementBoard.placementOf(sprintEntry.publicId());

        return RoundResultsPayload.builder()
                .userId(user.getId())
                .userEmail(user.getEmail().value())
                .userDisplayName(user.getDisplayName())
                .userPublicId(user.getPublicId().value())
                .seasonSlug(seasonSlug)
                .round(roundPosition)
                .score(score)
                .currentRound(currentRoundPosition)
                .lastRound(lastRound)
                .hitDistribution(recipient.result().hitDistribution())
                .sprint(sprintPlacement)
                .season(seasonPlacement)
                .quarter(quarterPlacement)
                .build();
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

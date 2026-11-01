package com.ligitabl.api.usecase.prediction.autojoin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.usecase.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.usecase.prediction.createprediction.JoinLockedRoundCommand;
import com.ligitabl.api.usecase.prediction.createprediction.RegisterDefaultTableCommand;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundLockedPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonInPlayPayload;
import com.ligitabl.api.usecase.shared.outbox.SeasonWelcomeFanoutPayload;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Joins users who never created a SeasonPrediction, triggered by ROUND_LOCKED and SEASON_IN_PLAY.
 * The cohort is evaluated at processing time, not from the payload.
 *
 * <p>Not {@code @Transactional}: it runs in the outbox processor's per-event transaction, so the
 * joins commit with markSent. A proxy here would mark that transaction rollback-only on any runtime
 * exception and bypass the processor's markFailed.
 *
 * <p>A database error is rethrown, not logged per user: it aborts the shared transaction, so every
 * later user would fail too. The batch retries and NOT EXISTS skips those already created. Both
 * {@code DataAccessException} types are caught: the repos throw jOOQ's, a translated JDBC path
 * Spring's, and they share no supertype.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoJoinUseCase {

    private final SeasonRepo seasonRepo;
    private final UserRepo userRepo;
    private final ContestRepo contestRepo;
    private final RoundRepo roundRepo;
    private final CreatePredictionUseCase createPredictionUseCase;
    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;

    /**
     * Joins users who registered after the pre-season window opened into the round that locked,
     * as if they had submitted while it was open.
     */
    public void execute(RoundLockedPayload event) {
        Season season = findSeason(event.seasonId());

        List<UUID> unjoinedUserIds = userRepo.findUnjoinedUserIdsAfter(season.getId(), season.getPreSeasonOpensAt());
        if (unjoinedUserIds.isEmpty()) {
            return;
        }

        Optional<JoinLockedRoundCommand> command = lockedRoundJoin(season);
        if (command.isEmpty()) {
            return;
        }

        for (UUID userId : unjoinedUserIds) {
            try {
                createPredictionUseCase
                        .execute(userId, command.get())
                        .peekLeft(error -> log.warn("[ROUND_LOCKED] Auto-join skipped for user {}: {}", userId, error));
            } catch (org.jooq.exception.DataAccessException | org.springframework.dao.DataAccessException e) {
                throw e;
            } catch (Exception e) {
                log.error("[ROUND_LOCKED] Auto-join failed for user {}", userId, e);
            }
        }
    }

    /**
     * Resolved as if the current round were still open: the round that locked is the one these
     * users join, so its status is deliberately not consulted.
     */
    private Optional<JoinLockedRoundCommand> lockedRoundJoin(Season season) {
        Optional<Contest> mainContest = contestRepo.findById(season.getMainContestId());
        if (mainContest.isEmpty()) {
            log.warn("[ROUND_LOCKED] Skipping auto-join batch for season {}: main contest not found", season.getId());
            return Optional.empty();
        }
        Optional<Round> currentRound = roundRepo.findById(season.getCurrentRoundId());
        if (currentRound.isEmpty()) {
            log.warn("[ROUND_LOCKED] Skipping auto-join batch for season {}: current round not found", season.getId());
            return Optional.empty();
        }
        int position = currentRound.get().getPosition();
        if (position > season.getMaxRounds()) {
            log.warn(
                    "[ROUND_LOCKED] Skipping auto-join batch for season {}: round {} is past the last round {}",
                    season.getId(),
                    position,
                    season.getMaxRounds());
            return Optional.empty();
        }
        return Optional.of(new JoinLockedRoundCommand(season, mainContest.get(), position));
    }

    /**
     * Gives a round-0 pre-season registration to everyone who was around during pre-season and
     * never submitted, then chains the welcome fan-out. Uses {@link RegisterDefaultTableCommand},
     * not the mid-season {@code NewJoin} shape, which would cost them the guest-prediction import
     * and most of their swap allowance.
     */
    public void execute(SeasonInPlayPayload event) throws JsonProcessingException {
        Season season = findSeason(event.seasonId());

        autoRegisterEligibleUsers(season);

        // Always, even when nobody was auto-joined: genuine pre-season registrants wait on this
        // email too. Written in this transaction so committed auto-joins are always welcomed;
        // processed in a later one so the fan-out gets its own retry budget.
        writeWelcomeFanoutEvent(season.getId());
    }

    private Season findSeason(UUID seasonId) {
        return seasonRepo
                .findById(seasonId)
                .orElseThrow(() -> new IllegalStateException("Season not found: " + seasonId));
    }

    private void autoRegisterEligibleUsers(Season season) {
        if (season.getPreSeasonOpensAt() == null) {
            // The enqueuer guards this, so reaching it means the season was edited in between.
            // Skip the batch rather than guess a cohort — the welcome fan-out still runs.
            log.warn("[SEASON_IN_PLAY] No preSeasonOpensAt on season {}; skipping auto-join", season.getId());
            return;
        }

        List<UUID> eligibleUserIds =
                userRepo.findUnjoinedUserIdsActiveSince(season.getId(), season.getPreSeasonOpensAt());
        if (eligibleUserIds.isEmpty()) {
            log.info("[SEASON_IN_PLAY] No users to auto-join for season {}", season.getId());
            return;
        }

        Optional<Contest> mainContest = contestRepo.findById(season.getMainContestId());
        if (mainContest.isEmpty()) {
            log.warn("[SEASON_IN_PLAY] Skipping auto-join batch for season {}: main contest not found", season.getId());
            return;
        }
        var command = new RegisterDefaultTableCommand(season, mainContest.get());

        int joined = 0;
        for (UUID userId : eligibleUserIds) {
            try {
                if (createPredictionUseCase
                        .execute(userId, command)
                        .peekLeft(
                                error -> log.warn("[SEASON_IN_PLAY] Auto-join skipped for user {}: {}", userId, error))
                        .isRight()) {
                    joined++;
                }
            } catch (org.jooq.exception.DataAccessException | org.springframework.dao.DataAccessException e) {
                throw e;
            } catch (Exception e) {
                log.error("[SEASON_IN_PLAY] Auto-join failed for user {}", userId, e);
            }
        }
        log.info(
                "[SEASON_IN_PLAY_AUTO_JOINED] seasonId={}, eligible={}, joined={}",
                season.getId(),
                eligibleUserIds.size(),
                joined);
    }

    private void writeWelcomeFanoutEvent(UUID seasonId) throws JsonProcessingException {
        OutboxEvent fanout = OutboxEvent.create(
                "season-welcome-fanout:" + seasonId,
                OutboxEventTypes.SEASON_WELCOME_FANOUT,
                "season",
                seasonId.toString(),
                objectMapper.writeValueAsString(new SeasonWelcomeFanoutPayload(seasonId)));
        outboxRepo.save(fanout);
    }
}

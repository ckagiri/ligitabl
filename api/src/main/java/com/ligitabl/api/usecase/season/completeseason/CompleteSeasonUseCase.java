package com.ligitabl.api.usecase.season.completeseason;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.usecase.finaltable.scorefinaltable.FinalTableScoringHook;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.SeasonCompletedPayload;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonState;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompleteSeasonUseCase {

    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;
    private final CompetitionDefaults competitionDefaults;
    private final FinalTableScoringHook finalTableScoringHook;
    private final Clock clock;

    @Transactional
    public CompleteSeasonResult execute() {
        var season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (season == null) {
            return new CompleteSeasonResult.SeasonNotFound();
        }

        var currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
        if (currentRound == null) {
            return new CompleteSeasonResult.SeasonNotEligible("Current round not found");
        }

        if (currentRound.getPosition() != season.getMaxRounds()) {
            return new CompleteSeasonResult.SeasonNotEligible("Current round is not the season's last round");
        }
        if (!currentRound.isFinalized() || !currentRound.isAdvanced()) {
            return new CompleteSeasonResult.SeasonNotEligible("Last round must be finalized and advanced first");
        }

        var notReady = roundRepo.findFirstNotFinalizedOrAdvanced(season.getId());
        if (notReady.isPresent()) {
            return new CompleteSeasonResult.SeasonNotEligible(
                    "Round " + notReady.get().getPosition() + " is not finalized and advanced");
        }

        if (!matchRepo.allMatchesFinished(currentRound.getId())) {
            return new CompleteSeasonResult.SeasonNotEligible("All matches in the last round must be finished");
        }

        season.setCompleted(true);
        season.setCompletedAt(OffsetDateTime.now(clock));

        // Completing the season should land it on OFF_SEASON (predictions closed, pre-season not
        // yet open), not INACTIVE — INACTIVE here means preSeasonOpensAt isn't configured/ordered
        // correctly. Reject rather than leave the season in limbo; the
        // admin needs to fix the season's dates first (see UpdateSeasonDatesUseCase).
        if (season.getSeasonState(clock.instant()) == SeasonState.INACTIVE) {
            return new CompleteSeasonResult.SeasonNotEligible(
                    "Completing this season would leave it INACTIVE — check preSeasonOpensAt/season.endDate");
        }

        seasonRepo.save(season);
        scoreFinalTable(season);
        enqueueSeasonCompleted(season);

        log.info("[ADMIN_COMPLETE_SEASON] season={} marked completed", season.getId());
        return new CompleteSeasonResult.Ok();
    }

    /**
     * Scores the Final Table side game against the final standings, in this transaction so the result
     * is there the moment the season is.
     *
     * <p> A side-game bug must not be able to fail an admin's completion.
     */
    private void scoreFinalTable(Season season) {
        try {
            finalTableScoringHook.onSeasonCompleted(season);
        } catch (Exception e) {
            log.error("[FINAL_TABLE_HOOK_FAILED] season={}: {}", season.getId(), e.getMessage(), e);
        }
    }

    /**
     * Records the completion in the outbox in this transaction, so it commits atomically with the
     * season's completed flag. The relay expands it into per-user SEGMENT_RESULTS events for the
     * full-season podium.
     *
     * <p>Any failure here logs loudly but never blocks the completion itself.
     */
    private void enqueueSeasonCompleted(Season season) {
        try {
            outboxRepo.save(OutboxEvent.create(
                    "season-completed:%s".formatted(season.getId()),
                    OutboxEventTypes.SEASON_COMPLETED,
                    "season",
                    season.getId().toString(),
                    objectMapper.writeValueAsString(new SeasonCompletedPayload(season.getId()))));
        } catch (Exception e) {
            log.error("[SEASON_COMPLETED_OUTBOX_FAILED] season={}: {}", season.getId(), e.getMessage(), e);
        }
    }
}

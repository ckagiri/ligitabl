package com.ligitabl.api.usecases.round.finalizeround;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/rounds")
@RequiredArgsConstructor
@Slf4j
public class FinalizeRoundController {
    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final FinalizeRoundUseCase finalizeRoundUseCase;

    /**
     * Finalizes the current round for the default competition (premier-league).
     * Only admins can execute this endpoint.
     */
    @PostMapping("/default/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> finalizeDefaultRound() {
        // Get default competition's current round
        var defaultCompetition = competitionDefaults.defaultCompetitionSlug();
        var season = seasonRepo.findActiveSeason(defaultCompetition)
                .orElseThrow(() -> new IllegalStateException("No active season for " + defaultCompetition));

        var result = finalizeRoundUseCase.execute(season.getId());

        return result
                .fold(
                        error -> switch (error) {
                            case FinalizeRoundError.SeasonNotFound e ->
                                    ResponseEntity.notFound().build();
                            case FinalizeRoundError.RoundNotFound e ->
                                    ResponseEntity.notFound().build();
                            case FinalizeRoundError.RoundNotReady(UUID roundId, String reason) ->
                                    String.format("Round %s not ready: %s", roundId, reason);
                            case FinalizeRoundError.RoundNotLocked e ->
                                    ResponseEntity.badRequest().body(new ErrorResponse(
                                            "Round is not locked. Current status: " + e.currentStatus()
                                    ));
                            case FinalizeRoundError.CancelledMatchesExist e ->
                                    ResponseEntity.badRequest().body(new ErrorResponse(
                                            "Cannot finalize: " + e.matchIds().size() + " CANCELLED matches require admin resolution"
                                    ));
                            case FinalizeRoundError.StandingsValidationFailed e ->
                                    ResponseEntity.badRequest().body(new ErrorResponse(
                                            "Standings validation failed: " + e.reason()
                                    ));
                            case FinalizeRoundError.AlreadyFinalized e ->
                                    ResponseEntity.badRequest().body(new ErrorResponse(
                                            "Round is already finalized"
                                    ));
                            case FinalizeRoundError.ScoringFailed(UUID userId, String reason) ->
                                    String.format("Scoring failed for user %s: %s", userId, reason);
                            case FinalizeRoundError.TransactionFailed(String reason) ->
                                    String.format("Transaction failed: %s", reason);

                        },
                        result_ -> ResponseEntity.ok(result_)
                );
    }

    record ErrorResponse(String message) {
    }
}

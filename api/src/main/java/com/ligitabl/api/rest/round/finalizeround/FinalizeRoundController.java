package com.ligitabl.api.rest.round.finalizeround;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/rounds")
@RequiredArgsConstructor
@Slf4j
public class FinalizeRoundController {
    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final FinalizeRoundUseCase finalizeRoundUseCase;

    record FinalizeCurrentRoundRequest(Boolean recompute) {
        boolean recomputeOrDefault() {
            return recompute != null && recompute;
        }
    }

    /**
     * Finalizes the current round for the default competition (premier-league).
     * Only admins can execute this endpoint.
     */
    @PostMapping("/current/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> finalizeDefaultRound(@RequestBody(required = false) FinalizeCurrentRoundRequest request) {
        boolean recompute = request != null && request.recomputeOrDefault();

        // Get default competition's current round
        var defaultCompetition = competitionDefaults.defaultCompetitionSlug();
        var season = seasonRepo
                .findActiveSeason(defaultCompetition)
                .orElseThrow(() -> new IllegalStateException("No active season for " + defaultCompetition));

        var result = finalizeRoundUseCase.execute(new FinalizeRoundCommand(season.getId(), null, recompute));

        return result.fold(
                error -> switch (error) {
                    case FinalizeRoundError.SeasonNotFound e -> ResponseEntity.notFound()
                            .build();
                    case FinalizeRoundError.RoundNotFound e -> ResponseEntity.notFound()
                            .build();
                    case FinalizeRoundError.RoundNotReady e -> ResponseEntity.badRequest()
                            .body(new ErrorResponse(String.format("Round %s not ready: %s", e.roundId(), e.reason())));
                    case FinalizeRoundError.RoundObstructed e -> ResponseEntity.status(409)
                            .body(new RoundObstructedResponse("ROUND_OBSTRUCTED", e.message(), e.obstructedMatchIds()));
                    case FinalizeRoundError.StandingsValidationFailed e -> ResponseEntity.badRequest()
                            .body(new ErrorResponse("Standings validation failed: " + e.reason()));
                    case FinalizeRoundError.TransactionFailed e -> ResponseEntity.internalServerError()
                            .body(new ErrorResponse(String.format("Transaction failed: %s", e.reason())));

                    default -> ResponseEntity.internalServerError()
                            .body(new ErrorResponse("Unexpected error: " + error));
                },
                ResponseEntity::ok);
    }

    record ErrorResponse(String message) {}

    record RoundObstructedResponse(String error, String message, java.util.List<java.util.UUID> obstructedMatches) {}
}

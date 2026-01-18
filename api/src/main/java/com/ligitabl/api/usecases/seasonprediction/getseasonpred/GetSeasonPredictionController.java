package com.ligitabl.api.usecases.seasonprediction.getseasonpred;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.auth.CurrentUserId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/seasonprediction")
@RequiredArgsConstructor
@Slf4j
public class GetSeasonPredictionController {

    private final GetSeasonPredictionUseCase getSeasonPredictionUseCase;
    private final CurrentUserId currentUserId;

    @GetMapping
    public ResponseEntity<?> getSeasonPrediction() {
        UUID userId = currentUserId.require();
        log.info("GetSeasonPrediction request for user {}", userId);

        return getSeasonPredictionUseCase.execute(userId).fold(this::handleError, this::handleSuccess);
    }

    private ResponseEntity<?> handleError(GetSeasonPredictionError error) {
        return switch (error) {
            case GetSeasonPredictionError.SeasonNotFound __ -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "SEASON_NOT_FOUND",
                            "message", "No season available"));

            case GetSeasonPredictionError.BaselineRankingsMissing e -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "BASELINE_RANKINGS_MISSING",
                            "message", "Season baseline rankings missing",
                            "season_id", e.seasonId()));

            case GetSeasonPredictionError.SeasonHasNoCurrentRound e -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "SEASON_HAS_NO_CURRENT_ROUND",
                            "message", "Season has no current round",
                            "season_id", e.seasonId()));

            case GetSeasonPredictionError.CurrentRoundNotFound e -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "CURRENT_ROUND_NOT_FOUND",
                            "message", "Current round not found",
                            "round_id", e.roundId()));

            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "UNKNOWN_ERROR"));
        };
    }

    private ResponseEntity<?> handleSuccess(GetSeasonPredictionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prediction_id", result.predictionId());
        payload.put("season_id", result.seasonId());
        payload.put("at_round_number", result.atRoundNumber());
        payload.put("current_round_number", result.currentRoundNumber());
        payload.put("round_status", result.roundStatus());
        payload.put("season_completed", result.seasonCompleted());
        payload.put("ranking_source", result.rankingSource().name());
        payload.put("rankings", result.rankings());
        payload.put(
                "last_swap_at",
                result.lastSwapAt() == null ? null : result.lastSwapAt().toString());
        payload.put("can_swap", result.swapStatus().canSwap());

        if (result.swapStatus().blockedReason() != null) {
            payload.put("swap_blocked_reason", result.swapStatus().blockedReason());
        }

        if (result.swapStatus().nextSwapAt() != null) {
            payload.put("next_swap_at", result.swapStatus().nextSwapAt().toString());
        }

        if (result.swapStatus().hoursUntilNext() != null) {
            payload.put("hours_until_next", result.swapStatus().hoursUntilNext());
        }

        return ResponseEntity.ok(payload);
    }
}

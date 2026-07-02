package com.ligitabl.api.rest.prediction.makeswap;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.auth.CurrentUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/seasonprediction")
@RequiredArgsConstructor
public class MakeSwapController {

    private final MakeSwapUseCase makeSwapUseCase;
    private final CurrentUserId currentUserId;

    @PostMapping("/swap")
    public ResponseEntity<?> makeSwap(@RequestBody SwapCommand command) {
        UUID userId = currentUserId.require();
        return makeSwapUseCase.execute(userId, command).fold(this::handleSwapError, this::handleSwapSuccess);
    }

    private ResponseEntity<?> handleSwapError(SwapError error) {
        return switch (error) {
            case SwapError.NoPredictionFound e -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "NO_PREDICTION",
                            "message", "No prediction found for current season"));

            case SwapError.RoundNotFound e -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error",
                            "ROUND_NOT_FOUND",
                            "message",
                            "Round " + e.roundPosition() + " not found",
                            "round_position",
                            e.roundPosition(),
                            "season_id",
                            e.seasonId().toString()));

            case SwapError.RoundNotOpen e -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error",
                            "ROUND_NOT_OPEN",
                            "message",
                            "Cannot swap when round is " + e.roundStatus(),
                            "round_status",
                            e.roundStatus()));

            case SwapError.CooldownActive e -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "COOLDOWN_ACTIVE",
                            "message", String.format("Next swap available in %.1fh", e.hoursRemaining()),
                            "next_swap_at", e.nextSwapAt().toString(),
                            "hours_remaining", e.hoursRemaining()));

            case SwapError.TeamsNotFound e -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error",
                            "INVALID_TEAMS",
                            "message",
                            "Teams not found in your prediction",
                            "team_a",
                            e.teamACode(),
                            "team_b",
                            e.teamBCode()));

            case SwapError.SeasonCompleted __ -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "SEASON_COMPLETED",
                            "message", "Cannot swap in completed season"));

            case SwapError.SeasonInSetupMode __ -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "SEASON_IN_SETUP_MODE",
                            "message", "Season is being reconfigured. Please try again shortly."));

            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "UNKNOWN_ERROR"));
        };
    }

    private ResponseEntity<?> handleSwapSuccess(SwapResult result) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "next_swap_at", result.nextSwapAt().toString(),
                "hours_until_next", result.hoursUntilNext(),
                "current_rankings", result.updatedPrediction().getCurrentRankings()));
    }
}

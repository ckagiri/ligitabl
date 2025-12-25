package com.ligitabl.api.usecases.prediction.makeswap;

import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class MakeSwapController {

    private final MakeSwapUseCase makeSwapUseCase;
        private final UserRepo userRepo;

    @PostMapping("/swap")
    public ResponseEntity<?> makeSwap(
            Authentication authentication,
            @RequestBody SwapCommand command
    ) {
        UUID userId = resolveUserId(authentication);
        return makeSwapUseCase.execute(userId, command)
                .fold(
                        this::handleSwapError,
                        this::handleSwapSuccess
                );
    }

    private UUID resolveUserId(Authentication authentication) {
        String publicIdStr = authentication.getName();
        PublicId publicId = PublicId.create(publicIdStr);

        return userRepo.findByPublicId(publicId)
                .map(com.ligitabl.model.domain.User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private ResponseEntity<?> handleSwapError(SwapError error) {
        return switch (error) {
            case SwapError.NoPredictionFound e ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of(
                                    "error", "NO_PREDICTION",
                                    "message", "No prediction found for current season"
                            ));

            case SwapError.RoundNotOpen e ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "ROUND_NOT_OPEN",
                                    "message", "Cannot swap when round is " + e.roundStatus(),
                                    "round_status", e.roundStatus()
                            ));

            case SwapError.CooldownActive e ->
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(Map.of(
                                    "error", "COOLDOWN_ACTIVE",
                                    "message", String.format("Next swap available in %.1fh", e.hoursRemaining()),
                                    "next_swap_at", e.nextSwapAt().toString(),
                                    "hours_remaining", e.hoursRemaining()
                            ));

            case SwapError.TeamsNotFound e ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "error", "INVALID_TEAMS",
                                    "message", "Teams not found in your prediction",
                                    "team_a", e.teamACode(),
                                    "team_b", e.teamBCode()
                            ));

            case SwapError.SeasonCompleted __ ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "SEASON_COMPLETED",
                                    "message", "Cannot swap in completed season"
                            ));

            default ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "UNKNOWN_ERROR"));
        };
    }

    private ResponseEntity<?> handleSwapSuccess(SwapResult result) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "next_swap_at", result.nextSwapAt().toString(),
                "hours_until_next", result.hoursUntilNext(),
                "current_rankings", result.updatedPrediction().getCurrentRankings()
        ));
    }
}

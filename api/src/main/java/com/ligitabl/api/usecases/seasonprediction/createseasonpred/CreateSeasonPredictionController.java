package com.ligitabl.api.usecases.seasonprediction.createseasonpred;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.auth.CurrentUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/seasonprediction")
@RequiredArgsConstructor
@Slf4j
public class CreateSeasonPredictionController {

    private final CreateSeasonPredictionUseCase createSeasonPredictionUseCase;
    private final CurrentUserId currentUserId;

    @PostMapping
    public ResponseEntity<?> createSeasonPrediction(@RequestBody @Valid CreateSeasonPredictionCommand request) {
        UUID userId = currentUserId.require();
        log.info("Create season-prediction request from user {}", userId);

        return createSeasonPredictionUseCase
                .execute(userId, request)
                .fold(this::handleCreateError, this::handleCreateSuccess);
    }

    private ResponseEntity<?> handleCreateSuccess(CreateSeasonPredictionResult result) {
        log.info(
                "User joined successfully: prediction={}, entry={}, atRound={}",
                result.predictionId(),
                result.entryId(),
                result.atRoundNumber());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "success", true,
                        "prediction_id", result.predictionId(),
                        "entry_id", result.entryId(),
                        "at_round_number", result.atRoundNumber(),
                        "message", result.message()));
    }

    private ResponseEntity<?> handleCreateError(CreateSeasonPredictionError error) {
        return switch (error) {
            case CreateSeasonPredictionError.SeasonNotFound __ -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "SEASON_NOT_FOUND",
                            "message", "No active season available"));

            case CreateSeasonPredictionError.SeasonCompleted __ -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "SEASON_COMPLETED",
                            "message", "Cannot join a completed season"));

            case CreateSeasonPredictionError.AlreadyJoined e -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "ALREADY_JOINED",
                            "message", "You have already joined this season",
                            "prediction_id", e.existingPredictionId()));

            case CreateSeasonPredictionError.InvalidTeamCount e -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "INVALID_TEAM_COUNT",
                            "message", String.format("Expected %d teams, but received %d", e.required(), e.provided()),
                            "provided", e.provided(),
                            "required", e.required()));

            case CreateSeasonPredictionError.DuplicatePositions e -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "DUPLICATE_POSITIONS",
                            "message", "Each position must be unique",
                            "duplicates", e.duplicates()));

            case CreateSeasonPredictionError.DuplicateTeamCodes e -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "DUPLICATE_TEAMS",
                            "message", "Each team can only appear once",
                            "duplicates", e.duplicates()));

            case CreateSeasonPredictionError.InvalidTeamCodes e -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "INVALID_TEAMS",
                            "message", "Some team codes are not valid for this season",
                            "invalid_codes", e.invalidCodes()));

            case CreateSeasonPredictionError.SeasonEnded e -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error",
                            "SEASON_ENDED",
                            "message",
                            "Cannot join - season has ended",
                            "current_round",
                            e.currentRound(),
                            "max_rounds",
                            e.maxRounds()));

            case CreateSeasonPredictionError.TransactionFailed e -> ResponseEntity.status(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "TRANSACTION_FAILED",
                            "message", "Failed to create prediction",
                            "details", e.reason()));

            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "UNKNOWN_ERROR"));
        };
    }
}

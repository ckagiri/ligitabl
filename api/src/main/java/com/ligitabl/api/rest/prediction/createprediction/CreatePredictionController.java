package com.ligitabl.api.rest.prediction.createprediction;

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
public class CreatePredictionController {

    private final CreatePredictionUseCase createPredictionUseCase;
    private final CurrentUserId currentUserId;

    @PostMapping
    public ResponseEntity<?> createSeasonPrediction(@RequestBody @Valid CreatePredictionCommand request) {
        UUID userId = currentUserId.require();
        log.info("Create season-prediction request from user {}", userId);

        return createPredictionUseCase
                .execute(userId, request)
                .fold(this::handleCreateError, this::handleCreateSuccess);
    }

    private ResponseEntity<?> handleCreateSuccess(CreatePredictionResult result) {
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

    private ResponseEntity<?> handleCreateError(CreatePredictionError error) {
        return switch (error) {
            case CreatePredictionError.NotFound __ -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "SEASON_NOT_FOUND",
                            "message", "No active season available"));

            case CreatePredictionError.Completed __ -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "SEASON_COMPLETED",
                            "message", "Cannot join a completed season"));

            case CreatePredictionError.SeasonInSetupMode __ -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "SEASON_IN_SETUP_MODE",
                            "message", "Season is being reconfigured. Please try again shortly."));

            case CreatePredictionError.AlreadyJoined e -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "ALREADY_JOINED",
                            "message", "You have already joined this season",
                            "prediction_id", e.existingPredictionId()));

            case CreatePredictionError.TooManySwaps e -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error",
                            "TOO_MANY_SWAPS",
                            "message",
                            "Too many swaps provided",
                            "provided",
                            e.provided(),
                            "max",
                            e.max()));

            case CreatePredictionError.SameTeam __ -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "SAME_TEAM",
                            "message", "Team A and Team B must be different"));

            case CreatePredictionError.InvalidTeamCode e -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "INVALID_TEAM_CODE",
                            "message", "Team code is not valid for this season",
                            "code", e.code()));

            case CreatePredictionError.Ended e -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error",
                            "SEASON_ENDED",
                            "message",
                            "Cannot join - season has ended",
                            "current_round",
                            e.currentRound(),
                            "max_rounds",
                            e.maxRounds()));

            case CreatePredictionError.CurrentRoundNotFound e -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "CURRENT_ROUND_NOT_FOUND",
                            "message", "Current round not found for season",
                            "season_id", e.seasonId()));

            case CreatePredictionError.MainContestNotFound __ -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "MAIN_CONTEST_NOT_FOUND",
                            "message", "Main contest not found for the active season"));

            case CreatePredictionError.TransactionFailed e -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "TRANSACTION_FAILED",
                            "message", "Failed to create prediction",
                            "details", e.reason()));

            case CreatePredictionError.CorruptPreSeasonRegistration e -> ResponseEntity.status(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "CORRUPT_PRE_SEASON_REGISTRATION",
                            "message", "Pre-season registration is in an invalid state",
                            "prediction_id", e.predictionId()));
        };
    }
}

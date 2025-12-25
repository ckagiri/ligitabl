package com.ligitabl.api.usecases.contest.joincontest;

import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.repo.UserRepo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

// interfaces/rest/ContestController.java
@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
@Slf4j
public class JoinContestController {

    private final JoinContestUseCase joinContestUseCase;
        private final UserRepo userRepo;

    @PostMapping("/join")
    public ResponseEntity<?> joinContest(
            Authentication authentication,
            @RequestBody @Valid JoinContestRequest request
    ) {
        UUID userId = resolveUserId(authentication);
        log.info("Join contest request from user {}", userId);

        return joinContestUseCase.execute(userId, request)
                .fold(
                        this::handleJoinError,
                        this::handleJoinSuccess
                );
    }

    private UUID resolveUserId(Authentication authentication) {
        String publicIdStr = authentication.getName();
        PublicId publicId = PublicId.create(publicIdStr);

        return userRepo.findByPublicId(publicId)
                .map(com.ligitabl.model.domain.User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private ResponseEntity<?> handleJoinError(JoinContestError error) {
        return switch (error) {
            case JoinContestError.SeasonNotFound __ ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of(
                                    "error", "SEASON_NOT_FOUND",
                                    "message", "No active season available"
                            ));

            case JoinContestError.SeasonCompleted __ ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "SEASON_COMPLETED",
                                    "message", "Cannot join a completed season"
                            ));

            case JoinContestError.AlreadyJoined e ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "ALREADY_JOINED",
                                    "message", "You have already joined this season",
                                    "prediction_id", e.existingPredictionId()
                            ));

            case JoinContestError.InvalidTeamCount e ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "error", "INVALID_TEAM_COUNT",
                                    "message", String.format(
                                            "Expected %d teams, but received %d",
                                            e.required(), e.provided()
                                    ),
                                    "provided", e.provided(),
                                    "required", e.required()
                            ));

            case JoinContestError.DuplicatePositions e ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "error", "DUPLICATE_POSITIONS",
                                    "message", "Each position must be unique",
                                    "duplicates", e.duplicates()
                            ));

            case JoinContestError.DuplicateTeamCodes e ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "error", "DUPLICATE_TEAMS",
                                    "message", "Each team can only appear once",
                                    "duplicates", e.duplicates()
                            ));

            case JoinContestError.InvalidTeamCodes e ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "error", "INVALID_TEAMS",
                                    "message", "Some team codes are not valid for this season",
                                    "invalid_codes", e.invalidCodes()
                            ));

            case JoinContestError.SeasonEnded e ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "SEASON_ENDED",
                                    "message", "Cannot join - season has ended",
                                    "current_round", e.currentRound(),
                                    "max_rounds", e.maxRounds()
                            ));

            case JoinContestError.TransactionFailed e ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of(
                                    "error", "TRANSACTION_FAILED",
                                    "message", "Failed to create prediction",
                                    "details", e.reason()
                            ));

            default ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "UNKNOWN_ERROR"));
        };
    }

    private ResponseEntity<?> handleJoinSuccess(JoinContestResult result) {
        log.info("User joined successfully: prediction={}, entry={}, atRound={}",
                result.predictionId(), result.entryId(), result.atRoundNumber());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "success", true,
                        "prediction_id", result.predictionId(),
                        "entry_id", result.entryId(),
                        "at_round_number", result.atRoundNumber(),
                        "message", result.message()
                ));
    }
}

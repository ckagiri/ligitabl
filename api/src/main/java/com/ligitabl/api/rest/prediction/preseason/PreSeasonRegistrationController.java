package com.ligitabl.api.rest.prediction.preseason;

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
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/pre-season")
@RequiredArgsConstructor
@Slf4j
public class PreSeasonRegistrationController {

    private final PreSeasonRegistrationUseCase useCase;
    private final CurrentUserId currentUserId;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody PreSeasonRegistrationCommand command) {
        UUID userId = currentUserId.require();
        log.info("[PRE_SEASON] register request from user {}", userId);

        return useCase.execute(userId, command).fold(this::handleError, this::handleSuccess);
    }

    private ResponseEntity<?> handleSuccess(PreSeasonRegistrationResult result) {
        return ResponseEntity.ok(Map.of(
                "predictionId", result.predictionId(),
                "entryId", result.entryId(),
                "swapsApplied", result.swapsApplied()));
    }

    private ResponseEntity<?> handleError(PreSeasonRegistrationError error) {
        return switch (error) {
            case PreSeasonRegistrationError.SeasonNotFound e ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "season_not_found"));
            case PreSeasonRegistrationError.NotPreSeason e ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "not_pre_season"));
            case PreSeasonRegistrationError.AlreadyJoined e ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "already_joined", "predictionId", e.existingPredictionId()));
            case PreSeasonRegistrationError.TooManySwaps e ->
                    ResponseEntity.badRequest().body(Map.of("error", "too_many_swaps", "max", e.max()));
            case PreSeasonRegistrationError.SameTeam e ->
                    ResponseEntity.badRequest().body(Map.of("error", "same_team"));
            case PreSeasonRegistrationError.InvalidTeamCode e ->
                    ResponseEntity.badRequest().body(Map.of("error", "invalid_team_code", "code", e.code()));
            case PreSeasonRegistrationError.MainContestNotFound e ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "contest_not_found"));
            case PreSeasonRegistrationError.TransactionFailed e ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "transaction_failed"));
        };
    }
}

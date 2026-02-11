package com.ligitabl.api.rest.leaderboard.getleaderboard;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
@Slf4j
public class GetLeaderboardController {
    private final GetLeaderboardUseCase getLeaderboardUseCase;

    /**
     * GET /leaderboard?phase=Q2
     *
     * Gets leaderboard for the default competition's main contest.
     * Phase is optional, defaults to FS (Full Season).
     */
    @GetMapping("/main/leaderboard")
    public ResponseEntity<?> getLeaderboard(
            @RequestParam(required = false) String phase,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        var query = new GetLeaderboardQuery(phase, offset, limit);

        return getLeaderboardUseCase
                .execute(query)
                .fold(
                        error -> {
                            ErrorResponse errorResponse =
                                    switch (error) {
                                        case GetLeaderboardError.DefaultCompetitionNotFound e -> new ErrorResponse(
                                                HttpStatus.NOT_FOUND, e.message());
                                        case GetLeaderboardError.ActiveSeasonNotFound e -> new ErrorResponse(
                                                HttpStatus.NOT_FOUND, e.message());
                                        case GetLeaderboardError.MainContestNotFound e -> new ErrorResponse(
                                                HttpStatus.NOT_FOUND, e.message());
                                        case GetLeaderboardError.InvalidPhase e -> new ErrorResponse(
                                                HttpStatus.BAD_REQUEST, "Invalid phase: " + e.phaseCode());
                                        case GetLeaderboardError.PhasesNotConfigured __ -> new ErrorResponse(
                                                HttpStatus.INTERNAL_SERVER_ERROR, "Competition phases not configured");
                                    };

                            return ResponseEntity.status(errorResponse.status().value())
                                    .body(new ErrorDto(errorResponse.message()));
                        },
                        result -> ResponseEntity.ok(new LeaderboardResponse(
                                result.contestId(),
                                new PhaseDto(
                                        result.phase().getCode(),
                                        result.phase().getName(),
                                        result.phase().getFrom(),
                                        result.phase().getTo()),
                                result.rankings().stream()
                                        .map(entry -> new LeaderboardEntryDto(
                                                entry.position(),
                                                entry.publicId(),
                                                entry.displayName(),
                                                entry.totalScore(),
                                                entry.roundScore(),
                                                entry.maxScore(),
                                                entry.totalZeroes(),
                                                entry.totalSwaps(),
                                                entry.movement()))
                                        .toList())));
    }

    record ErrorDto(String message) {}

    record ErrorResponse(HttpStatusCode status, String message) {}

    record LeaderboardResponse(UUID contestId, PhaseDto phase, List<LeaderboardEntryDto> rankings) {}
}

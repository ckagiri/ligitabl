package com.ligitabl.api.rest.leaderboard;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.rest.leaderboard.dtos.LeaderboardEntryDto;
import com.ligitabl.api.rest.leaderboard.dtos.PhaseDto;

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
                            if (error instanceof GetLeaderboardError.DefaultCompetitionNotFound e) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(new ErrorDto(e.message()));
                            }

                            if (error instanceof GetLeaderboardError.ActiveSeasonNotFound e) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(new ErrorDto(e.message()));
                            }

                            if (error instanceof GetLeaderboardError.MainContestNotFound e) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(new ErrorDto(e.message()));
                            }

                            if (error instanceof GetLeaderboardError.InvalidPhase invalid) {
                                return ResponseEntity.badRequest()
                                        .body(new ErrorDto("Invalid phase: " + invalid.phaseCode()));
                            }

                            if (error instanceof GetLeaderboardError.PhasesNotConfigured) {
                                return ResponseEntity.internalServerError()
                                        .body(new ErrorDto("Competition phases not configured"));
                            }

                            return ResponseEntity.internalServerError().body(new ErrorDto("Unexpected error"));
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

    record LeaderboardResponse(UUID contestId, PhaseDto phase, List<LeaderboardEntryDto> rankings) {}
}

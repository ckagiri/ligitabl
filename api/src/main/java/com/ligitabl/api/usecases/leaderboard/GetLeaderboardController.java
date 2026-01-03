package com.ligitabl.api.usecases.leaderboard;

import com.ligitabl.api.usecases.leaderboard.dtos.LeaderboardEntryDto;
import com.ligitabl.api.usecases.leaderboard.dtos.PhaseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
    public ResponseEntity<?> getLeaderboard(@RequestParam(required = false) String phase) {
        var query = new GetLeaderboardQuery(phase);

        return getLeaderboardUseCase.execute(query)
                .fold(
                        error -> {
                            if (error instanceof GetLeaderboardError.DefaultCompetitionNotFound
                                    || error instanceof GetLeaderboardError.ActiveSeasonNotFound
                                    || error instanceof GetLeaderboardError.MainContestNotFound) {
                                return ResponseEntity.notFound().build();
                            }

                            if (error instanceof GetLeaderboardError.InvalidPhase invalid) {
                                return ResponseEntity.badRequest()
                                        .body(new ErrorDto("Invalid phase: " + invalid.phaseCode()));
                            }

                            if (error instanceof GetLeaderboardError.PhasesNotConfigured) {
                                return ResponseEntity.internalServerError()
                                        .body(new ErrorDto("Competition phases not configured"));
                            }

                            return ResponseEntity.internalServerError()
                                    .body(new ErrorDto("Unexpected error"));
                        },
                        result -> ResponseEntity.ok(new LeaderboardResponse(
                                result.contestId(),
                                new PhaseDto(
                                        result.phase().getCode(),
                                        result.phase().getName(),
                                        result.phase().getFrom(),
                                        result.phase().getTo()
                                ),
                                result.rankings().stream()
                                        .map(entry -> new LeaderboardEntryDto(
                                                entry.position(),
                                                entry.displayName(),
                                                entry.totalScore(),
                                                entry.maxScore(),
                                                entry.totalZeroes(),
                                                entry.totalSwaps(),
                                                entry.movement()
                                        ))
                                        .toList()
                        ))
                );
    }

        record ErrorDto(String message) {}

    record LeaderboardResponse(
            UUID contestId,
            PhaseDto phase,
            List<LeaderboardEntryDto> rankings
    ) {}
}

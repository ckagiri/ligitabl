package com.ligitabl.api.rest.standings;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
@Slf4j
public class GetDefaultRoundStandingsController {

    private final GetDefaultRoundStandingsUseCase getDefaultRoundStandingsUseCase;

    /**
     * GET /api/rounds/current/standings
     * GET /api/rounds/current/standings?competition=bundesliga
     */
    @GetMapping("/current/standings")
    public ResponseEntity<List<StandingsEntryDto>> getCurrentRoundStandings(
            @RequestParam(required = false) String competition) {
        log.info("GetCurrentRoundStandings request, competition={}", competition);
        return executeUseCase(GetDefaultRoundStandingsQuery.currentRound(competition));
    }

    /**
     * GET /api/rounds/2/standings
     * GET /api/rounds/2/standings?competition=bundesliga
     */
    @GetMapping("/{roundPosition}/standings")
    public ResponseEntity<List<StandingsEntryDto>> getRoundStandingsByPosition(
            @PathVariable Integer roundPosition, @RequestParam(required = false) String competition) {
        log.info("GetRoundStandings request, position={}, competition={}", roundPosition, competition);
        return executeUseCase(GetDefaultRoundStandingsQuery.byPosition(roundPosition, competition));
    }

    private ResponseEntity<List<StandingsEntryDto>> executeUseCase(GetDefaultRoundStandingsQuery request) {
        var result = getDefaultRoundStandingsUseCase.execute(request);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                payload -> {
                    log.debug("GetRoundStandings success, count={}", payload.standings().size());
                    return ResponseEntity.ok(payload.standings());
                });
    }
}

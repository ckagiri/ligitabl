package com.ligitabl.api.runners.calcstandings;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.rest.standings.StandingsEntryDto;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
@Slf4j
public class CalculateRoundStandingsController {

    private final CalculateRoundStandingsUseCase calculateRoundStandingsUseCase;

    @PostMapping("/{roundPosition}/standings/calculate")
    public ResponseEntity<List<StandingsEntryDto>> getRoundStandingsByPosition(
            @PathVariable Integer roundPosition, @RequestParam(required = false) String competition) {
        log.info("CalculateRoundStandings request, position={}, competition={}", roundPosition, competition);
        return executeUseCase(CalculateRoundStandingsCommand.byPosition(roundPosition, competition));
    }

    @PostMapping("/current/standings/calculate")
    public ResponseEntity<List<StandingsEntryDto>> getCurrentRoundStandings(
            @RequestParam(required = false) String competition) {
        log.info("GetCurrentRoundStandings request, competition={}", competition);
        return executeUseCase(CalculateRoundStandingsCommand.currentRound(competition));
    }

    private ResponseEntity<List<StandingsEntryDto>> executeUseCase(CalculateRoundStandingsCommand command) {
        var result = calculateRoundStandingsUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                standings -> {
                    log.debug("CalculateRoundStandings success, count={}", standings.size());
                    return ResponseEntity.ok(standings);
                });
    }
}

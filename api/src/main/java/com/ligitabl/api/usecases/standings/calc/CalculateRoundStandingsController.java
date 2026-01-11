package com.ligitabl.api.usecases.standings.calc;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecases.standings.StandingsEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
@Slf4j
public class CalculateRoundStandingsController {

    private final CalculateRoundStandingsUseCase getDefaultRoundStandingsUseCase;

    /**
     * GET /api/rounds/2/standings
     * GET /api/rounds/2/standings?competition=bundesliga
     */
    @GetMapping("/{roundPosition}/standings")
    public ResponseEntity<List<StandingsEntryDto>> getRoundStandingsByPosition(
            @PathVariable Integer roundPosition,
            @RequestParam(required = false) String competition) {
        log.info("GetRoundStandings request, position={}, competition={}", roundPosition, competition);
        return executeUseCase(CalculateRoundStandingsCommand.byPosition(roundPosition, competition));
    }

    private ResponseEntity<List<StandingsEntryDto>> executeUseCase(CalculateRoundStandingsCommand request) {
        var result = getDefaultRoundStandingsUseCase.execute(request);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                standings -> {
                    log.debug("GetRoundStandings success, count={}", standings.size());
                    return ResponseEntity.ok(standings);
                }
        );
    }
}

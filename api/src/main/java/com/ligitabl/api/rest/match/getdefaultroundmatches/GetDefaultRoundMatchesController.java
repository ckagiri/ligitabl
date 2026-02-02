package com.ligitabl.api.rest.match.getdefaultroundmatches;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.rest.match.MatchDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
@Slf4j
public class GetDefaultRoundMatchesController {

    private final GetDefaultRoundMatchesUseCase getDefaultRoundMatchesUseCase;

    @GetMapping({"/current/matches", "/default/matches"})
    public ResponseEntity<List<MatchDto>> getCurrentRoundMatches(@RequestParam(required = false) String competition) {
        log.info("GetCurrentRoundMatches command, competition={}", competition);
        return executeUseCase(GetDefaultRoundMatchesQuery.currentRound(competition));
    }

    @GetMapping("/{roundPosition}/matches")
    public ResponseEntity<List<MatchDto>> getRoundMatchesByPosition(
            @PathVariable Integer roundPosition, @RequestParam(required = false) String competition) {
        log.info("GetRoundMatches command, position={}, competition={}", roundPosition, competition);
        return executeUseCase(GetDefaultRoundMatchesQuery.byPosition(roundPosition, competition));
    }

    private ResponseEntity<List<MatchDto>> executeUseCase(GetDefaultRoundMatchesQuery command) {
        var result = getDefaultRoundMatchesUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                matches -> {
                    log.debug("GetRoundMatches success count={}", matches.size());
                    return ResponseEntity.ok(matches);
                });
    }
}

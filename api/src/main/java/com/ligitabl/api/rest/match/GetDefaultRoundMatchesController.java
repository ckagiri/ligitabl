package com.ligitabl.api.rest.match;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecase.match.getdefaultroundmatches.GetDefaultRoundMatchesQuery;
import com.ligitabl.api.usecase.match.getdefaultroundmatches.GetDefaultRoundMatchesUseCase;
import com.ligitabl.api.usecase.match.getdefaultroundmatches.RoundMatchesResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
@Slf4j
public class GetDefaultRoundMatchesController {

    private final GetDefaultRoundMatchesUseCase getDefaultRoundMatchesUseCase;

    @GetMapping({"/current/matches", "/default/matches"})
    public ResponseEntity<RoundMatchesResult> getCurrentRoundMatches(
            @RequestParam(required = false) String competition) {
        log.info("GetCurrentRoundMatches command, competition={}", competition);
        return executeUseCase(GetDefaultRoundMatchesQuery.currentRound(competition));
    }

    @GetMapping("/{roundPosition}/matches")
    public ResponseEntity<RoundMatchesResult> getRoundMatchesByPosition(
            @PathVariable Integer roundPosition, @RequestParam(required = false) String competition) {
        log.info("GetRoundMatches command, position={}, competition={}", roundPosition, competition);
        return executeUseCase(GetDefaultRoundMatchesQuery.byPosition(roundPosition, competition));
    }

    private ResponseEntity<RoundMatchesResult> executeUseCase(GetDefaultRoundMatchesQuery command) {
        var result = getDefaultRoundMatchesUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                payload -> {
                    log.debug(
                            "GetRoundMatches success count={}",
                            payload.matches().size());
                    return ResponseEntity.ok(payload);
                });
    }
}

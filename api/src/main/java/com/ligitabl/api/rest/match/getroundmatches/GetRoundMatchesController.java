package com.ligitabl.api.rest.match.getroundmatches;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.rest.match.MatchDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
@Slf4j
public class GetRoundMatchesController {

    private final GetRoundMatchesUseCase getRoundMatchesUseCase;

    @GetMapping("/{competitionSlug}/seasons/{seasonSlug}/rounds/{position}/matches")
    public ResponseEntity<List<MatchDto>> getMatchesForRound(
            @PathVariable String competitionSlug, @PathVariable String seasonSlug, @PathVariable int position) {
        log.info(
                "GetRoundMatches request competitionSlug={} seasonSlug={} position={}",
                competitionSlug,
                seasonSlug,
                position);
        var query = new GetRoundMatchesQuery(competitionSlug, seasonSlug, position);
        var result = getRoundMatchesUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                matches -> {
                    log.debug(
                            "GetRoundMatches success competitionSlug={} seasonSlug={} position={} count={}",
                            competitionSlug,
                            seasonSlug,
                            position,
                            matches.size());
                    return ResponseEntity.ok(matches);
                });
    }
}

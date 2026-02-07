package com.ligitabl.api.rest.round.getroundbyposition;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.rest.round.RoundDto;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
@Slf4j
public class GetRoundByPositionController {

    private final GetRoundByPositionUseCase getRoundByPositionUseCase;

    @GetMapping("/{competitionSlug}/seasons/{seasonSlug}/rounds/{position}")
    public ResponseEntity<RoundDto> getRoundByPosition(
            @PathVariable String competitionSlug, @PathVariable String seasonSlug, @PathVariable int position) {
        log.info(
                "GetRoundByPosition request competitionSlug={} seasonSlug={} position={}",
                competitionSlug,
                seasonSlug,
                position);

        var query = new GetRoundByPositionQuery(competitionSlug, seasonSlug, position);
        var result = getRoundByPositionUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                round -> {
                    log.debug(
                            "GetRoundByPosition success competitionSlug={} seasonSlug={} position={} roundId={}",
                            competitionSlug,
                            seasonSlug,
                            position,
                            round.getId());
                    return ResponseEntity.ok(round);
                });
    }
}

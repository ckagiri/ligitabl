package com.ligitabl.api.rest.round.getrounds;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.rest.round.RoundDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
@Slf4j
public class GetRoundsController {

    private final GetRoundsUseCase getRoundsUseCase;

    @GetMapping("/{competitionSlug}/seasons/{seasonSlug}/rounds")
    public ResponseEntity<List<RoundDto>> getRounds(
            @PathVariable String competitionSlug, @PathVariable String seasonSlug) {
        log.info("GetRounds request competitionSlug={} seasonSlug={}", competitionSlug, seasonSlug);
        var query = new GetRoundsQuery(competitionSlug, seasonSlug);
        var result = getRoundsUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                rounds -> ResponseEntity.ok(rounds));
    }
}

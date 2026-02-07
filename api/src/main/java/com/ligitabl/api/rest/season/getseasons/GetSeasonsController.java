package com.ligitabl.api.rest.season.getseasons;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.rest.season.SeasonDto;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
@Slf4j
public class GetSeasonsController {

    private final GetSeasonsUseCase getSeasonsUseCase;

    @GetMapping("/{competitionSlug}/seasons")
    public ResponseEntity<List<SeasonDto>> getSeasons(@PathVariable String competitionSlug) {
        log.info("GetSeasons request competitionSlug={}", competitionSlug);
        var query = new GetSeasonsQuery(competitionSlug);
        var result = getSeasonsUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                seasons -> {
                    log.debug("GetSeasons success competitionSlug={} count={}", competitionSlug, seasons.size());
                    return ResponseEntity.ok(seasons);
                });
    }
}

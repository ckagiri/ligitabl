package com.ligitabl.api.rest.season.getseasonbyslug;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.rest.season.SeasonDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
@Slf4j
public class GetSeasonBySlugController {

    private final GetSeasonBySlugUseCase getSeasonBySlugUseCase;

    @GetMapping("/{competitionSlug}/seasons/{seasonSlug}")
    public ResponseEntity<SeasonDto> getSeasonBySlug(
            @PathVariable String competitionSlug, @PathVariable String seasonSlug) {
        log.info("GetSeasonBySlug request competitionSlug={} seasonSlug={}", competitionSlug, seasonSlug);
        var query = new GetSeasonBySlugQuery(competitionSlug, seasonSlug);
        var result = getSeasonBySlugUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                dto -> {
                    log.debug(
                            "GetSeasonBySlug success competitionSlug={} seasonSlug={} seasonId={}",
                            competitionSlug,
                            seasonSlug,
                            dto.getId());
                    return ResponseEntity.ok(dto);
                });
    }
}

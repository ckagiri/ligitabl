package com.ligitabl.api.rest.competition.getcompetitionbyslug;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.rest.competition.CompetitionDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
@Slf4j
public class GetCompetitionBySlugController {

    private final GetCompetitionBySlugUseCase getCompetitionBySlugUseCase;

    @GetMapping("/{slug}")
    public ResponseEntity<CompetitionDto> getCompetitionBySlug(@PathVariable String slug) {
        log.info("GetCompetitionBySlug request slug={}", slug);
        var query = new GetCompetitionBySlugQuery(slug);
        var result = getCompetitionBySlugUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                dto -> {
                    log.debug("GetCompetitionBySlug success slug={} competitionId={}", slug, dto.getId());
                    return ResponseEntity.ok(dto);
                });
    }
}

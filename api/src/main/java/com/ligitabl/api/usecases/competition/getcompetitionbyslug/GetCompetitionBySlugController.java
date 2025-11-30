package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.usecases.competition.CompetitionDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
public class GetCompetitionBySlugController {

    private final GetCompetitionBySlugUseCase getCompetitionBySlugUseCase;

    @GetMapping("/{slug}")
    @ResponseStatus(HttpStatus.OK)
    public CompetitionDto getCompetitionBySlug(@PathVariable String slug) {
        var query = new GetCompetitionBySlugQuery(slug);
        return getCompetitionBySlugUseCase.execute(query);
    }
}


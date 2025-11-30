package com.ligitabl.api.usecases.competition.getcompetitions;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.usecases.competition.CompetitionDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
public class GetCompetitionsController {

    private final GetCompetitionsUseCase getCompetitionsUseCase;

    @GetMapping
    public List<CompetitionDto> getCompetitions() {
        return getCompetitionsUseCase.execute();
    }
}

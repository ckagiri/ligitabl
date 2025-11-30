package com.ligitabl.api.usecases.competition.getcompetitions;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.usecases.competition.CompetitionDto;
import com.ligitabl.model.repo.CompetitionRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCompetitionsHandler implements GetCompetitionsUseCase {

    private final CompetitionRepo competitionRepo;

    @Override
    public List<CompetitionDto> execute(Void request) {
        return CompetitionDto.listOf(competitionRepo.findAll());
    }
}

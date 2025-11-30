package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import org.springframework.stereotype.Service;

import com.ligitabl.api.usecases.competition.CompetitionDto;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCompetitionBySlugHandler implements GetCompetitionBySlugUseCase {

    private final CompetitionRepo competitionRepo;

    @Override
    public CompetitionDto execute(GetCompetitionBySlugQuery query) {
        var slug = CompetitionSlug.of(query.getSlug());
        return competitionRepo.findBySlug(slug).map(CompetitionDto::from).orElse(null);
    }
}

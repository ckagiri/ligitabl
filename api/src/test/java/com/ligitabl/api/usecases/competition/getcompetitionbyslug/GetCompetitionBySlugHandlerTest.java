package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import java.util.UUID;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.usecases.competition.CompetitionDto;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;

class GetCompetitionBySlugHandlerTest {

    @Mock
    private CompetitionRepo competitionRepo;

    private GetCompetitionBySlugUseCase getCompetitionBySlugUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getCompetitionBySlugUseCase = new GetCompetitionBySlugHandler(competitionRepo);
    }

    @Test
    void shouldReturnCompetitionWhenFound() {
        var competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .build();

        given(competitionRepo.findBySlug(any())).willReturn(Optional.of(competition));

        CompetitionDto dto = getCompetitionBySlugUseCase.execute(new GetCompetitionBySlugQuery("premier-league"));
        assertThat(dto.getName()).isEqualTo("Premier League");
        assertThat(dto.getSlug()).isEqualTo("premier-league");
        assertThat(dto.getCode()).isEqualTo("PL");
    }

    @Test
    void shouldReturnErrorWhenCompetitionNotFound() {
        given(competitionRepo.findBySlug(any())).willReturn(Optional.empty());

        CompetitionDto result = getCompetitionBySlugUseCase.execute(new GetCompetitionBySlugQuery("unknown"));

        assertThat(result).isNull();
    }
}

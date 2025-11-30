package com.ligitabl.api.usecases.competition.getcompetitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.competition.CompetitionDto;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;

class GetCompetitionsHandlerTest {

    @Mock
    private CompetitionRepo competitionRepo;

    private GetCompetitionsHandler getCompetitionsUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getCompetitionsUseCase = new GetCompetitionsHandler(competitionRepo);
    }

    @Test
    void shouldReturnCompetitions() {
        var competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .build();

        given(competitionRepo.findAll()).willReturn(List.of(competition));

        Either<UseCaseError, java.util.List<CompetitionDto>> result = getCompetitionsUseCase.execute(null);

        assertThat(result.isRight()).isTrue();
        var list = result.get();
        assertThat(list).hasSize(1);
        CompetitionDto dto = list.get(0);
        assertThat(dto.getName()).isEqualTo("Premier League");
        assertThat(dto.getSlug()).isEqualTo("premier-league");
        assertThat(dto.getCode()).isEqualTo("PL");
    }
}

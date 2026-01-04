package com.ligitabl.api.usecases.season.getseasons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

class GetSeasonsUseCaseTest {

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private RequestValidator requestValidator;

    @Mock
    private HierarchyValidator hierarchyValidator;

    private GetSeasonsUseCase getSeasonsUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getSeasonsUseCase = new GetSeasonsUseCase(hierarchyValidator, seasonRepo, requestValidator);
    }

    @Test
    void shouldReturnSeasons() {
        var season = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(UUID.randomUUID())
                .name("2024/25 Premier League")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .build();

        var competition = Competition.builder()
                .id(season.getCompetitionId())
                .slug(CompetitionSlug.of("some-competition"))
                .name("Premier League")
                .build();

        given(hierarchyValidator.validateCompetition("some-competition")).willReturn(Either.right(competition));

        given(seasonRepo.findAllByCompetitionId(season.getCompetitionId())).willReturn(List.of(season));

        given(requestValidator.validate(any(GetSeasonsQuery.class)))
                .willAnswer(invocation -> Either.right(invocation.getArgument(0)));

        var query = new GetSeasonsQuery("some-competition");
        Either<UseCaseError, java.util.List<SeasonDto>> result = getSeasonsUseCase.execute(query);

        assertThat(result.isRight()).isTrue();
        var list = result.get();
        assertThat(list).hasSize(1);
        SeasonDto dto = list.get(0);
        assertThat(dto.getName()).isEqualTo("2024/25 Premier League");
        assertThat(dto.getSlug()).isEqualTo("2024-25");
        assertThat(dto.getMaxRounds()).isEqualTo(38);
    }
}

package com.ligitabl.api.usecases.season.getseasonbyslug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

class GetSeasonBySlugUseCaseTest {

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private CompetitionRepo competitionRepo;

    private GetSeasonBySlugUseCase getSeasonBySlugUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        RequestValidator validator = request -> Either.right(request);
        getSeasonBySlugUseCase = new GetSeasonBySlugHandler(seasonRepo, competitionRepo, validator);
    }

    @Test
    void shouldReturnSeasonWhenFound() {
        var competitionId = UUID.randomUUID();
        var competition = Competition.builder()
            .id(competitionId)
            .name("Premier League")
            .slug(CompetitionSlug.of("premier-league"))
            .code("PL")
            .build();

        var season = Season.builder()
            .id(UUID.randomUUID())
            .competitionId(competitionId)
            .name("2024/25 Premier League")
            .slug(SeasonSlug.of("2024-25"))
            .startDate(LocalDate.of(2024, 8, 1))
            .endDate(LocalDate.of(2025, 5, 31))
            .maxRounds(38)
            .build();

        given(competitionRepo.findBySlug(any())).willReturn(Optional.of(competition));
        given(seasonRepo.findAll()).willReturn(List.of(season));

        Either<UseCaseError, SeasonDto> result =
            getSeasonBySlugUseCase.execute(new GetSeasonBySlugQuery("premier-league", "2024-25"));

        assertThat(result.isRight()).isTrue();
        SeasonDto dto = result.get();
        assertThat(dto.getName()).isEqualTo("2024/25 Premier League");
        assertThat(dto.getSlug()).isEqualTo("2024-25");
        assertThat(dto.getMaxRounds()).isEqualTo(38);
    }

    @Test
    void shouldReturnNullWhenSeasonNotFound() {
        given(competitionRepo.findBySlug(any())).willReturn(Optional.empty());

        Either<UseCaseError, SeasonDto> result =
            getSeasonBySlugUseCase.execute(new GetSeasonBySlugQuery("unknown", "2024-25-premier-league"));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
    }
}

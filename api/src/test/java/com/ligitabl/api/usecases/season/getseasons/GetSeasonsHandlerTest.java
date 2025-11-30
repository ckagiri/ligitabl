package com.ligitabl.api.usecases.season.getseasons;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ligitabl.api.usecases.season.SeasonDto;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.SeasonRepo;

class GetSeasonsHandlerTest {

    @Mock
    private SeasonRepo seasonRepo;

    private GetSeasonsHandler getSeasonsUseCase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getSeasonsUseCase = new GetSeasonsHandler(seasonRepo);
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

        given(seasonRepo.findAll()).willReturn(List.of(season));

        Either<UseCaseError, java.util.List<SeasonDto>> result = getSeasonsUseCase.execute(null);

        assertThat(result.isRight()).isTrue();
        var list = result.get();
        assertThat(list).hasSize(1);
        SeasonDto dto = list.get(0);
        assertThat(dto.getName()).isEqualTo("2024/25 Premier League");
        assertThat(dto.getSlug()).isEqualTo("2024-25");
        assertThat(dto.getMaxRounds()).isEqualTo(38);
    }
}

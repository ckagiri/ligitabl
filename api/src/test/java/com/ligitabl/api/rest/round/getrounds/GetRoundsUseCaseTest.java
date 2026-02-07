package com.ligitabl.api.rest.round.getrounds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.rest.round.RoundDto;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

class GetRoundsUseCaseTest {

    @Mock
    RoundRepo roundRepo;

    @Mock
    RequestValidator validator;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    HierarchyValidator hierarchyValidator;

    GetRoundsUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetRoundsUseCase(hierarchyValidator, roundRepo, validator);
    }

    @Test
    void happy_path_returns_round_dtos() {
        var query = new GetRoundsQuery("premier-league", "2024-25");

        var competitionId = UUID.randomUUID();
        var seasonId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .build();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .build();

        var round = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Matchday 1")
                .position(1)
                .build();

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(hierarchyValidator.validateCompetitionAndSeason("premier-league", "2024-25"))
                .thenReturn(Either.right(season));
        when(roundRepo.findBySeasonId(seasonId)).thenReturn(List.of(round));

        Either<UseCaseError, List<RoundDto>> result = useCase.execute(query);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).hasSize(1);
        assertThat(result.getRight().getFirst().getPosition()).isEqualTo(1);
        verify(validator).validate(query);
        verify(hierarchyValidator).validateCompetitionAndSeason("premier-league", "2024-25");
        verify(roundRepo).findBySeasonId(seasonId);
    }

    @Test
    void not_found_season_returns_left() {
        var query = new GetRoundsQuery("premier-league", "2024-25");

        var competitionId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .build();

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(hierarchyValidator.validateCompetitionAndSeason("premier-league", "2024-25"))
                .thenReturn(Either.left(new NotFoundError("Season", "slug", "2024-25")));

        var result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
        verify(hierarchyValidator).validateCompetitionAndSeason("premier-league", "2024-25");
        verifyNoInteractions(roundRepo);
    }

    @Test
    void validation_failure_short_circuits() {
        var query = new GetRoundsQuery("premier-league", "bad");
        UseCaseError error = new ValidationError("invalid slug");

        when(validator.validate(query)).thenReturn(Either.left(error));

        var result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(competitionRepo, seasonRepo, roundRepo);
    }
}

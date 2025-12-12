package com.ligitabl.api.usecases.round.getroundbyposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.usecases.round.RoundDto;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

class GetRoundByPositionUseCaseTest {

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    RequestValidator validator;

    GetRoundByPositionUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetRoundByPositionHandler(competitionRepo, seasonRepo, roundRepo, validator);
    }

    @Test
    void happy_path_returns_round_dto() {
        var query = new GetRoundByPositionQuery("premier-league", "2024-25", 1);

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
        when(competitionRepo.findBySlug(CompetitionSlug.of("premier-league")))
                .thenReturn(Optional.of(competition));
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2024-25")))
                .thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(round));

        Either<UseCaseError, RoundDto> result = useCase.execute(query);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight().getPosition()).isEqualTo(1);
        verify(validator).validate(query);
        verify(competitionRepo).findBySlug(CompetitionSlug.of("premier-league"));
        verify(seasonRepo).findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2024-25"));
        verify(roundRepo).findBySeasonIdAndPosition(seasonId, 1);
    }

    @Test
    void not_found_round_returns_left() {
        var query = new GetRoundByPositionQuery("premier-league", "2024-25", 99);

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

        when(validator.validate(query)).thenReturn(Either.right(query));
        when(competitionRepo.findBySlug(CompetitionSlug.of("premier-league")))
                .thenReturn(Optional.of(competition));
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2024-25")))
                .thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 99)).thenReturn(Optional.empty());

        var result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
        verify(roundRepo).findBySeasonIdAndPosition(seasonId, 99);
    }

    @Test
    void validation_failure_short_circuits() {
        var query = new GetRoundByPositionQuery("premier-league", "bad", 1);
        UseCaseError error = new ValidationError("invalid slug");

        when(validator.validate(query)).thenReturn(Either.left(error));

        var result = useCase.execute(query);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isSameAs(error);
        verifyNoInteractions(competitionRepo, seasonRepo, roundRepo);
    }
}

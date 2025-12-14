package com.ligitabl.api.usecases.match.getdefaultroundmatches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.api.usecases.shared.MatchEnricher;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;

class GetDefaultRoundMatchesUseCaseTest {

    @Mock
    HierarchyValidator hierarchyValidator;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    MatchRepo matchRepo;

        @Mock
        MatchEnricher matchEnricher;

    GetDefaultRoundMatchesUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
                useCase = new GetDefaultRoundMatchesHandler(hierarchyValidator, seasonRepo, matchRepo, matchEnricher);
    }

    @Test
    void happy_path_returns_match_dtos() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(seasonId)
                .build();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .currentRoundId(roundId)
                .build();

        var match = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.SCHEDULED)
                .build();

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(match));

        MatchDto dto = MatchDto.builder().roundId(roundId).build();
        when(matchEnricher.enrichWithTeams(List.of(match))).thenReturn(Either.right(List.of(dto)));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(null);

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).hasSize(1);
        assertThat(result.getRight().getFirst().getRoundId()).isEqualTo(roundId);
        verify(hierarchyValidator).validateCompetition("premier-league");
        verify(seasonRepo).findById(seasonId);
        verify(matchRepo).findByRoundId(roundId);
        verify(matchEnricher).enrichWithTeams(List.of(match));
    }

    @Test
    void missing_active_season_returns_validation_error() {
        UUID competitionId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .build();

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(null);

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).validateCompetition("premier-league");
        verifyNoInteractions(seasonRepo, matchRepo);
    }

    @Test
    void missing_current_round_returns_validation_error() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(seasonId)
                .build();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .build();

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

        Either<UseCaseError, List<MatchDto>> result = useCase.execute(null);

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).validateCompetition("premier-league");
        verify(seasonRepo).findById(seasonId);
        verifyNoInteractions(matchRepo);
    }
}

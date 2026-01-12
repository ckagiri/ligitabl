package com.ligitabl.api.usecases.standings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

class GetDefaultRoundStandingsUseCaseTest {

    @Mock
    HierarchyValidator hierarchyValidator;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    StandingsRepo standingsRepo;

    @Mock
    StandingsEnricher standingsEnricher;

    GetDefaultRoundStandingsUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetDefaultRoundStandingsUseCase(
                hierarchyValidator,
                competitionDefaults,
                seasonRepo,
                roundRepo,
                standingsRepo,
                standingsEnricher);
    }

    @Test
    void happy_path_returns_standings_dtos_for_current_round() {
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
                .maxRounds(38)
                .currentRoundId(roundId)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(1)
                .name("Matchday 1")
                .slug("md-1")
                .build();

        var standings = Standings.builder()
                .seasonId(seasonId)
                .roundPosition(1)
                .rankings(List.of())
                .finalised(true)
                .build();

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).thenReturn(Optional.of(standings));

        StandingsEntryDto dto = StandingsEntryDto.builder().position(1).teamName("Team").build();
        when(standingsEnricher.enrichWithTeams(standings)).thenReturn(Either.right(List.of(dto)));

        Either<UseCaseError, List<StandingsEntryDto>> result = useCase.execute(GetDefaultRoundStandingsQuery.currentRound(null));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).hasSize(1);
        assertThat(result.getRight().getFirst().getPosition()).isEqualTo(1);

        verify(hierarchyValidator).validateCompetition("premier-league");
        verify(seasonRepo).findById(seasonId);
        verify(roundRepo).findById(roundId);
        verify(standingsRepo).findBySeasonAndRoundPosition(seasonId, 1);
        verify(standingsEnricher).enrichWithTeams(standings);
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

        Either<UseCaseError, List<StandingsEntryDto>> result = useCase.execute(GetDefaultRoundStandingsQuery.currentRound(null));

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).validateCompetition("premier-league");
        verifyNoInteractions(seasonRepo, roundRepo, standingsRepo, standingsEnricher);
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
                .maxRounds(38)
                .build();

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

        Either<UseCaseError, List<StandingsEntryDto>> result = useCase.execute(GetDefaultRoundStandingsQuery.currentRound(null));

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).validateCompetition("premier-league");
        verify(seasonRepo).findById(seasonId);
        verifyNoInteractions(roundRepo, standingsRepo, standingsEnricher);
    }

    @Test
    void missing_standings_returns_empty_list_not_error() {
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
                .maxRounds(38)
                .currentRoundId(roundId)
                .build();

        var round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(1)
                .name("Matchday 1")
                .slug("md-1")
                .build();

        when(hierarchyValidator.validateCompetition("premier-league")).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).thenReturn(Optional.empty());

        when(standingsEnricher.enrichWithTeams(any(Standings.class))).thenReturn(Either.right(List.of()));

        Either<UseCaseError, List<StandingsEntryDto>> result = useCase.execute(GetDefaultRoundStandingsQuery.currentRound(null));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEmpty();

        verify(standingsEnricher).enrichWithTeams(any(Standings.class));
    }
}

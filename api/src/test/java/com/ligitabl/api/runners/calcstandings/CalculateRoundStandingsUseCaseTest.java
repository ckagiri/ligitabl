package com.ligitabl.api.runners.calcstandings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.domain.StandingsCalculatorService;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.rest.standings.StandingsEnricher;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

@ExtendWith(MockitoExtension.class)
class CalculateRoundStandingsUseCaseTest {

    @Mock
    HierarchyValidator hierarchyValidator;

        CompetitionDefaults competitionDefaults;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    MatchRepo matchRepo;

    @Mock
    StandingsRepo standingsRepo;

    @Mock
    StandingsEnricher standingsEnricher;

    @Mock
    StandingsCalculatorService standingsCalculator;

    @Captor
    ArgumentCaptor<Standings> standingsCaptor;

    CalculateRoundStandingsUseCase useCase;

    @BeforeEach
    void setUp() {
                competitionDefaults = new CompetitionDefaults("premier-league");
        useCase = new CalculateRoundStandingsUseCase(
                hierarchyValidator,
                competitionDefaults,
                seasonRepo,
                roundRepo,
                matchRepo,
                standingsRepo,
                standingsEnricher,
                standingsCalculator);
    }

    @Test
    @DisplayName("finalises standings only when round status is COMPLETED")
    void finalisesStandingsOnlyWhenRoundIsCompleted() {
        var compSlug = "premier-league";

        UUID seasonId = UUID.randomUUID();
        UUID competitionId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        Competition competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of(compSlug))
                .code("PL")
                .activeSeasonId(seasonId)
                .build();

        Season season = Season.builder()
                .id(seasonId)
                .clientId(2024)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .build();

        Round round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Matchday 5")
                .slug("md-5")
                .position(5)
                .finalized(false)
                .build();

        List<StandingsTeamRank> rankings = List.of(StandingsTeamRank.builder()
                .ranking(TeamRank.of("ARS", 1))
                .metadata(new StandingsMetadata(0, 0, 0, 0, 0, 0, 0, 0))
                .build());

        when(hierarchyValidator.validateCompetition(compSlug)).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 5)).thenReturn(Optional.of(round));

        // COMPLETED: all matches finished
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(match(roundId, seasonId, MatchStatus.FINISHED)));

        when(standingsCalculator.calculateRankings(seasonId, 5)).thenReturn(Either.right(rankings));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.empty());
        when(standingsRepo.save(any(Standings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(standingsEnricher.enrichWithTeams(any(Standings.class))).thenReturn(Either.right(List.of()));

        var result = useCase.execute(CalculateRoundStandingsCommand.byPosition(5, compSlug));

        assertThat(result.isRight()).isTrue();

        verify(standingsRepo).save(standingsCaptor.capture());
        Standings saved = standingsCaptor.getValue();

        assertThat(saved.isFinalised()).isTrue();
        assertThat(saved.getFinalisedAt()).isNotNull();
    }

    @Test
    @DisplayName("does not finalise standings when round status is LOCKED")
    void doesNotFinaliseStandingsWhenRoundIsLocked() {
        var compSlug = "premier-league";

        UUID seasonId = UUID.randomUUID();
        UUID competitionId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        Competition competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of(compSlug))
                .code("PL")
                .activeSeasonId(seasonId)
                .build();

        Season season = Season.builder()
                .id(seasonId)
                .clientId(2024)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .build();

        Round round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Matchday 5")
                .slug("md-5")
                .position(5)
                .finalized(false)
                .build();

        List<StandingsTeamRank> rankings = List.of(StandingsTeamRank.builder()
                .ranking(TeamRank.of("ARS", 1))
                .metadata(new StandingsMetadata(0, 0, 0, 0, 0, 0, 0, 0))
                .build());

        when(hierarchyValidator.validateCompetition(compSlug)).thenReturn(Either.right(competition));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 5)).thenReturn(Optional.of(round));

        // LOCKED: mix of finished + scheduled
        when(matchRepo.findByRoundId(roundId))
                .thenReturn(List.of(
                        match(roundId, seasonId, MatchStatus.FINISHED),
                        match(roundId, seasonId, MatchStatus.SCHEDULED)));

        when(standingsCalculator.calculateRankings(seasonId, 5)).thenReturn(Either.right(rankings));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.empty());
        when(standingsRepo.save(any(Standings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(standingsEnricher.enrichWithTeams(any(Standings.class))).thenReturn(Either.right(List.of()));

        var result = useCase.execute(CalculateRoundStandingsCommand.byPosition(5, compSlug));

        assertThat(result.isRight()).isTrue();

        verify(standingsRepo).save(standingsCaptor.capture());
        Standings saved = standingsCaptor.getValue();

        assertThat(saved.isFinalised()).isFalse();
        assertThat(saved.getFinalisedAt()).isNull();
    }

    private static Match match(UUID roundId, UUID seasonId, MatchStatus status) {
        return Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .seasonId(seasonId)
                .roundId(roundId)
                .slug("test-match")
                .status(status)
                .build();
    }
}

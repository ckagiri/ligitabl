package com.ligitabl.api.rest.match.getdefaultroundmatches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.match.MatchDto;
import com.ligitabl.api.rest.match.MatchEnricher;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.StandingsRepo;

class GetDefaultRoundMatchesUseCaseTest {

    @Mock
    HierarchyValidator hierarchyValidator;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    MatchRepo matchRepo;

    @Mock
    MatchEnricher matchEnricher;

    @Mock
    RoundRepo roundRepo;

    @Mock
    StandingsRepo standingsRepo;

    GetDefaultRoundMatchesUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new GetDefaultRoundMatchesUseCase(
                matchRepo, matchEnricher, hierarchyValidator, competitionDefaults, roundRepo, standingsRepo);
    }

    @Test
    void happy_path_returns_match_dtos() {
        UUID roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(UUID.randomUUID())
                .slug(SeasonSlug.of("2024-25"))
                .maxRounds(38)
                .currentRoundId(roundId)
                .mainContestId(UUID.randomUUID())
                .build();
        var round = Round.builder().id(roundId).position(1).build();

        var match = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.SCHEDULED)
                .build();

        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(roundRepo.findById(roundId)).thenReturn(java.util.Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(match));
        when(standingsRepo.findBySeasonAndRoundPosition(season.getId(), 1)).thenReturn(java.util.Optional.empty());

        MatchDto dto = MatchDto.builder().roundId(roundId).build();
        when(matchEnricher.enrichWithTeams(List.of(match))).thenReturn(Either.right(List.of(dto)));

        Either<UseCaseError, RoundMatchesResult> result =
                useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight().matches()).hasSize(1);
        assertThat(result.getRight().matches().getFirst().getRoundId()).isEqualTo(roundId);
        assertThat(result.getRight().viewingRound()).isEqualTo(1);
        assertThat(result.getRight().currentRound()).isEqualTo(1);
        assertThat(result.getRight().lastRound()).isEqualTo(38);
        assertThat(result.getRight().seasonSlug()).isEqualTo("2024-25");
        assertThat(result.getRight().seasonInSetupMode()).isFalse();
        assertThat(result.getRight().standingsFinalised()).isFalse();
        assertThat(result.getRight().allMatchesTerminalOrBlocking())
                .as("SCHEDULED match is neither complete nor blocking")
                .isFalse();
        assertThat(result.getRight().matchesComplete()).isFalse();
        verify(hierarchyValidator).resolveHierarchy("premier-league", null);
        verify(roundRepo).findById(roundId);
        verify(matchRepo).findByRoundId(roundId);
        verify(matchEnricher).enrichWithTeams(List.of(match));
    }

    @Test
    void all_matches_finished_or_postponed_marks_terminal_and_complete() {
        UUID roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(UUID.randomUUID())
                .slug(SeasonSlug.of("2024-25"))
                .maxRounds(38)
                .currentRoundId(roundId)
                .mainContestId(UUID.randomUUID())
                .build();
        var round = Round.builder().id(roundId).position(1).build();

        var finished = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.FINISHED)
                .build();
        var postponed = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.POSTPONED)
                .build();
        var matches = List.of(finished, postponed);

        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(roundRepo.findById(roundId)).thenReturn(java.util.Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(matches);
        when(standingsRepo.findBySeasonAndRoundPosition(season.getId(), 1)).thenReturn(java.util.Optional.empty());
        when(matchEnricher.enrichWithTeams(matches)).thenReturn(Either.right(List.of()));

        Either<UseCaseError, RoundMatchesResult> result =
                useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight().allMatchesTerminalOrBlocking()).isTrue();
        assertThat(result.getRight().matchesComplete()).isTrue();
    }

    @Test
    void blocking_match_marks_terminal_but_not_complete() {
        UUID roundId = UUID.randomUUID();

        var season = Season.builder()
                .id(UUID.randomUUID())
                .slug(SeasonSlug.of("2024-25"))
                .maxRounds(38)
                .currentRoundId(roundId)
                .mainContestId(UUID.randomUUID())
                .build();
        var round = Round.builder().id(roundId).position(1).build();

        var finished = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.FINISHED)
                .build();
        var cancelled = Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .status(MatchStatus.CANCELLED)
                .build();
        var matches = List.of(finished, cancelled);

        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(roundRepo.findById(roundId)).thenReturn(java.util.Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(matches);
        when(standingsRepo.findBySeasonAndRoundPosition(season.getId(), 1)).thenReturn(java.util.Optional.empty());
        when(matchEnricher.enrichWithTeams(matches)).thenReturn(Either.right(List.of()));

        Either<UseCaseError, RoundMatchesResult> result =
                useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight().allMatchesTerminalOrBlocking())
                .as("no SCHEDULED/LIVE matches remain, so it's terminal-or-blocking")
                .isTrue();
        assertThat(result.getRight().matchesComplete())
                .as("a CANCELLED match makes the round LOCKED, not COMPLETED")
                .isFalse();
    }

    @Test
    void missing_active_season_returns_validation_error() {
        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(Either.left(
                        com.ligitabl.api.shared.errors.UseCaseErrors.validation("Competition has no active season")));

        Either<UseCaseError, RoundMatchesResult> result =
                useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).resolveHierarchy("premier-league", null);
        verifyNoInteractions(matchRepo, matchEnricher, roundRepo);
    }

    @Test
    void missing_current_round_returns_validation_error() {
        when(hierarchyValidator.resolveHierarchy("premier-league", null))
                .thenReturn(Either.left(
                        com.ligitabl.api.shared.errors.UseCaseErrors.validation("Season has no current round")));

        Either<UseCaseError, RoundMatchesResult> result =
                useCase.execute(GetDefaultRoundMatchesQuery.currentRound(null));

        assertThat(result.isLeft()).isTrue();
        verify(hierarchyValidator).resolveHierarchy("premier-league", null);
        verifyNoInteractions(matchRepo, matchEnricher, roundRepo);
    }
}

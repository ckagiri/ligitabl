package com.ligitabl.api.usecase.matchadmin.transitionmatchstatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.api.usecase.shared.standings.RoundStandingsCalculator;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.CompetitionSlug;
import com.ligitabl.model.domain.league.Match;
import com.ligitabl.model.domain.league.MatchStatus;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.SeasonSlug;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

@ExtendWith(MockitoExtension.class)
class TransitionMatchStatusUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private StandingsRepo standingsRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private RoundStandingsCalculator standingsCalculator;

    @Mock
    private Clock clock;

    private TransitionMatchStatusUseCase useCase;

    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;

    private Competition competition;
    private Season season;
    private Round round;

    @BeforeEach
    void setUp() {
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(seasonId)
                .build();

        season = Season.builder()
                .id(seasonId)
                .clientId(1)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .currentRoundId(roundId)
                .currentMatchDay(1)
                .mainContestId(UUID.randomUUID())
                .build();

        round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .name("Matchday 1")
                .slug("md-1")
                .position(1)
                .finalized(false)
                .build();

        when(hierarchyValidator.resolveHierarchy(anyString(), any()))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(competition, season, round)));

        useCase = new TransitionMatchStatusUseCase(
                matchRepo,
                roundRepo,
                standingsRepo,
                hierarchyValidator,
                competitionDefaults,
                standingsCalculator,
                clock);
    }

    @Test
    void transitionToFinished_recalculatesCurrentRoundStandings() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.FINISHED)
                .reason("API stale; result entered manually")
                .score(new TransitionMatchCommand.ScoreDto(2, 1))
                .build();

        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T21:00:00Z"));
        when(standingsCalculator.calculateAndPersist(seasonId, 1)).thenReturn(Either.right(null));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        verify(standingsCalculator).calculateAndPersist(seasonId, 1);
    }

    @Test
    void transitionNotInvolvingFinished_doesNotRecalculateStandings() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.POSTPONED)
                .reason("Heavy rain")
                .build();

        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T21:00:00Z"));

        assertTrue(useCase.execute(cmd).isRight());
        verifyNoInteractions(standingsCalculator);
    }

    @Test
    void transition_successfulTransition_returnsRight() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.POSTPONED)
                .reason("Heavy rain")
                .build();

        Instant now = Instant.parse("2026-01-13T10:00:00Z");

        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(now);

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        TransitionResult ok = result.get();
        assertEquals(MatchStatus.SCHEDULED, ok.getOldStatus());
        assertEquals(MatchStatus.POSTPONED, ok.getNewStatus());
        assertEquals(round.getPosition(), ok.getRoundPosition());
        assertEquals(now, ok.getTimestamp());

        verify(matchRepo).save(argThat(m -> m.getStatus() == MatchStatus.POSTPONED && m.wasPostponed()));
    }

    @Test
    void transitionToFinished_withoutScore_returnsLeft() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.LIVE)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.FINISHED)
                .reason("Full time")
                .build();

        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }

    @Test
    void transition_invalidTransition_returnsLeft() {
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.FINISHED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.POSTPONED)
                .reason("Invalid")
                .build();

        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }

    @Test
    void transition_invalidTransition_allowedInSetupMode() {
        season.enterSetupMode(); // mainContestId -> null

        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.FINISHED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.SCHEDULED)
                .reason("Correcting a bad score in setup mode")
                .build();

        Instant now = Instant.parse("2026-01-13T10:00:00Z");

        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(now);

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        assertEquals(MatchStatus.SCHEDULED, result.get().getNewStatus());
        verify(matchRepo).save(argThat(m -> m.getStatus() == MatchStatus.SCHEDULED));
    }

    @Test
    void transition_liveTarget_stillBlockedInSetupMode() {
        season.enterSetupMode(); // mainContestId -> null

        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.FINISHED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.LIVE)
                .reason("Not a valid setup-mode target")
                .build();

        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }

    @Test
    void transition_inSetupMode_pastRoundMatch_marksOutOfSync() {
        season.enterSetupMode(); // mainContestId -> null

        Round laterRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Matchday 5")
                .slug("md-5")
                .position(5)
                .finalized(true)
                .build();
        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(laterRound));

        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.FINISHED)
                .score(new TransitionMatchCommand.ScoreDto(1, 0))
                .reason("Backfilling a past-round result")
                .build();

        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-13T10:00:00Z"));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        verify(roundRepo).markUnfinalizedBetween(seasonId, round.getPosition(), laterRound.getPosition());
        verify(standingsRepo).markUnfinalisedBetween(seasonId, round.getPosition(), laterRound.getPosition());
    }

    @Test
    void transition_inSetupMode_currentRoundMatch_alsoMarksOutOfSync() {
        season.enterSetupMode(); // mainContestId -> null

        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.POSTPONED)
                .reason("Same round as current")
                .build();

        // `round` (position 1) is used as both the match's round and the season's current round —
        // in setup mode, the current round can already be finalized+advanced (via a refinalize
        // cascade), so a status change here is just as stale as one in a genuinely past round.
        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-13T10:00:00Z"));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        verify(roundRepo).markUnfinalizedBetween(seasonId, round.getPosition(), round.getPosition());
        verify(standingsRepo).markUnfinalisedBetween(seasonId, round.getPosition(), round.getPosition());
    }

    @Test
    void transition_pastRoundMatch_outsideSetupMode_doesNotMarkOutOfSync() {
        // Live mode (default season fixture, mainContestId set) — cascade must never fire.
        Round laterRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Matchday 5")
                .slug("md-5")
                .position(5)
                .finalized(true)
                .build();
        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(laterRound));

        Match match = Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .build();

        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug(match.getSlug())
                .newStatus(MatchStatus.POSTPONED)
                .reason("Live mode edit")
                .build();

        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));
        when(matchRepo.save(any())).thenAnswer(i -> i.getArgument(0, Match.class));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-13T10:00:00Z"));

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isRight());
        verify(roundRepo, never()).markUnfinalizedBetween(any(), anyInt(), anyInt());
        verify(standingsRepo, never()).markUnfinalisedBetween(any(), anyInt(), anyInt());
    }

    @Test
    void transition_matchNotFound_returnsLeft() {
        TransitionMatchCommand cmd = TransitionMatchCommand.builder()
                .competitionIdentifier("premier-league")
                .roundPosition(null)
                .matchSlug("missing")
                .newStatus(MatchStatus.LIVE)
                .reason("Start")
                .build();

        when(matchRepo.findByRoundIdAndSlug(roundId, "missing")).thenReturn(Optional.empty());

        Either<UseCaseError, TransitionResult> result = useCase.execute(cmd);

        assertTrue(result.isLeft());
        verify(matchRepo, never()).save(any());
    }
}
